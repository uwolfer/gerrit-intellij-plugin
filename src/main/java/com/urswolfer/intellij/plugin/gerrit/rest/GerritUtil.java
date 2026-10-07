/*
 * Copyright 2000-2011 JetBrains s.r.o.
 * Copyright 2013-2018 Urs Wolfer
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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.Consumer;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.http.HttpStatusException;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccountAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.ui.LoginDialog;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.GitUtil;
import git4idea.config.GitExecutableManager;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Parts based on org.jetbrains.plugins.github.GithubUtil
 *
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
@Service(Service.Level.APP)
public final class GerritUtil {
    private static final Logger LOG = Logger.getInstance(GerritUtil.class);

    /**
     * Longest change query sent in one request. Gerrit rejects a URL longer than 16 KB, and a proxy in front of it
     * often rejects one longer than 8 KB already; the query options make up the rest of the URL.
     */
    private static final int MAX_QUERY_LENGTH = 4000;

    public static GerritUtil getInstance() {
        return ApplicationManager.getApplication().getService(GerritUtil.class);
    }

    /**
     * Looked up per call rather than held in a field, so this class stays constructible outside a running IDE. A
     * project without an account gets the api of none, which fails the way an unconfigured host always has.
     */
    private GerritRestApi gerritApi(Project project) {
        return GerritApiProvider.getInstance().get(GerritProjectAccount.getInstance(project).get());
    }

    private boolean isLoginAndPasswordAvailable(Project project) {
        return GerritProjectAccount.getInstance(project).isLoginAndPasswordAvailable();
    }

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
        throw new RuntimeException(exception.get());
    }

    public void createMergeChange(final ChangeInput changeInput,
                                  final Project project,
                                  final Consumer<ChangeInfo> consumer) {
        Supplier<ChangeInfo> supplier = new Supplier<ChangeInfo>() {
            @Override
            public ChangeInfo get() {
                try {
                    return gerritApi(project).changes().create(changeInput).info();
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to create Gerrit merge change");
    }

    public void createMergePatchSet(final String changeId,
                                    final MergePatchSetInput mergePatchSetInput,
                                    final Project project,
                                    final Consumer<ChangeInfo> consumer) {
        Supplier<ChangeInfo> supplier = new Supplier<ChangeInfo>() {
            @Override
            public ChangeInfo get() {
                try {
                    return gerritApi(project).changes().id(changeId).createMergePatchSet(mergePatchSetInput);
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to refresh Gerrit merge patch set");
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
                    gerritApi(project).changes().id(changeId).revision(revision).review(reviewInput);
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to post Gerrit review");
    }

    public void postSubmit(final String changeId,
                           final SubmitInput submitInput,
                           final Project project,
                           final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritApi(project).changes().id(changeId).current().submit(submitInput);
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to submit Gerrit change");
    }

    @SuppressWarnings("unchecked")
    public void postPublish(final String changeId,
                            final Project project,
                            final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritApi(project).changes().id(changeId).publish();
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to publish Gerrit change");
    }

    @SuppressWarnings("unchecked")
    public void delete(final String changeId,
                       final Project project,
                       final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritApi(project).changes().id(changeId).delete();
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to delete Gerrit change");
    }

    @SuppressWarnings("unchecked")
    public void postAbandon(final String changeId,
                            final AbandonInput abandonInput,
                            final Project project,
                            final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritApi(project).changes().id(changeId).abandon(abandonInput);
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to abandon Gerrit change");
    }

    @SuppressWarnings("unchecked")
    public void addReviewer(final String changeId,
                            final String reviewerName,
                            final Project project,
                            final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritApi(project).changes().id(changeId).addReviewer(reviewerName);
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to add reviewer");
    }

    /**
     * @param assignee an empty value removes the assignee
     * @param consumer receives the new assignee, or null once it has been removed
     */
    public void setAssignee(final String changeId,
                            final String assignee,
                            final Project project,
                            final Consumer<AccountInfo> consumer) {
        Supplier<AccountInfo> supplier = () -> {
            try {
                ChangeApi changeApi = gerritApi(project).changes().id(changeId);
                if (assignee.isEmpty()) {
                    changeApi.deleteAssignee();
                    return null;
                }
                AssigneeInput input = new AssigneeInput();
                input.assignee = assignee;
                return changeApi.setAssignee(input);
            } catch (RestApiException e) {
                throw new RuntimeException(e);
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to set assignee");
    }

    /**
     * Star-endpoint added in Gerrit 2.8.
     */
    @SuppressWarnings("unchecked")
    public void changeStarredStatus(final String id,
                                    final boolean starred,
                                    final Project project,
                                    final Consumer<Void> consumer) {
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    if (starred) {
                        gerritApi(project).accounts().self().starChange(id);
                    } else {
                        gerritApi(project).accounts().self().unstarChange(id);
                    }
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to star Gerrit change " +
                "(not supported for Gerrit versions older than 2.8)");
    }

    @SuppressWarnings("unchecked")
    public void setReviewed(final int changeNr,
                            final String revision,
                            final String filePath,
                            final Project project) {
        if (!isLoginAndPasswordAvailable(project)) {
            return;
        }
        Supplier<Void> supplier = new Supplier<Void>() {
            @Override
            public Void get() {
                try {
                    gerritApi(project).changes().id(changeNr).revision(revision).setReviewed(filePath, true);
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, __ -> {}, project, "Failed set file review status for Gerrit change");
    }

    public void getChangesToReview(Project project, Consumer<List<ChangeInfo>> consumer) {
        Changes.QueryRequest queryRequest = gerritApi(project).changes().query("is:open+reviewer:self")
            .withOption(ListChangesOption.DETAILED_ACCOUNTS);
        getChanges(queryRequest, project, consumer);
    }

    public void getChangesForProject(String query, final Project project, final Consumer<LoadChangesProxy> consumer) {
        if (GerritSettings.getInstance().getListAllChanges()) {
            getChanges(query, project, consumer);
        } else {
            getChanges(appendQueryStringForProject(project, query), project, consumer);
        }
    }

    public void getChanges(final String query, final Project project, final Consumer<LoadChangesProxy> consumer) {
        getChanges(Collections.singletonList(query), project, consumer);
    }

    private void getChanges(final List<String> queries, final Project project, final Consumer<LoadChangesProxy> consumer) {
        Supplier<LoadChangesProxy> supplier = new Supplier<LoadChangesProxy>() {
            @Override
            public LoadChangesProxy get() {
                List<Changes.QueryRequest> queryRequests = new ArrayList<>();
                for (String query : queries) {
                    queryRequests.add(gerritApi(project).changes().query(query)
                            .withOptions(EnumSet.of(
                                ListChangesOption.ALL_REVISIONS,
                                ListChangesOption.DETAILED_ACCOUNTS,
                                ListChangesOption.CHANGE_ACTIONS,
                                ListChangesOption.CURRENT_ACTIONS,
                                ListChangesOption.DETAILED_LABELS,
                                ListChangesOption.LABELS,
                                ListChangesOption.SUBMITTABLE
                            )));
                }
                return new LoadChangesProxy(queryRequests, GerritUtil.this, project);
            }
        };
        accessGerrit(supplier, consumer, project);
    }

    public void getChanges(final Changes.QueryRequest queryRequest, final Project project, Consumer<List<ChangeInfo>> consumer) {
        accessGerrit(() -> {
            List<ChangeInfo> changeInfos = queryChanges(queryRequest, project);
            return changeInfos != null ? changeInfos : Collections.<ChangeInfo>emptyList();
        }, consumer, project);
    }

    /**
     * Loads the changes in the background, then hands them to the consumer on the event dispatch thread.
     */
    void loadChanges(Supplier<List<ChangeInfo>> loader, Project project, Consumer<List<ChangeInfo>> consumer) {
        accessGerrit(loader, consumer, project);
    }

    /**
     * Runs the query in the calling thread. A failure is reported to the user, and gives null.
     */
    @Nullable
    List<ChangeInfo> queryChanges(Changes.QueryRequest queryRequest, Project project) {
        try {
            return queryRequest.get();
        } catch (RestApiException e) {
            // remove special handling (-> just notify error) once we drop Gerrit < 2.9 support
            if (e instanceof HttpStatusException) {
                HttpStatusException httpStatusException = (HttpStatusException) e;
                if (httpStatusException.getStatusCode() == 400) {
                    boolean tryFallback = false;
                    String message = httpStatusException.getMessage();
                    if (message.matches(".*Content:.*\"-S\".*")) {
                        tryFallback = true;
                        queryRequest.withStart(0); // remove start, trust that sortkey is set
                    }
                    if (message.matches(".*Content:.*\"(CHANGE_ACTIONS|CURRENT_ACTIONS|SUBMITTABLE)\".*\"-o\".*")) {
                        tryFallback = true;
                        Set<ListChangesOption> options = queryRequest.getOptions();
                        options.remove(ListChangesOption.CHANGE_ACTIONS);
                        options.remove(ListChangesOption.CURRENT_ACTIONS);
                        options.remove(ListChangesOption.SUBMITTABLE);
                        queryRequest.withOptions(options);
                    }
                    if (tryFallback) {
                        try {
                            return queryRequest.get();
                        } catch (RestApiException ex) {
                            notifyError(ex, "Failed to get Gerrit changes.", project);
                            return null;
                        }
                    }
                }
            }
            notifyError(e, "Failed to get Gerrit changes.", project);
            return null;
        }
    }

    private List<String> appendQueryStringForProject(Project project, String query) {
        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (repositories.isEmpty()) {
            showAddGitRepositoryNotification(project);
        }
        List<String> projectNames = new ArrayList<>();
        for (GitRepository repository : repositories) {
            projectNames.addAll(getProjectNames(project, repository.getRemotes()));
        }
        return appendProjectQueryParts(query, projectNames, MAX_QUERY_LENGTH);
    }

    /**
     * One query for all projects becomes too long for a request with many repositories, so the projects are split
     * over as many queries as needed to keep each below the given length.
     */
    @VisibleForTesting
    static List<String> appendProjectQueryParts(String query, Collection<String> projectNames, int maxLength) {
        String prefix = query == null || query.isEmpty() ? "" : query + "+";
        List<String> queries = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        // a repository has several remotes, or the same remote for fetch and push
        for (String projectName : new LinkedHashSet<>(projectNames)) {
            String term = "project:" + Url.encode(projectName);
            if (part.length() > 0 && prefix.length() + part.length() + "+OR+".length() + term.length() + 2 > maxLength) {
                queries.add(prefix + "(" + part + ")");
                part.setLength(0);
            }
            part.append(part.length() > 0 ? "+OR+" : "").append(term);
        }
        if (part.length() > 0) {
            queries.add(prefix + "(" + part + ")");
        }
        if (queries.isEmpty()) {
            queries.add(query == null ? "" : query);
        }
        return queries;
    }

    public List<String> getProjectNames(Project project, Collection<GitRemote> remotes) {
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        return account != null ? getProjectNames(remotes, account.host, account.cloneBaseUrl)
            : getProjectNames(remotes, "", "");
    }

    /**
     * A remote on another host, such as a mirror on GitHub, would add its path as a project of the same name. Such
     * remotes only count when no remote is on the Gerrit host, as one reached through an SSH alias looks the same.
     */
    @VisibleForTesting
    static List<String> getProjectNames(Collection<GitRemote> remotes, String host, @Nullable String cloneBaseUrl) {
        List<String> onGerritHost = new ArrayList<>();
        List<String> elsewhere = new ArrayList<>();
        for (GitRemote remote : remotes) {
            for (String remoteUrl : remote.getUrls()) {
                boolean onHost = isOnHost(remoteUrl, host) || isOnHost(remoteUrl, cloneBaseUrl);
                addProjectName(onHost ? onGerritHost : elsewhere, remoteUrl, host, cloneBaseUrl);
            }
            // a remote can fetch from a mirror and push to Gerrit
            for (String pushUrl : remote.getPushUrls()) {
                if (isOnHost(pushUrl, host) || isOnHost(pushUrl, cloneBaseUrl)) {
                    addProjectName(onGerritHost, pushUrl, host, cloneBaseUrl);
                }
            }
        }
        return onGerritHost.isEmpty() ? elsewhere : onGerritHost;
    }

    private static void addProjectName(List<String> projectNames, String remoteUrl, String host,
                                       @Nullable String cloneBaseUrl) {
        String projectName = getRemoteProjectName(remoteUrl, host, cloneBaseUrl);
        if (projectName != null) {
            projectNames.add(projectName);
        }
    }

    private static boolean isOnHost(String remoteUrl, @Nullable String hostUrl) {
        if (hostUrl == null || hostUrl.isEmpty()) {
            return false;
        }
        try {
            return UrlUtils.urlHasSameHost(remoteUrl, hostUrl);
        } catch (IllegalArgumentException e) { // a url which is not a URI is on no host
            return false;
        }
    }

    /**
     * @return the project a remote url would be on the configured Gerrit, whichever host it is on, or {@code null}
     */
    @Nullable
    private static String getRemoteProjectName(String remoteUrl, String host, @Nullable String cloneBaseUrl) {
        String strippedUrl = UrlUtils.stripGitExtension(remoteUrl);
        String projectName;
        try {
            projectName = getProjectName(host, cloneBaseUrl, strippedUrl);
        } catch (IllegalArgumentException e) { // java.net.URI rejects some remotes git accepts, such as "/repos/[old]"
            return null;
        }
        if (projectName == null || projectName.isEmpty() || !strippedUrl.endsWith(projectName)) {
            return null;
        }
        return UrlUtils.stripAuthenticationPrefix(strippedUrl, projectName);
    }

    public void getProjectHead(final String projectName, final Project project, final Consumer<String> consumer) {
        Supplier<String> supplier = new Supplier<String>() {
            @Override
            public String get() {
                try {
                    return gerritApi(project).projects().name(projectName).head();
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to resolve Gerrit default branch");
    }

    public void getProjectBranches(final String projectName,
                                   final Project project,
                                   final Consumer<List<String>> consumer) {
        Supplier<List<String>> supplier = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                try {
                    List<String> branches = new ArrayList<>();
                    for (BranchInfo branchInfo : gerritApi(project).projects().name(projectName).branches().get()) {
                        if (branchInfo == null || branchInfo.ref == null || branchInfo.ref.isEmpty()) {
                            continue;
                        }
                        String branch = branchInfo.ref.trim();
                        if (branch.startsWith("refs/heads/") && branch.length() > "refs/heads/".length()) {
                            branches.add(branch);
                        }
                    }
                    Collections.sort(branches);
                    return branches;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to load Gerrit branches");
    }

    public static String getProjectName(String gerritUrl, String gerritCloneBaseUrl, String url) {
        String baseUrl = gerritCloneBaseUrl == null || gerritCloneBaseUrl.isEmpty() ? gerritUrl : gerritCloneBaseUrl;
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        String basePath = UrlUtils.createUriFromGitConfigString(baseUrl).getPath();
        String path = UrlUtils.createUriFromGitConfigString(url).getPath();

        if (path.length() >= basePath.length() && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }

        path = UrlUtils.stripGitExtension(path);

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        // gerrit project names usually don't start with a slash
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return path;
    }

    public void showAddGitRepositoryNotification(final Project project) {
        NotificationBuilder notification = new NotificationBuilder(project, "Insufficient dependencies for Gerrit plugin",
                "Please configure a Git repository.")
                .action(NotificationAction.createSimpleExpiring("Open Settings", new Runnable() {
                    @Override
                    public void run() {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project,
                                VcsBundle.message("version.control.main.configurable.name"));
                    }
                }));
        NotificationService.getInstance().notifyWarning(notification);
    }

    public void getChangeDetails(final int changeNr, final Project project, final Consumer<ChangeInfo> consumer) {
        getChangeDetails(null, changeNr, project, consumer);
    }

    /**
     * Calls the consumer only once the change got loaded, an error is shown otherwise.
     */
    public void getChangeDetails(final String projectName,
                                 final int changeNr,
                                 final Project project,
                                 final Consumer<ChangeInfo> consumer) {
        getChangeDetailsOrNull(projectName, changeNr, project, changeInfo -> {
            if (changeInfo != null) {
                consumer.consume(changeInfo);
            }
        });
    }

    /**
     * Hands {@code null} to the consumer if the change cannot be loaded, for a caller which can go on without it.
     */
    public void getChangeDetailsOrNull(final String projectName,
                                       final int changeNr,
                                       final Project project,
                                       final Consumer<ChangeInfo> consumer) {
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
                        if (projectName == null) {
                            return gerritApi(project).changes().id(changeNr).get(options);
                        }
                        return gerritApi(project).changes().id(projectName, changeNr).get(options);
                    } catch (HttpStatusException e) {
                        // remove special handling (-> just notify error) once we drop Gerrit < 2.7 support
                        if (e.getStatusCode() == 400) {
                            options.remove(ListChangesOption.MESSAGES);
                            if (projectName == null) {
                                return gerritApi(project).changes().id(changeNr).get(options);
                            }
                            return gerritApi(project).changes().id(projectName, changeNr).get(options);
                        } else {
                            throw e;
                        }
                    }
                } catch (RestApiException e) {
                    notifyError(e, "Failed to get Gerrit change.", project);
                    return null;
                }
            }
        };
        accessGerrit(supplier, consumer, project);
    }

    /**
     * Support starting from Gerrit 2.7.
     */
    public void getComments(final int changeNr,
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
                        comments = gerritApi(project).changes().id(changeNr).revision(revision).comments();
                    } else {
                        comments = new HashMap<>();
                    }

                    Map<String, List<CommentInfo>> drafts;
                    if (includeDraftComments && isLoginAndPasswordAvailable(project)) {
                        drafts = gerritApi(project).changes().id(changeNr).revision(revision).drafts();
                    } else {
                        drafts = new HashMap<>();
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
                        commentInfo = gerritApi(project).changes().id(changeNr).revision(revision)
                                .draft(draftInput.id).update(draftInput);
                    } else {
                        DraftApi draftApi = gerritApi(project).changes().id(changeNr).revision(revision)
                                .createDraft(draftInput);
                        commentInfo = draftApi.get();
                    }
                    return commentInfo;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to save draft comment");
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
                    gerritApi(project).changes().id(changeNr).revision(revision).draft(draftCommentId).delete();
                    return null;
                } catch (RestApiException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        accessGerrit(supplier, consumer, project, "Failed to delete draft comment");
    }

    private boolean testConnection(GerritAuthData gerritAuthData) throws RestApiException {
        // we need to test with a temporary client with probably new (unsaved) credentials
        GerritApi tempClient = createClientWithCustomAuthData(gerritAuthData);
        Changes.QueryRequest query = tempClient.changes().query();
        if (gerritAuthData.isLoginAndPasswordAvailable()) {
            query.withQuery("reviewer:self");
        }
        query.withLimit(1).get();
        return true;
    }

    /**
     * Checks if user has set up correct user credentials for access in the settings.
     *
     * @return true if we could successfully login with these credentials, false if authentication failed or in the case of some other error.
     */
    public boolean checkCredentials(final Project project) {
        try {
            return checkCredentials(project, new GerritAccountAuthData(GerritProjectAccount.getInstance(project).getId()));
        } catch (Exception e) {
            // this method is a quick-check if we've got valid user setup.
            // if an exception happens, we'll show the reason in the login dialog that will be shown right after checkCredentials failure.
            LOG.info(e);
            return false;
        }
    }

    public boolean checkCredentials(Project project, final GerritAuthData gerritAuthData) {
        String host = gerritAuthData.getHost();
        if (host == null || host.isEmpty()) {
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
            final LoginDialog dialog = new LoginDialog(project, GerritSettings.getInstance(), this);
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
                return gerritApi(project).projects().list().get();
            }
        });
    }

    public FetchInfo getFirstFetchInfo(Project project, ChangeInfo changeDetails) {
        if (changeDetails.revisions == null) {
            return null;
        }
        RevisionInfo revisionInfo =
            changeDetails.revisions.get(SelectedRevisions.getInstance(project).get(changeDetails));
        return getFirstFetchInfo(project, revisionInfo);
    }

    public FetchInfo getFirstFetchInfo(Project project, RevisionInfo revisionInfo) {
        return getFirstFetchInfo(revisionInfo, () -> GerritProjectAccount.getInstance(project).getHost());
    }

    /**
     * Gerrit fills in the fetch information only with a plugin which provides download schemes, such as
     * download-commands, installed. Since Gerrit 2.11 every patch set can still be fetched by its ref. Remotes are
     * matched against the clone base URL anyway, so the host makes the ones on it match as well.
     */
    public static FetchInfo getFirstFetchInfo(RevisionInfo revisionInfo, Supplier<String> gerritUrl) {
        if (revisionInfo == null) {
            return null;
        }
        if (revisionInfo.fetch != null) {
            Iterator<FetchInfo> fetchInfos = revisionInfo.fetch.values().iterator();
            if (fetchInfos.hasNext()) {
                return fetchInfos.next();
            }
        }
        return revisionInfo.ref != null ? new FetchInfo(gerritUrl.get(), revisionInfo.ref) : null;
    }

    @SuppressWarnings("UnresolvedPropertyKey")
    public boolean testGitExecutable(final Project project) {
        final GitVersion version;
        try {
            version = GitExecutableManager.getInstance().getVersion(project);
        } catch (Exception e) {
            Messages.showErrorDialog(project, e.getMessage(), GitBundle.message("find.git.error.title"));
            return false;
        }

        if (!version.isSupported()) {
            Messages.showWarningDialog(project, GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                    GitBundle.message("find.git.success.title"));
            return false;
        }
        return true;
    }

    public String getErrorTextFromException(Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            message = "(No exception message available)";
            LOG.error(message, t);
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
                if (project.isDisposed()) {
                    return;
                }
                Task.Backgroundable backgroundTask = new Task.Backgroundable(project, "Accessing Gerrit", true) {
                    public void run(@NotNull ProgressIndicator indicator) {
                        if (project.isDisposed()) {
                            return;
                        }
                        try {
                            final T result = supplier.get();
                            ApplicationManager.getApplication().invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    if (project.isDisposed()) {
                                        return;
                                    }
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
        NotificationService.getInstance().notifyError(notification);
    }

    private GerritApi createClientWithCustomAuthData(GerritAuthData gerritAuthData) {
        return GerritApiProvider.getInstance().create(gerritAuthData);
    }
}
