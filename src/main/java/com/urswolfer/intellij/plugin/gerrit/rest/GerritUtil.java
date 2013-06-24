/*
 * Copyright 2000-2011 JetBrains s.r.o.
 * Copyright 2013 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.urswolfer.intellij.plugin.gerrit.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.AccountInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ProjectInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ReviewInput;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.SubmitInput;
import com.urswolfer.intellij.plugin.gerrit.ui.LoginDialog;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parts based on org.jetbrains.plugins.github.GithubUtil
 *
 * @author Urs Wolfer
 */
public class GerritUtil {

    public static final Logger LOG = Logger.getInstance("gerrit");

    static final String GERRIT_NOTIFICATION_GROUP = "gerrit";

    @Nullable
    public static <T> T accessToGerritWithModalProgress(@NotNull final Project project, @NotNull String host,
                                                        @NotNull final ThrowableComputable<T, IOException> computable) throws IOException {
        try {
            return doAccessToGerritWithModalProgress(project, computable);
        }
        catch (IOException e) {
            SslSupport sslSupport = SslSupport.getInstance();
            if (SslSupport.isCertificateException(e)) {
                if (sslSupport.askIfShouldProceed(host)) {
                    // retry with the host being already trusted
                    return doAccessToGerritWithModalProgress(project, computable);
                }
                else {
                    return null;
                }
            }
            throw e;
        }
    }

    private static <T> T doAccessToGerritWithModalProgress(@NotNull final Project project,
                                                           @NotNull final ThrowableComputable<T, IOException> computable) throws IOException {
        final AtomicReference<T> result = new AtomicReference<T>();
        final AtomicReference<IOException> exception = new AtomicReference<IOException>();
        ProgressManager.getInstance().run(new Task.Modal(project, "Access to Gerrit", true) {
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    result.set(computable.compute());
                }
                catch (IOException e) {
                    exception.set(e);
                }
            }
        });
        //noinspection ThrowableResultOfMethodCallIgnored
        if (exception.get() == null) {
            return result.get();
        }
        throw exception.get();
    }

    public static void postReview(@NotNull String url, @NotNull String login, @NotNull String password,
                                  @NotNull String changeId, @NotNull String revision, @NotNull ReviewInput reviewInput) {
        final String request = "/a/changes/" + changeId + "/revisions/" + revision + "/review";
        try {
            String json = new Gson().toJson(reviewInput);
            GerritApiUtil.postRequest(url, login, password, request, json);
        } catch (IOException e) {
            LOG.error(e);
        }
    }

    public static void postSubmit(@NotNull String url, @NotNull String login, @NotNull String password,
                                  @NotNull String changeId, @NotNull SubmitInput submitInput) {
        final String request = "/a/changes/" + changeId + "/submit";
        try {
            String json = new Gson().toJson(submitInput);
            GerritApiUtil.postRequest(url, login, password, request, json);
        } catch (IOException e) {
            LOG.error(e);
        }
    }

    @NotNull
    public static List<ChangeInfo> getChanges(@NotNull String url, @NotNull String login, @NotNull String password) {
        final String request = "/a/changes/";
        try {
            JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
            if (result == null) {
                return Collections.emptyList();
            }
            return parseChangeInfos(result);
        } catch (IOException e) {
            LOG.error(e);
            return Collections.emptyList();
        }
    }

    @NotNull
    public static ChangeInfo getChangeDetails(@NotNull String url, @NotNull String login, @NotNull String password, @NotNull String changeNr) {
        final String request = "/a/changes/?q=" + changeNr + "&o=CURRENT_REVISION";
        try {
            JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
            if (result == null) {
                throw new RuntimeException("No valid result available.");
            }
            return parseSingleChangeInfos(result.getAsJsonArray().get(0).getAsJsonObject());
        } catch (IOException e) {
            LOG.error(e);
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static List<ChangeInfo> parseChangeInfos(@NotNull JsonElement result) {
        if (!result.isJsonArray()) {
            LOG.assertTrue(result.isJsonObject(), String.format("Unexpected JSON result format: %s", result));
            return Collections.singletonList(parseSingleChangeInfos(result.getAsJsonObject()));
        }

        List<ChangeInfo> changeInfoList = new ArrayList<ChangeInfo>();
        for (JsonElement element : result.getAsJsonArray()) {
            LOG.assertTrue(element.isJsonObject(),
                    String.format("This element should be a JsonObject: %s%nTotal JSON response: %n%s", element, result));
            changeInfoList.add(parseSingleChangeInfos(element.getAsJsonObject()));
        }
        return changeInfoList;
    }

    @NotNull
    private static ChangeInfo parseSingleChangeInfos(@NotNull JsonObject result) {
        final Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd hh:mm:ss")
                .create();
        return gson.fromJson(result, ChangeInfo.class);
    }

    private static boolean testConnection(final String url, final String login, final String password) throws IOException {
        AccountInfo user = retrieveCurrentUserInfo(url, login, password);
        return user != null;
    }

    @Nullable
    private static AccountInfo retrieveCurrentUserInfo(@NotNull String url, @NotNull String login,
                                                      @NotNull String password) throws IOException {
        JsonElement result = GerritApiUtil.getRequest(url, login, password, "/a/accounts/self");
        return parseUserInfo(result);
    }

    @Nullable
    private static AccountInfo parseUserInfo(@Nullable JsonElement result) {
        if (result == null) {
            return null;
        }
        if (!result.isJsonObject()) {
            LOG.error(String.format("Unexpected JSON result format: %s", result));
            return null;
        }
        final Gson gson = new GsonBuilder()
                .create();
        return gson.fromJson(result, AccountInfo.class);
    }

    @NotNull
    private static List<ProjectInfo> getAvailableProjects(@NotNull String url, @NotNull String login, @NotNull String password) {
        final String request = "/a/projects/";
        try {
            JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
            if (result == null) {
                return Collections.emptyList();
            }
            return parseProjectInfos(result);
        }
        catch (IOException e) {
            LOG.error(e);
            return Collections.emptyList();
        }
    }

    @NotNull
    private static List<ProjectInfo> parseProjectInfos(@NotNull JsonElement result) {
        List<ProjectInfo> repositories = new ArrayList<ProjectInfo>();
        final JsonObject jsonObject = result.getAsJsonObject();
        for (Map.Entry<String, JsonElement> element : jsonObject.entrySet()) {
            LOG.assertTrue(element.getValue().isJsonObject(),
                    String.format("This element should be a JsonObject: %s%nTotal JSON response: %n%s", element, result));
            repositories.add(parseSingleRepositoryInfo(element.getValue().getAsJsonObject()));

        }
        return repositories;
    }

    @NotNull
    private static ProjectInfo parseSingleRepositoryInfo(@NotNull JsonObject result) {
        final Gson gson = new GsonBuilder()
                .create();
        return gson.fromJson(result, ProjectInfo.class);
    }

    /**
     * Checks if user has set up correct user credentials for access in the settings.
     * @return true if we could successfully login with these credentials, false if authentication failed or in the case of some other error.
     */
    public static boolean checkCredentials(final Project project) {
        final GerritSettings settings = GerritSettings.getInstance();
        try {
            return checkCredentials(project, settings.getHost(), settings.getLogin(), settings.getPassword());
        }
        catch (IOException e) {
            // this method is a quick-check if we've got valid user setup.
            // if an exception happens, we'll show the reason in the login dialog that will be shown right after checkCredentials failure.
            LOG.info(e);
            return false;
        }
    }

    public static boolean checkCredentials(Project project, final String url, final String login, final String password) throws IOException {
        if (StringUtil.isEmptyOrSpaces(url) || StringUtil.isEmptyOrSpaces(login) || StringUtil.isEmptyOrSpaces(password)){
            return false;
        }
        Boolean result = accessToGerritWithModalProgress(project, url, new ThrowableComputable<Boolean, IOException>() {
            @Override
            public Boolean compute() throws IOException {
                ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to Gerrit");
                return testConnection(url, login, password);
            }
        });
        return result == null ? false : result;
    }

    /**
     * Shows Gerrit login settings if credentials are wrong or empty and return the list of all projects
     */
    @Nullable
    public static List<ProjectInfo> getAvailableProjects(final Project project) throws IOException {
        while (!checkCredentials(project)){
            final LoginDialog dialog = new LoginDialog(project);
            dialog.show();
            if (!dialog.isOK()){
                return null;
            }
        }
        // Otherwise our credentials are valid and they are successfully stored in settings
        final GerritSettings settings = GerritSettings.getInstance();
        final String validPassword = settings.getPassword();
        return accessToGerritWithModalProgress(project, settings.getHost(), new ThrowableComputable<List<ProjectInfo>, IOException>() {
            @Override
            public List<ProjectInfo> compute() throws IOException {
                ProgressManager.getInstance().getProgressIndicator().setText("Extracting info about available repositories");
                return getAvailableProjects(settings.getHost(), settings.getLogin(), validPassword);
            }
        });
    }

    @SuppressWarnings("UnresolvedPropertyKey")
    public static boolean testGitExecutable(final Project project) {
        final GitVcsApplicationSettings settings = GitVcsApplicationSettings.getInstance();
        final String executable = settings.getPathToGit();
        final GitVersion version;
        try {
            version = GitVersion.identifyVersion(executable);
        } catch (Exception e) {
            Messages.showErrorDialog(project, e.getMessage(), GitBundle.getString("find.git.error.title"));
            return false;
        }

        if (!version.isSupported()) {
            Messages.showWarningDialog(project, GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                    GitBundle.getString("find.git.success.title"));
            return false;
        }
        return true;
    }

    @NotNull
    public static String getErrorTextFromException(@NotNull IOException e) {
        return e.getMessage();
    }

    public static void notifyError(@NotNull Project project, @NotNull String title, @NotNull String message) {
        new Notification(GERRIT_NOTIFICATION_GROUP, title, message, NotificationType.ERROR).notify(project);
    }
}
