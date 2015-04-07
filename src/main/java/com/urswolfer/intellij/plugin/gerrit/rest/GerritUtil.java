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

import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.*;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.*;
import com.google.gerrit.extensions.restapi.RestApiException;
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
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.gerrit.client.rest.http.HttpStatusException;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
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
import org.jetbrains.annotations.NotNull;

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

    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private Logger log;
    @Inject
    private NotificationService notificationService;
    @Inject
    private GerritRestApi gerritClient;
    @Inject
    private GerritRestApiFactory gerritRestApiFactory;
    @Inject
    private ProxyHttpClientBuilderExtension proxyHttpClientBuilderExtension;
    @Inject
    private SelectedRevisions selectedRevisions;

    public <T> T accessToGerritWithModalProgress(Project project,
                                                 final ThrowableComputable<T, Exception> computable) {
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

    public void postReview(final String changeId,
                           final String revision,
                           final ReviewInput reviewInput,
                           final Project project,
                           final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritClient.changes().id(changeId).revision(revision).review(reviewInput);
                    return null;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to post Gerrit review.");
    }

    public void postSubmit(final String changeId,
                           final SubmitInput submitInput,
                           final Project project,
                           final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritClient.changes().id(changeId).current().submit(submitInput);
                    return null;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to submit Gerrit change.");
    }

    @SuppressWarnings("unchecked")
    public void postAbandon(final String changeId,
                            final AbandonInput abandonInput,
                            final Project project) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritClient.changes().id(changeId).abandon(abandonInput);
                    return null;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, Consumer.EMPTY_CONSUMER, project, "Failed to abandon Gerrit change.");
    }

    @SuppressWarnings("unchecked")
    public void addReviewer(final String changeId,
                            final String reviewerName,
                            final Project project) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritClient.changes().id(changeId).addReviewer(reviewerName);
                    return null;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, Consumer.EMPTY_CONSUMER, project, "Failed to add reviewer.");
    }

    /**
     * Star-endpoint added in Gerrit 2.8.
     */
    @SuppressWarnings("unchecked")
    public void changeStarredStatus(final String id,
                                    final boolean starred,
                                    final Project project) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    if (starred) {
                        gerritClient.accounts().self().starChange(id);
                    } else {
                        gerritClient.accounts().self().unstarChange(id);
                    }
                    return null;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, Consumer.EMPTY_CONSUMER, project, "Failed to star Gerrit change." +
                "<br/>Not supported for Gerrit instances older than version 2.8.");
    }

    @SuppressWarnings("unchecked")
    public void setReviewed(final String changeId,
                            final String revision,
                            final String filePath,
                            final Project project) {
        if (!gerritSettings.isLoginAndPasswordAvailable()) {
            return;
        }
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritClient.changes().id(changeId).revision(revision).setReviewed(filePath, true);
                    return null;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, Consumer.EMPTY_CONSUMER, project, "Failed set file review status for Gerrit change.");
    }

    public void getChangesToReview(Project project, Consumer<List<ChangeInfo>> consumer) {
        Changes.QueryRequest queryRequest = gerritClient.changes().query("is:open+reviewer:self")
            .withOption(ListChangesOption.DETAILED_ACCOUNTS);
        getChanges(queryRequest, project, consumer);
    }

    public void getChangesForProject(String query, final Project project, final Consumer<LoadChangesProxy> consumer) {
        if (!gerritSettings.getListAllChanges()) {
            query = appendQueryStringForProject(project, query);
        }
        getChanges(query, project, consumer);
    }

    public void getChanges(final String query, final Project project, final Consumer<LoadChangesProxy> consumer) {
        Supplier<LoadChangesProxy> supplier = new Supplier<LoadChangesProxy>() {
            @Override
            public LoadChangesProxy get() {
                    Changes.QueryRequest queryRequest = gerritClient.changes().query(query)
                            .withOptions(EnumSet.of(
                                ListChangesOption.ALL_REVISIONS,
                                ListChangesOption.DETAILED_ACCOUNTS,
                                ListChangesOption.LABELS
                            ));
                    return new LoadChangesProxy(queryRequest, GerritUtil.this, project);
            }
        };
        accessGerrit(supplier, consumer, project);
    }

    public void getChanges(final Changes.QueryRequest queryRequest, final Project project, Consumer<List<ChangeInfo>> consumer) {
        Supplier<List<ChangeInfo>> supplier = new Supplier<List<ChangeInfo>>() {
            @Override
            public List<ChangeInfo> get() {
                try {
                    return queryRequest.get();
                } catch (RestApiException e) {
                    notifyError(e, "Failed to get Gerrit changes.", project);
                    return Collections.emptyList();
                }
            }
        };
        accessGerrit(supplier, consumer, project);
    }

    private String appendQueryStringForProject(Project project, String query) {
        String projectQueryPart = getProjectQueryPart(project);
        query = Joiner.on('+').skipNulls().join(Strings.emptyToNull(query), Strings.emptyToNull(projectQueryPart));
        return query;
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
        List<String> projectNames = getProjectNames(remotes);
        Iterable<String> projectNamesWithQueryPrefix = Iterables.transform(projectNames, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return "project:" + input;
            }
        });

        if (Iterables.isEmpty(projectNamesWithQueryPrefix)) {
            return "";
        }
        return String.format("(%s)", Joiner.on("+OR+").join(projectNamesWithQueryPrefix));
    }

    public List<String> getProjectNames(Collection<GitRemote> remotes) {
        List<String> projectNames = Lists.newArrayList();
        for (GitRemote remote : remotes) {
            for (String remoteUrl : remote.getUrls()) {
                remoteUrl = UrlUtils.stripGitExtension(remoteUrl);
                String projectName = getProjectName(gerritSettings.getHost(), remoteUrl);
                if (!Strings.isNullOrEmpty(projectName) && remoteUrl.endsWith(projectName)) {
                    projectNames.add(projectName);
                }
            }
        }
        return projectNames;
    }

    private String getProjectName(String repositoryUrl, String url) {
        if (!repositoryUrl.endsWith("/")) {
            repositoryUrl = repositoryUrl + "/";
        }

        String basePath = UrlUtils.createUriFromGitConfigString(repositoryUrl).getPath();
        String path = UrlUtils.createUriFromGitConfigString(url).getPath();

        if (path.length() >= basePath.length()) {
            path = path.substring(basePath.length());
        }

        path = UrlUtils.stripGitExtension(path);

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

    public void getChangeDetails(final int changeNr, final Project project, final Consumer<ChangeInfo> consumer) {
        Supplier<ChangeInfo> supplier = new Supplier<ChangeInfo>() {
            @Override
            public ChangeInfo get() {
                try {
                    EnumSet<ListChangesOption> options = EnumSet.of(
                            ListChangesOption.ALL_REVISIONS,
                            ListChangesOption.MESSAGES,
                            ListChangesOption.DETAILED_ACCOUNTS,
                            ListChangesOption.LABELS,
                            ListChangesOption.DETAILED_LABELS);
                    try {
                        return gerritClient.changes().id(changeNr).get(options);
                    } catch (HttpStatusException e) {
                        // remove special handling (-> just notify error) once we drop Gerrit < 2.7 support
                        if (e.getStatusCode() == 400) {
                            options.remove(ListChangesOption.MESSAGES);
                            return gerritClient.changes().id(changeNr).get(options);
                        } else {
                            throw e;
                        }
                    }
                } catch (RestApiException e) {
                    notifyError(e, "Failed to get Gerrit change.", project);
                    return new ChangeInfo();
                }
            }
        };
        accessGerrit(supplier, consumer, project);
    }

    /**
     * Support starting from Gerrit 2.7.
     */
    public void getComments(final String changeId,
                            final String revision,
                            final Project project,
                            final boolean includePublishedComments,
                            final boolean includeDraftComments,
                            final Consumer<Map<String, List<CommentInfo>>> consumer) {

        Supplier<Map<String, List<CommentInfo>>> supplier = new Supplier<Map<String, List<CommentInfo>>>() {
            @Override
            public Map<String, List<CommentInfo>> get() {
                try {
                    Map<String, List<CommentInfo>> comments;
                    if (includePublishedComments) {
                        comments = gerritClient.changes().id(changeId).revision(revision).comments();
                    } else {
                        comments = Maps.newHashMap();
                    }

                    Map<String, List<CommentInfo>> drafts;
                    if (includeDraftComments && gerritSettings.isLoginAndPasswordAvailable()) {
                        drafts = gerritClient.changes().id(changeId).revision(revision).drafts();
                    } else {
                        drafts = Maps.newHashMap();
                    }

                    HashMap<String, List<CommentInfo>> allComments = new HashMap<String, List<CommentInfo>>(drafts);
                    for (Map.Entry<String, List<CommentInfo>> entry : comments.entrySet()) {
                        List<CommentInfo> commentInfos = allComments.get(entry.getKey());
                        if (commentInfos != null) {
                            commentInfos.addAll(entry.getValue());
                        } else {
                            allComments.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return allComments;
                } catch (RestApiException e) {
                    // remove check once we drop Gerrit < 2.7 support and fail in any case
                    if (!(e instanceof HttpStatusException) || ((HttpStatusException) e).getStatusCode() != 404) {
                        notifyError(e, "Failed to get Gerrit comments.", project);
                    }
                    return new TreeMap<String, List<CommentInfo>>();
                }
            }
        };
        accessGerrit(supplier, consumer, project);
    }

    public void saveDraftComment(final int changeNr,
                                 final String revision,
                                 final DraftInput draftInput,
                                 final Project project,
                                 final Consumer<CommentInfo> consumer) {
        Supplier<CommentInfo> supplier = new Supplier<CommentInfo>() {
            @Override
            public CommentInfo get() {
                try {
                    CommentInfo commentInfo;
                    if (draftInput.id != null) {
                        commentInfo = gerritClient.changes().id(changeNr).revision(revision)
                                .draft(draftInput.id).update(draftInput);
                    } else {
                        DraftApi draftApi = gerritClient.changes().id(changeNr).revision(revision)
                                .createDraft(draftInput);
                        commentInfo = draftApi.get();
                    }
                    return commentInfo;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to save draft comment.");
    }

    public void deleteDraftComment(final int changeNr,
                                   final String revision,
                                   final String draftCommentId,
                                   final Project project,
                                   final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritClient.changes().id(changeNr).revision(revision).draft(draftCommentId).delete();
                    return null;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to delete draft comment.");
    }

    private boolean testConnection(GerritAuthData gerritAuthData) throws RestApiException {
        // we need to test with a temporary client with probably new (unsaved) credentials
        GerritApi tempClient = createClientWithCustomAuthData(gerritAuthData);
        if (gerritAuthData.isLoginAndPasswordAvailable()) {
            AccountInfo user = tempClient.accounts().self().get();
            return user != null;
        } else {
            tempClient.changes().query().withLimit(1).get();
            return true;
        }
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
        });
        return result == null ? false : result;
    }

    /**
     * Shows Gerrit login settings if credentials are wrong or empty and return the list of all projects
     */
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
                return gerritClient.projects().list().get();
            }
        });
    }

    public FetchInfo getFirstFetchInfo(ChangeInfo changeDetails) {
        RevisionInfo revisionInfo = changeDetails.revisions.get(selectedRevisions.get(changeDetails));
        return getFirstFetchInfo(revisionInfo);
    }

    public FetchInfo getFirstFetchInfo(RevisionInfo revisionInfo) {
        return Iterables.getFirst(revisionInfo.fetch.values(), null);
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

    public String getErrorTextFromException(Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            message = "(No exception message available)";
            log.error(message, t);
        }
        return message;
    }

    private <T> void accessGerrit(final Supplier<T> supplier, final Consumer<T> consumer, final Project project) {
        accessGerrit(supplier, consumer, project, null);
    }

    /**
     * @param errorMessage if the provided supplier throws an exception, this error message is displayed (if it is not null)
     *                     and the provided consumer will not be executed.
     */
    private <T> void accessGerrit(final Supplier<T> supplier,
                              final Consumer<T> consumer,
                              final Project project,
                              final String errorMessage) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                Task.Backgroundable backgroundTask = new Task.Backgroundable(project, "Accessing Gerrit", true) {
                    public void run(@NotNull ProgressIndicator indicator) {
                        try {
                            final T result = supplier.get();
                            ApplicationManager.getApplication().invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    //noinspection unchecked
                                    consumer.consume(result);
                                }
                            });
                        } catch (RuntimeException e) {
                            if (errorMessage != null) {
                                notifyError(e, errorMessage, project);
                            } else {
                                throw e;
                            }
                        }
                    }
                };
                backgroundTask.queue();
            }
        });
    }

    private void notifyError(Throwable throwable, String errorMessage, Project project) {
        NotificationBuilder notification = new NotificationBuilder(project, errorMessage, getErrorTextFromException(throwable));
        notificationService.notifyError(notification);
    }

    private GerritApi createClientWithCustomAuthData(GerritAuthData gerritAuthData) {
        return gerritRestApiFactory.create(gerritAuthData, proxyHttpClientBuilderExtension);
    }

    private double serverVersion = 0.0; // cache for server version which should not change once set over the life of this object, so only look it up once

    private static double parseVersion(String version) {
        if (version == null || version.length() == 0) {
            return 0.0;
        }

        try {
            final int fd = version.indexOf('.');
            final int sd = version.indexOf('.', fd + 1);
            if (0 < sd) {
                version = version.substring(0, sd);
            }
            return Double.parseDouble(version);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public void getServerVersion(final Project project,
                                        final Consumer<Double> consumer) {
        Supplier<Double> supplier = new Supplier<Double>() {
            @Override
            public Double get() {
                try {
                    if (serverVersion == 0.0) {
                        serverVersion = parseVersion(gerritClient.config().server().getVersion()); // idempotent, so do not worry about threading issues
                    }
                    return serverVersion;
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to get gerrit server version.");
    }
}
