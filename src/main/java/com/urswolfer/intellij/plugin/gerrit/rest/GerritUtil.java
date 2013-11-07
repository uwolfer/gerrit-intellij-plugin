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

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.*;
import com.google.inject.Inject;
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
import com.urswolfer.intellij.plugin.gerrit.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.*;
import com.urswolfer.intellij.plugin.gerrit.rest.gson.DateDeserializer;
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
import java.net.URI;
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

    @NotNull private static final Gson gson = initGson();

    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private SslSupport sslSupport;
    @Inject
    private GerritApiUtil gerritApiUtil;

    private static Gson initGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new DateDeserializer());
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        return builder.create();
    }

    @Nullable
    public <T> T accessToGerritWithModalProgress(@NotNull final Project project,
                                                 @NotNull final ThrowableComputable<T, Exception> computable) {
        try {
            return doAccessToGerritWithModalProgress(project, computable);
        } catch (Exception e) {
            if (sslSupport.isCertificateException(e)) {
                if (sslSupport.askIfShouldProceed(gerritSettings.getHost())) {
                    // retry with the host being already trusted
                    return doAccessToGerritWithModalProgress(project, computable);
                } else {
                    return null;
                }
            }
            throw Throwables.propagate(e);
        }
    }

    private <T> T doAccessToGerritWithModalProgress(@NotNull final Project project,
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

    public void postReview(@NotNull String changeId,
                           @NotNull String revision,
                           @NotNull ReviewInput reviewInput,
                           Project project) {
        final String request = "/a/changes/" + changeId + "/revisions/" + revision + "/review";
        String json = new Gson().toJson(reviewInput);
        try {
            gerritApiUtil.postRequest(request, json);
        } catch (RestApiException e) {
            notifyError(project, "Failed to post review.", getErrorTextFromException(e));
        }
    }

    public void postSubmit(@NotNull String changeId,
                           @NotNull SubmitInput submitInput,
                           Project project) {
        final String request = "/a/changes/" + changeId + "/submit";
        String json = new Gson().toJson(submitInput);
        try {
            gerritApiUtil.postRequest(request, json);
        } catch (RestApiException e) {
            notifyError(project, "Failed to submit change.", getErrorTextFromException(e));
        }
    }

    @NotNull
    public List<ChangeInfo> getChanges(Project project) {
        return getChanges("", project);
    }

    @NotNull
    public List<ChangeInfo> getChangesToReview(Project project) {
        return getChanges("?q=is:open+reviewer:self", project);
    }

    /**
     * Provide information only for current project
     */
    @NotNull
    public List<ChangeInfo> getChangesForProject(@NotNull final Project project) {
        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (repositories.isEmpty()) {
            //Show notification
            showAddGitRepositoryNotification(project);
            return Lists.newArrayList();
        }

        List<GitRemote> remotes = Lists.newArrayList();
        for (GitRepository repository : repositories) {
            remotes.addAll(repository.getRemotes());
        }

        String host = parseUri(gerritSettings.getHost()).getHost();
        List<String> projectNames = Lists.newArrayList();
        for (GitRemote remote : remotes) {
            for (String repositoryUrl : remote.getUrls()) {
                String repositoryHost = parseUri(repositoryUrl).getHost();
                if (repositoryHost != null && repositoryHost.equals(host)) {
                    projectNames.add("project:" + getProjectName(gerritSettings.getHost(), repositoryUrl));
                }
            }
        }

        if (projectNames.isEmpty()) {
            return Collections.emptyList();
        }
        String projectQuery = Joiner.on("+OR+").join(projectNames);
        return getChanges("?q=is:open+(" + projectQuery + ')', project);
    }

    private URI parseUri(String url) {
        if (!url.contains("://")) { // some urls do not contain a protocol; just add something so it will not fail with parsing
            url = "git://" + url;
        }
        return URI.create(url);
    }

    private String getProjectName(String repositoryUrl, String url) {

        // Normalise the base.
        if( !repositoryUrl.endsWith("/") )
            repositoryUrl = repositoryUrl + "/";

        String basePath = parseUri(repositoryUrl).getPath();
        String path = parseUri(url).getPath();

        path = path.substring(basePath.length());

        path = path.replace(".git", ""); // some repositories end their name with ".git"

        if( path.endsWith("/") )
            path = path.substring(0, path.length()-1);

        return path;
    }

    public void showAddGitRepositoryNotification(final Project project) {
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
    public List<ChangeInfo> getChanges(@NotNull String query, Project project) {
        String request = "/a/changes/" + query;
        request = appendToUrlQuery(request, "o=LABELS");
        JsonElement result = null;
        try {
            result = gerritApiUtil.getRequest(request);
        } catch (RestApiException e) {
            notifyError(project, "Failed to get changes.", getErrorTextFromException(e));
        }
        if (result == null) {
            return Collections.emptyList();
        }
        return parseChangeInfos(result);
    }

    public Optional<ChangeInfo> getChangeDetails(@NotNull String changeNr, Project project) {
        final String request = "/a/changes/?q=" + changeNr + "&o=CURRENT_REVISION&o=MESSAGES";
        JsonElement result = null;
        try {
            result = gerritApiUtil.getRequest(request);
        } catch (RestApiException e) {
            notifyError(project, "Failed to get change.", getErrorTextFromException(e));
        }
        if (result == null) {
            return Optional.absent();
        }
        return Optional.of(parseSingleChangeInfos(result.getAsJsonArray().get(0).getAsJsonObject()));
    }

    @NotNull
    private List<ChangeInfo> parseChangeInfos(@NotNull JsonElement result) {
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
    private ChangeInfo parseSingleChangeInfos(@NotNull JsonObject result) {
        return gson.fromJson(result, ChangeInfo.class);
    }

    /**
     * Support starting from Gerrit 2.7.
     */
    @NotNull
    public TreeMap<String, List<CommentInfo>> getComments(@NotNull String changeId,
                                                          @NotNull String revision,
                                                          Project project) {
        final String request = "/a/changes/" + changeId + "/revisions/" + revision + "/comments/";
        JsonElement result = null;
        try {
            result = gerritApiUtil.getRequest(request);
        } catch (RestApiException e) {
            if (e instanceof HttpStatusException) { // remove once we drop Gerrit > 2.7 support
                if (((HttpStatusException) e).getStatusCode() == 404) {
                    LOG.warn("Failed to load comments; most probably because of too old Gerrit version (only 2.7 and newer supported). Returning empty.");
                    return Maps.newTreeMap();
                }
            }
            notifyError(project, "Failed to get comments.", getErrorTextFromException(e));
        }
        if (result == null) {
            return Maps.newTreeMap();
        }
        return parseCommentInfos(result);
    }

    @NotNull
    private TreeMap<String, List<CommentInfo>> parseCommentInfos(@NotNull JsonElement result) {
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
    private CommentInfo parseSingleCommentInfos(@NotNull JsonObject result) {
        return gson.fromJson(result, CommentInfo.class);
    }

    private boolean testConnection(@NotNull GerritAuthData gerritAuthData) throws RestApiException {
        AccountInfo user = retrieveCurrentUserInfo(gerritAuthData);
        return user != null;
    }

    @Nullable
    private AccountInfo retrieveCurrentUserInfo(@NotNull GerritAuthData gerritAuthData) throws RestApiException {
        JsonElement result = gerritApiUtil.getRequest(gerritAuthData, "/a/accounts/self");
        return parseUserInfo(result);
    }

    @Nullable
    private AccountInfo parseUserInfo(@Nullable JsonElement result) {
        if (result == null) {
            return null;
        }
        if (!result.isJsonObject()) {
            LOG.error(String.format("Unexpected JSON result format: %s", result));
            return null;
        }
        return gson.fromJson(result, AccountInfo.class);
    }

    @NotNull
    private List<ProjectInfo> getAvailableProjects() throws RestApiException {
        final String request = "/a/projects/";
        JsonElement result = gerritApiUtil.getRequest(request);
        if (result == null) {
            return Collections.emptyList();
        }
        return parseProjectInfos(result);
    }

    @NotNull
    private List<ProjectInfo> parseProjectInfos(@NotNull JsonElement result) {
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
    private ProjectInfo parseSingleRepositoryInfo(@NotNull JsonObject result) {
        final Gson gson = new GsonBuilder()
                .create();
        return gson.fromJson(result, ProjectInfo.class);
    }

    /**
     * Checks if user has set up correct user credentials for access in the settings.
     *
     * @return true if we could successfully login with these credentials, false if authentication failed or in the case of some other error.
     */
    public boolean checkCredentials(final Project project) {
        try {
            return checkCredentials(project, gerritSettings);
        } catch (Exception e) {
            // this method is a quick-check if we've got valid user setup.
            // if an exception happens, we'll show the reason in the login dialog that will be shown right after checkCredentials failure.
            LOG.info(e);
            return false;
        }
    }

    public boolean checkCredentials(Project project, final GerritAuthData gerritAuthData) {
        if (StringUtil.isEmptyOrSpaces(gerritAuthData.getHost()) ||
                StringUtil.isEmptyOrSpaces(gerritAuthData.getLogin()) ||
                StringUtil.isEmptyOrSpaces(gerritAuthData.getPassword())) {
            return false;
        }
        Boolean result = accessToGerritWithModalProgress(project, new ThrowableComputable<Boolean, Exception>() {
            @Override
            public Boolean compute() throws Exception {
                ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to Gerrit");
                return testConnection(gerritAuthData);
            }
        });
        return result == null ? false : result;
    }

    /**
     * Shows Gerrit login settings if credentials are wrong or empty and return the list of all projects
     */
    @Nullable
    public List<ProjectInfo> getAvailableProjects(final Project project) {
        while (!checkCredentials(project)) {
            final LoginDialog dialog = new LoginDialog(project, gerritSettings, this);
            dialog.show();
            if (!dialog.isOK()) {
                return null;
            }
        }
        // Otherwise our credentials are valid and they are successfully stored in settings
        return accessToGerritWithModalProgress(project, new ThrowableComputable<List<ProjectInfo>, Exception>() {
            @Override
            public List<ProjectInfo> compute() throws Exception {
                ProgressManager.getInstance().getProgressIndicator().setText("Extracting info about available repositories");
                return getAvailableProjects();
            }
        });
    }

    public String getRef(ChangeInfo changeDetails) {
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
    public boolean testGitExecutable(final Project project) {
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
    public String getErrorTextFromException(@NotNull Exception e) {
        return e.getMessage();
    }

    public void notifyError(@NotNull Project project, @NotNull String title, @NotNull String message) {
        notify(project, title, message, NotificationType.ERROR);
    }

    public void notifyInformation(@NotNull Project project, @NotNull String title, @NotNull String message) {
        notify(project, title, message, NotificationType.INFORMATION);
    }

    private void notify(@NotNull Project project, @NotNull String title, @NotNull String message, @NotNull NotificationType notificationType) {
        new Notification(GERRIT_NOTIFICATION_GROUP, title, message, notificationType).notify(project);
    }

    private String appendToUrlQuery(String requestUrl, String queryString) {
        if (requestUrl.contains("?")) {
            requestUrl += "&";
        } else {
            requestUrl += "?";
        }
        requestUrl += queryString;
        return requestUrl;
    }
}
