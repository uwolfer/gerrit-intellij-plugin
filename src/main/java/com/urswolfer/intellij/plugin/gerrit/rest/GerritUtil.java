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
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.*;
import com.google.inject.Inject;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.*;
import com.urswolfer.intellij.plugin.gerrit.rest.gson.DateDeserializer;
import com.urswolfer.intellij.plugin.gerrit.ui.LoginDialog;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.GitUtil;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parts based on org.jetbrains.plugins.github.GithubUtil
 *
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
public class GerritUtil {

    @NotNull private static final Gson gson = initGson();

    @Inject
    private GerritRestAccess gerritRestAccess;
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private SslSupport sslSupport;
    @Inject
    private GerritApiUtil gerritApiUtil;
    @Inject
    private Logger log;
    @Inject
    private NotificationService notificationService;

    private static Gson initGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new DateDeserializer());
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        return builder.create();
    }

    @Nullable
    public <T> T accessToGerritWithModalProgress(Project project,
                                                 ThrowableComputable<T, Exception> computable) {
        gerritSettings.preloadPassword();
        return accessToGerritWithModalProgress(project, computable, gerritSettings);
    }

    @Nullable
    public <T> T accessToGerritWithModalProgress(Project project,
                                                 ThrowableComputable<T, Exception> computable,
                                                 GerritAuthData gerritAuthData) {
        try {
            return doAccessToGerritWithModalProgress(project, computable);
        } catch (Exception e) {
            if (sslSupport.isCertificateException(e)) {
                if (sslSupport.askIfShouldProceed(gerritAuthData.getHost())) {
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
                           final Project project,
                           final Consumer<Void> consumer) {
        final String request = "/changes/" + changeId + "/revisions/" + revision + "/review";
        String json = new Gson().toJson(reviewInput);
        gerritRestAccess.postRequest(request, json, project, new Consumer<ConsumerResult<JsonElement>>() {
            @Override
            public void consume(ConsumerResult<JsonElement> result) {
                if (result.getException().isPresent()) {
                    NotificationBuilder notification = new NotificationBuilder(project, "Failed to post Gerrit review.",
                            getErrorTextFromException(result.getException().get()));
                    notificationService.notifyError(notification);
                } else {
                    consumer.consume(null); // we can parse the response once we actually need it
                }
            }
        });
    }

    public void postSubmit(@NotNull String changeId,
                           @NotNull SubmitInput submitInput,
                           final Project project) {
        final String request = "/changes/" + changeId + "/submit";
        String json = new Gson().toJson(submitInput);
        gerritRestAccess.postRequest(request, json, project, new Consumer<ConsumerResult<JsonElement>>() {
            @Override
            public void consume(ConsumerResult<JsonElement> result) {
                if (result.getException().isPresent()) {
                    NotificationBuilder notification = new NotificationBuilder(project, "Failed to submit Gerrit change.",
                            getErrorTextFromException(result.getException().get()));
                    notificationService.notifyError(notification);
                }
            }
        });
    }

    /**
     * Star-endpoint added in Gerrit 2.8.
     */
    public void changeStarredStatus(String changeNr,
                                    boolean starred,
                                    final Project project) {
        final String request = "/accounts/self/starred.changes/" + changeNr;
        Consumer<ConsumerResult<JsonElement>> consumer = new Consumer<ConsumerResult<JsonElement>>() {
            @Override
            public void consume(ConsumerResult<JsonElement> result) {
                if (result.getException().isPresent()) {
                    NotificationBuilder notification = new NotificationBuilder(project, "Failed to star Gerrit change.",
                            getErrorTextFromException(result.getException().get()));
                    notificationService.notifyError(notification);
                }
            }
        };
        if (starred) {
            gerritRestAccess.putRequest(request, project, consumer);
        } else {
            gerritRestAccess.deleteRequest(request, project, consumer);
        }
    }

    public void getChangeReviewed(String changeId,
                                  String revision,
                                  String filePath,
                                  boolean reviewed,
                                  final Project project) {
        String encodedPath;
        try {
            encodedPath = URLEncoder.encode(filePath, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw Throwables.propagate(e);
        }
        final String request = String.format("/changes/%s/revisions/%s/files/%s/reviewed", changeId, revision, encodedPath);
        Consumer<ConsumerResult<JsonElement>> consumer = new Consumer<ConsumerResult<JsonElement>>() {
            @Override
            public void consume(ConsumerResult<JsonElement> result) {
                if (result.getException().isPresent()) {
                    NotificationBuilder notification = new NotificationBuilder(project, "Failed set file review status for Gerrit change.",
                            getErrorTextFromException(result.getException().get()));
                    notificationService.notifyError(notification);
                }
            }
        };
        if (reviewed) {
            gerritRestAccess.putRequest(request, project, consumer);
        } else {
            gerritRestAccess.deleteRequest(request, project, consumer);
        }
    }

    public void getChangesToReview(Project project, Consumer<List<ChangeInfo>> consumer) {
        getChanges("is:open+reviewer:self", project, consumer);
    }

    public void getChangesForProject(String query, final Project project, final Consumer<List<ChangeInfo>> consumer) {
        if (!gerritSettings.getListAllChanges()) {
            query = appendQueryStringForProject(project, query);
        }
        getChanges(query, project, consumer);
    }

    public void getChanges(String query, final Project project, final Consumer<List<ChangeInfo>> consumer) {
        String request = formatRequestUrl("changes", query);
        request = appendToUrlQuery(request, "o=LABELS");
        gerritRestAccess.getRequest(request, project, new Consumer<ConsumerResult<JsonElement>>() {
            @Override
            public void consume(final ConsumerResult<JsonElement> result) {
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Parsing Gerrit changes", true) {
                    public void run(@NotNull ProgressIndicator indicator) {
                        List<ChangeInfo> changeInfoList = null;
                        if (!result.getException().isPresent()) {
                            changeInfoList = parseChangeInfos(result.getResult());
                        }
                        final List<ChangeInfo> finalChangeInfoList = changeInfoList;
                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                if (result.getException().isPresent()) {
                                    NotificationBuilder notification = new NotificationBuilder(
                                            project,
                                            "Failed to get Gerrit changes.",
                                            getErrorTextFromException(result.getException().get())
                                    );
                                    notificationService.notifyError(notification);
                                } else {
                                    consumer.consume(finalChangeInfoList);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private String appendQueryStringForProject(Project project, String query) {
        String projectQueryPart = getProjectQueryPart(project);
        query = Joiner.on('+').skipNulls().join(Strings.emptyToNull(query), projectQueryPart);
        return query;
    }

    private String formatRequestUrl(String endPoint, String query) {
        if (query.isEmpty()) {
            return String.format("/%s/", endPoint);
        } else {
            return String.format("/%s/?q=%s", endPoint, query);
        }
    }

    private String getProjectQueryPart(Project project) {
        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (repositories.isEmpty()) {
            showAddGitRepositoryNotification(project);
            return "";
        }

        List<GitRemote> remotes = Lists.newArrayList();
        for (GitRepository repository : repositories) {
            remotes.addAll(repository.getRemotes());
        }

        List<String> projectNames = Lists.newArrayList();
        for (GitRemote remote : remotes) {
            for (String repositoryUrl : remote.getUrls()) {
                if (UrlUtils.urlHasSameHost(repositoryUrl, gerritSettings.getHost())) {
                    projectNames.add("project:" + getProjectName(gerritSettings.getHost(), repositoryUrl));
                }
            }
        }

        if (projectNames.isEmpty()) {
            return "";
        }
        return String.format("(%s)", Joiner.on("+OR+").join(projectNames));
    }

    private String getProjectName(String repositoryUrl, String url) {
        if (!repositoryUrl.endsWith("/")) {
            repositoryUrl = repositoryUrl + "/";
        }

        String basePath = UrlUtils.createUriFromGitConfigString(repositoryUrl).getPath();
        String path = UrlUtils.createUriFromGitConfigString(url).getPath();

        path = path.substring(basePath.length());

        path = path.replace(".git", ""); // some repositories end their name with ".git"

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    public void showAddGitRepositoryNotification(final Project project) {
        NotificationBuilder notification = new NotificationBuilder(project, "Insufficient dependencies for Gerrit plugin",
                "Please configure a Git repository.<br/><a href='vcs'>Open Settings</a>")
                .listener(new NotificationListener() {
                    @Override
                    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            if (event.getDescription().equals("vcs")) {
                                ShowSettingsUtil.getInstance().showSettingsDialog(project, ActionsBundle.message("group.VcsGroup.text"));
                            }
                        }
                    }
                });
        notificationService.notifyWarning(notification);
    }

    public void getChangeDetails(@NotNull String changeNr, final Project project, final Consumer<ChangeInfo> consumer) {
        final String request = "/changes/?q=" + changeNr + "&o=CURRENT_REVISION&o=MESSAGES";
        gerritRestAccess.getRequest(request, project, new Consumer<ConsumerResult<JsonElement>>() {
            @Override
            public void consume(final ConsumerResult<JsonElement> result) {
                if (result.getException().isPresent()) {
                    // remove special handling (-> just notify error) once we drop Gerrit < 2.7 support
                    Exception exception = result.getException().get();
                    if (exception instanceof HttpStatusException && ((HttpStatusException) exception).getStatusCode() == 400) {
                        gerritRestAccess.getRequest(request.replace("&o=MESSAGES", ""), project, new Consumer<ConsumerResult<JsonElement>>() {
                            @Override
                            public void consume(final ConsumerResult<JsonElement> result) {
                                if (result.getException().isPresent()) {
                                    NotificationBuilder notification = new NotificationBuilder(
                                            project,
                                            "Failed to get Gerrit change.",
                                            getErrorTextFromException(result.getException().get())
                                    );
                                    notificationService.notifyError(notification);
                                } else {
                                    ChangeInfo changeInfo = parseSingleChangeInfos(result.getResult().getAsJsonArray().get(0).getAsJsonObject());
                                    consumer.consume(changeInfo);
                                }
                            }
                        });
                    } else {
                        NotificationBuilder notification = new NotificationBuilder(
                                project,
                                "Failed to get Gerrit change.",
                                getErrorTextFromException(exception)
                        );
                        notificationService.notifyError(notification);
                    }
                } else {
                    ChangeInfo changeInfo = parseSingleChangeInfos(result.getResult().getAsJsonArray().get(0).getAsJsonObject());
                    consumer.consume(changeInfo);
                }
            }
        });
    }

    @NotNull
    private List<ChangeInfo> parseChangeInfos(@NotNull JsonElement result) {
        if (!result.isJsonArray()) {
            log.assertTrue(result.isJsonObject(), String.format("Unexpected JSON result format: %s", result));
            return Collections.singletonList(parseSingleChangeInfos(result.getAsJsonObject()));
        }

        List<ChangeInfo> changeInfoList = new ArrayList<ChangeInfo>();
        for (JsonElement element : result.getAsJsonArray()) {
            log.assertTrue(element.isJsonObject(),
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
    public void getComments(@NotNull String changeId,
                            @NotNull String revision,
                            final Project project,
                            final Consumer<TreeMap<String, List<CommentInfo>>> consumer) {
        final String request = "/changes/" + changeId + "/revisions/" + revision + "/comments/";
        gerritRestAccess.getRequest(request, project, new Consumer<ConsumerResult<JsonElement>>() {
            @Override
            public void consume(ConsumerResult<JsonElement> result) {
                if (result.getException().isPresent()) {
                    Exception exception = result.getException().get();
                    // remove check once we drop Gerrit < 2.7 support and fail in any case
                    if (!(exception instanceof HttpStatusException) || ((HttpStatusException) exception).getStatusCode() != 404) {
                        NotificationBuilder notification = new NotificationBuilder(project,
                                "Failed to get Gerrit comments.", getErrorTextFromException(exception));
                        notificationService.notifyError(notification);
                    }
                } else {
                    consumer.consume(parseCommentInfos(result.getResult()));
                }
            }
        });
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
        if (gerritAuthData.isLoginAndPasswordAvailable()) {
            AccountInfo user = retrieveCurrentUserInfo(gerritAuthData);
            return user != null;
        } else {
            try {
                HttpResponse response = gerritApiUtil.doREST(gerritAuthData, "/", null, Collections.<Header>emptyList(),
                        GerritApiUtil.HttpVerb.GET);
                if (response.getStatusLine().getStatusCode() == 200) {
                    return true;
                }
            } catch (IOException e) {
                throw new RestApiException(e);
            }
            return false;
        }
    }

    @Nullable
    public AccountInfo retrieveCurrentUserInfo(@NotNull GerritAuthData gerritAuthData) throws RestApiException {
        JsonElement result = gerritApiUtil.getRequest(gerritAuthData, "/accounts/self");
        return parseUserInfo(result);
    }

    @Nullable
    private AccountInfo parseUserInfo(@Nullable JsonElement result) {
        if (result == null) {
            return null;
        }
        if (!result.isJsonObject()) {
            log.error(String.format("Unexpected JSON result format: %s", result));
            return null;
        }
        return gson.fromJson(result, AccountInfo.class);
    }

    @NotNull
    private List<ProjectInfo> getAvailableProjects() throws RestApiException {
        final String request = "/projects/";
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
            log.assertTrue(element.getValue().isJsonObject(),
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
            log.info(e);
            return false;
        }
    }

    public boolean checkCredentials(Project project, final GerritAuthData gerritAuthData) {
        if (Strings.isNullOrEmpty(gerritAuthData.getHost())) {
            return false;
        }
        Boolean result = accessToGerritWithModalProgress(project, new ThrowableComputable<Boolean, Exception>() {
            @Override
            public Boolean compute() throws Exception {
                ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to Gerrit");
                return testConnection(gerritAuthData);
            }
        }, gerritAuthData);
        return result == null ? false : result;
    }

    /**
     * Shows Gerrit login settings if credentials are wrong or empty and return the list of all projects
     */
    @Nullable
    public List<ProjectInfo> getAvailableProjects(final Project project) {
        while (!checkCredentials(project)) {
            final LoginDialog dialog = new LoginDialog(project, gerritSettings, this, log);
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
