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

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.*;
import com.urswolfer.intellij.plugin.gerrit.ui.LoginDialog;
import git4idea.GitUtil;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parts based on org.jetbrains.plugins.github.GithubUtil
 *
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
public class GerritUtil {

    public static final Logger LOG = Logger.getInstance("gerrit");

    static final String GERRIT_NOTIFICATION_GROUP = "gerrit";

    @Nullable
    public static <T> T accessToGerritWithModalProgress(@NotNull final Project project, @NotNull String host,
                                                        @NotNull final ThrowableComputable<T, Exception> computable) {
        try {
            return doAccessToGerritWithModalProgress(project, computable);
        } catch (Exception e) {
            SslSupport sslSupport = SslSupport.getInstance();
            if (SslSupport.isCertificateException(e)) {
                if (sslSupport.askIfShouldProceed(host)) {
                    // retry with the host being already trusted
                    return doAccessToGerritWithModalProgress(project, computable);
                } else {
                    return null;
                }
            }
            throw Throwables.propagate(e);
        }
    }

    private static <T> T doAccessToGerritWithModalProgress(@NotNull final Project project,
                                                           @NotNull final ThrowableComputable<T, Exception> computable) {
        final AtomicReference<T> result = new AtomicReference<T>();
        final AtomicReference<Exception> exception = new AtomicReference<Exception>();
        ProgressManager.getInstance().run(new Task.Modal(project, "Access to Gerrit", true) {
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    result.set(computable.compute());
                } catch (Exception e) {
                    exception.set(e);
                }
            }
        });
        //noinspection ThrowableResultOfMethodCallIgnored
        if (exception.get() == null) {
            return result.get();
        }
        throw Throwables.propagate(exception.get());
    }

    public static void postReview(@NotNull String url, @NotNull String login, @NotNull String password,
                                  @NotNull String changeId, @NotNull String revision, @NotNull ReviewInput reviewInput) {
        final String request = "/a/changes/" + changeId + "/revisions/" + revision + "/review";
        String json = new Gson().toJson(reviewInput);
        GerritApiUtil.postRequest(url, login, password, request, json);
    }

    public static void postSubmit(@NotNull String url, @NotNull String login, @NotNull String password,
                                  @NotNull String changeId, @NotNull SubmitInput submitInput) {
        final String request = "/a/changes/" + changeId + "/submit";
        String json = new Gson().toJson(submitInput);
        GerritApiUtil.postRequest(url, login, password, request, json);
    }

    @NotNull
    public static List<ChangeInfo> getChanges(@NotNull String url, @NotNull String login, @NotNull String password) {
        return getChanges(url, login, password, "");
    }

    @NotNull
    public static List<ChangeInfo> getChangesToReview(@NotNull String url, @NotNull String login, @NotNull String password) {
        return getChanges(url, login, password, "?q=is:open+reviewer:self");
    }

    /**
     * Provide information only for current project
     */
    @NotNull
    public static List<ChangeInfo> getChangesForProject(@NotNull String url, @NotNull String login, @NotNull String password, @NotNull final Project project) {
        String projectName = "";
        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (repositories.isEmpty()) {
            //Show notification
            showAddGitRepositoryNotification(project);
            return Lists.newArrayList();
        }

        GitRemote gitRemote = Iterables.getFirst(repositories.get(0).getRemotes(), null);
        if (gitRemote == null) {
            notifyError(project, "No remotes available to fetch", "Git repository doesn't have any remotes. <br/> Please add one and try again.");
            return Lists.newArrayList();
        }
        for (String repositoryUrl : gitRemote.getUrls()) {
            if (repositoryUrl.contains(url)) {
                projectName = repositoryUrl.replace(url + "/", "");
                break;
            }
        }
        return getChanges(url, login, password, "?q=is:open+project:" + projectName);
    }

    public static void showAddGitRepositoryNotification(final Project project) {
        Notifications.Bus.notify(new Notification(GERRIT_NOTIFICATION_GROUP, "Insufficient dependencies", "Please add git repository <br/> <a href='vcs'>Add vcs root</a>", NotificationType.WARNING, new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    if (event.getDescription().equals("vcs")) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, ActionsBundle.message("group.VcsGroup.text"));
                    }
                }
            }
        }));
    }

    @NotNull
    public static List<ChangeInfo> getChanges(@NotNull String url, @NotNull String login, @NotNull String password, @NotNull String query) {
        final String request = "/a/changes/" + query;
        JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
        if (result == null) {
            return Collections.emptyList();
        }
        return parseChangeInfos(result);
    }

    @NotNull
    public static ChangeInfo getChangeDetails(@NotNull String url, @NotNull String login, @NotNull String password, @NotNull String changeNr) {
        final String request = "/a/changes/?q=" + changeNr + "&o=CURRENT_REVISION";
        JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
        if (result == null) {
            throw new RuntimeException("No valid result available.");
        }
        return parseSingleChangeInfos(result.getAsJsonArray().get(0).getAsJsonObject());
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
        final Gson gson = createGson();
        return gson.fromJson(result, ChangeInfo.class);
    }

    /**
     * Support starting from Gerrit 2.7.
     */
    @NotNull
    public static TreeMap<String, List<CommentInfo>> getComments(@NotNull String url, @NotNull String login, @NotNull String password,
                                                                 @NotNull String changeId, @NotNull String revision) {
        final String request = "/a/changes/" + changeId + "/revisions/" + revision + "/comments/";
        try {
            JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
            if (result == null) {
                return Maps.newTreeMap();
            }
            return parseCommentInfos(result);
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                LOG.warn("Failed to load comments; most probably because of too old Gerrit version (only 2.7 and newer supported). Returning empty.");
            }
            return Maps.newTreeMap();
        }
    }

    @NotNull
    private static TreeMap<String, List<CommentInfo>> parseCommentInfos(@NotNull JsonElement result) {
        TreeMap<String, List<CommentInfo>> commentInfos = Maps.newTreeMap();
        final JsonObject jsonObject = result.getAsJsonObject();

        for (Map.Entry<String, JsonElement> element : jsonObject.entrySet()) {
            List<CommentInfo> currentCommentInfos = Lists.newArrayList();

            for (JsonElement jsonElement : element.getValue().getAsJsonArray()) {
                currentCommentInfos.add(parseSingleCommentInfos(jsonElement.getAsJsonObject()));
            }

            commentInfos.put(element.getKey(), currentCommentInfos);
        }
        return commentInfos;
    }

    @NotNull
    private static CommentInfo parseSingleCommentInfos(@NotNull JsonObject result) {
        final Gson gson = createGson();
        return gson.fromJson(result, CommentInfo.class);
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .setDateFormat("yyyy-MM-dd hh:mm:ss")
                .create();
    }

    private static boolean testConnection(final String url, final String login, final String password) {
        AccountInfo user = retrieveCurrentUserInfo(url, login, password);
        return user != null;
    }

    @Nullable
    private static AccountInfo retrieveCurrentUserInfo(@NotNull String url, @NotNull String login,
                                                       @NotNull String password) {
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
        JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
        if (result == null) {
            return Collections.emptyList();
        }
        return parseProjectInfos(result);
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
     *
     * @return true if we could successfully login with these credentials, false if authentication failed or in the case of some other error.
     */
    public static boolean checkCredentials(final Project project) {
        final GerritSettings settings = GerritSettings.getInstance();
        try {
            return checkCredentials(project, settings.getHost(), settings.getLogin(), settings.getPassword());
        } catch (Exception e) {
            // this method is a quick-check if we've got valid user setup.
            // if an exception happens, we'll show the reason in the login dialog that will be shown right after checkCredentials failure.
            LOG.info(e);
            return false;
        }
    }

    public static boolean checkCredentials(Project project, final String url, final String login, final String password) {
        if (StringUtil.isEmptyOrSpaces(url) || StringUtil.isEmptyOrSpaces(login) || StringUtil.isEmptyOrSpaces(password)) {
            return false;
        }
        Boolean result = accessToGerritWithModalProgress(project, url, new ThrowableComputable<Boolean, Exception>() {
            @Override
            public Boolean compute() throws Exception {
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
    public static List<ProjectInfo> getAvailableProjects(final Project project) {
        while (!checkCredentials(project)) {
            final LoginDialog dialog = new LoginDialog(project);
            dialog.show();
            if (!dialog.isOK()) {
                return null;
            }
        }
        // Otherwise our credentials are valid and they are successfully stored in settings
        final GerritSettings settings = GerritSettings.getInstance();
        final String validPassword = settings.getPassword();
        return accessToGerritWithModalProgress(project, settings.getHost(), new ThrowableComputable<List<ProjectInfo>, Exception>() {
            @Override
            public List<ProjectInfo> compute() throws Exception {
                ProgressManager.getInstance().getProgressIndicator().setText("Extracting info about available repositories");
                return getAvailableProjects(settings.getHost(), settings.getLogin(), validPassword);
            }
        });
    }

    public static String getRef(ChangeInfo changeDetails) {
        String ref = null;
        final TreeMap<String, RevisionInfo> revisions = changeDetails.getRevisions();
        for (RevisionInfo revisionInfo : revisions.values()) {
            final TreeMap<String, FetchInfo> fetch = revisionInfo.getFetch();
            for (FetchInfo fetchInfo : fetch.values()) {
                ref = fetchInfo.getRef();
            }
        }
        return ref;
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
    public static String getErrorTextFromException(@NotNull Exception e) {
        return e.getMessage();
    }

    public static void notifyError(@NotNull Project project, @NotNull String title, @NotNull String message) {
        notify(project, title, message, NotificationType.ERROR);
    }

    public static void notifyInformation(@NotNull Project project, @NotNull String title, @NotNull String message) {
        notify(project, title, message, NotificationType.INFORMATION);
    }

    private static void notify(@NotNull Project project, @NotNull String title, @NotNull String message, @NotNull NotificationType notificationType) {
        new Notification(GERRIT_NOTIFICATION_GROUP, title, message, notificationType).notify(project);
    }
}
