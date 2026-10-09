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
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.Consumer;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.http.HttpStatusException;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // The client puts Gerrit's answer on a line of its own after "Content:", hence DOTALL.
    private static final Pattern UNSUPPORTED_OPTION = Pattern.compile(
        "Content:.*\"(CHANGE_ACTIONS|SUBMITTABLE)\".*\"-o\"", Pattern.DOTALL);

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

    private <T> T accessToGerritWithModalProgress(Project project,
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
        callGerrit(() -> gerritApi(project).changes().create(changeInput).info(),
            consumer, project, "Failed to create Gerrit merge change");
    }

    public void createMergePatchSet(final String changeId,
                                    final MergePatchSetInput mergePatchSetInput,
                                    final Project project,
                                    final Consumer<ChangeInfo> consumer) {
        callGerrit(() -> gerritApi(project).changes().id(changeId).createMergePatchSet(mergePatchSetInput),
            consumer, project, "Failed to refresh Gerrit merge patch set");
    }

    public void postReview(final String changeId,
                           final String revision,
                           final ReviewInput reviewInput,
                           final Project project,
                           final Consumer<Void> consumer) {
        callGerrit(() -> {
            gerritApi(project).changes().id(changeId).revision(revision).review(reviewInput);
            return null;
        }, consumer, project, "Failed to post Gerrit review");
    }

    public void postSubmit(final String changeId,
                           final SubmitInput submitInput,
                           final Project project,
                           final Consumer<Void> consumer) {
        callGerrit(() -> {
            gerritApi(project).changes().id(changeId).current().submit(submitInput);
            return null;
        }, consumer, project, "Failed to submit Gerrit change");
    }

    public void postPublish(final String changeId,
                            final Project project,
                            final Consumer<Void> consumer) {
        callGerrit(() -> {
            gerritApi(project).changes().id(changeId).publish();
            return null;
        }, consumer, project, "Failed to publish Gerrit change");
    }

    public void delete(final String changeId,
                       final Project project,
                       final Consumer<Void> consumer) {
        callGerrit(() -> {
            gerritApi(project).changes().id(changeId).delete();
            return null;
        }, consumer, project, "Failed to delete Gerrit change");
    }

    public void postAbandon(final String changeId,
                            final AbandonInput abandonInput,
                            final Project project,
                            final Consumer<Void> consumer) {
        callGerrit(() -> {
            gerritApi(project).changes().id(changeId).abandon(abandonInput);
            return null;
        }, consumer, project, "Failed to abandon Gerrit change");
    }

    public void addReviewer(final String changeId,
                            final String reviewerName,
                            final Project project,
                            final Consumer<Void> consumer) {
        callGerrit(() -> {
            gerritApi(project).changes().id(changeId).addReviewer(reviewerName);
            return null;
        }, consumer, project, "Failed to add reviewer");
    }

    /**
     * @param assignee an empty value removes the assignee
     * @param consumer receives the new assignee, or null once it has been removed
     */
    public void setAssignee(final String changeId,
                            final String assignee,
                            final Project project,
                            final Consumer<AccountInfo> consumer) {
        callGerrit(() -> {
            ChangeApi changeApi = gerritApi(project).changes().id(changeId);
            if (assignee.isEmpty()) {
                changeApi.deleteAssignee();
                return null;
            }
            AssigneeInput input = new AssigneeInput();
            input.assignee = assignee;
            return changeApi.setAssignee(input);
        }, consumer, project, "Failed to set assignee");
    }

    public void changeStarredStatus(final String id,
                                    final boolean starred,
                                    final Project project,
                                    final Consumer<Void> consumer) {
        callGerrit(() -> {
            if (starred) {
                gerritApi(project).accounts().self().starChange(id);
            } else {
                gerritApi(project).accounts().self().unstarChange(id);
            }
            return null;
        }, consumer, project, "Failed to star Gerrit change");
    }

    public void setReviewed(final int changeNr,
                            final String revision,
                            final String filePath,
                            final Project project) {
        if (!isLoginAndPasswordAvailable(project)) {
            return;
        }
        callGerrit(() -> {
            gerritApi(project).changes().id(changeNr).revision(revision).setReviewed(filePath, true);
            return null;
        }, __ -> {}, project, "Failed set file review status for Gerrit change");
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
        accessGerrit(() -> {
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
            return new LoadChangesProxy(queryRequests, this, project);
        }, consumer, project);
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
        while (true) {
            try {
                return queryRequest.get();
            } catch (RestApiException e) {
                if (!(e instanceof HttpStatusException && ((HttpStatusException) e).getStatusCode() == 400
                        && withoutUnsupportedOption(queryRequest, e.getMessage()))) {
                    notifyError(e, "Failed to get Gerrit changes.", project);
                    return null;
                }
            }
        }
    }

    /**
     * Drops the option which the 400 of an older Gerrit names, and tells whether it did; Gerrit names one at a time.
     * Gerrit 2.9 knows neither CHANGE_ACTIONS nor SUBMITTABLE, and gives the actions of the change for CURRENT_ACTIONS,
     * which is kept.
     */
    @VisibleForTesting
    static boolean withoutUnsupportedOption(Changes.QueryRequest queryRequest, String message) {
        Matcher matcher = UNSUPPORTED_OPTION.matcher(message);
        if (!matcher.find()) {
            return false;
        }
        Set<ListChangesOption> options = queryRequest.getOptions();
        // false for an option which is gone already, so that the caller does not retry forever
        if (!options.remove(ListChangesOption.valueOf(matcher.group(1)))) {
            return false;
        }
        queryRequest.withOptions(options);
        return true;
    }

    private List<String> appendQueryStringForProject(Project project, String query) {
        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (repositories.isEmpty()) {
            GerritGitUtil.getInstance().showAddGitRepositoryNotification(project);
        }
        List<String> projectNames = new ArrayList<>();
        for (GitRepository repository : repositories) {
            projectNames.addAll(GerritRemotes.getProjectNames(project, repository.getRemotes()));
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

    public void getProjectHead(final String projectName, final Project project, final Consumer<String> consumer) {
        callGerrit(() -> gerritApi(project).projects().name(projectName).head(),
            consumer, project, "Failed to resolve Gerrit default branch");
    }

    public void getProjectBranches(final String projectName,
                                   final Project project,
                                   final Consumer<List<String>> consumer) {
        callGerrit(() -> {
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
        }, consumer, project, "Failed to load Gerrit branches");
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
        accessGerrit(() -> {
            try {
                EnumSet<ListChangesOption> options = EnumSet.of(
                        ListChangesOption.ALL_REVISIONS,
                        ListChangesOption.MESSAGES,
                        ListChangesOption.DETAILED_ACCOUNTS,
                        ListChangesOption.LABELS,
                        ListChangesOption.DETAILED_LABELS);
                if (projectName == null) {
                    return gerritApi(project).changes().id(changeNr).get(options);
                }
                return gerritApi(project).changes().id(projectName, changeNr).get(options);
            } catch (RestApiException e) {
                notifyError(e, "Failed to get Gerrit change.", project);
                return null;
            }
        }, consumer, project);
    }

    public void getComments(final int changeNr,
                            final String revision,
                            final Project project,
                            final boolean includePublishedComments,
                            final boolean includeDraftComments,
                            final Consumer<Map<String, List<CommentInfo>>> consumer) {
        accessGerrit(() -> {
            try {
                return loadComments(changeNr, revision, project, includePublishedComments, includeDraftComments);
            } catch (RestApiException e) {
                notifyError(e, "Failed to get Gerrit comments.", project);
                return new TreeMap<String, List<CommentInfo>>();
            }
        }, consumer, project);
    }

    /**
     * Runs in the calling thread.
     */
    public Map<String, List<CommentInfo>> loadComments(int changeNr,
                                                       String revision,
                                                       Project project,
                                                       boolean includePublishedComments,
                                                       boolean includeDraftComments) throws RestApiException {
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
    }

    /**
     * The changes a query finds, with all their patch sets and the files of the current one. Runs in the calling
     * thread, and reports a failure to the caller only, for lookups the user did not ask for.
     */
    public List<ChangeInfo> queryChangesWithFiles(String query, Project project) throws RestApiException {
        return gerritApi(project).changes().query(query)
            .withOptions(EnumSet.of(ListChangesOption.ALL_REVISIONS, ListChangesOption.CURRENT_FILES)).get();
    }

    /**
     * Runs in the calling thread.
     */
    public Map<String, FileInfo> getRevisionFiles(int changeNr, String revision, Project project)
            throws RestApiException {
        return gerritApi(project).changes().id(changeNr).revision(revision).files();
    }

    /**
     * Runs in the calling thread.
     */
    public byte[] getFileContent(int changeNr, String revision, String path, Project project)
            throws RestApiException, IOException {
        BinaryResult content = gerritApi(project).changes().id(changeNr).revision(revision).file(path).content();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            content.writeTo(bytes);
        } finally {
            content.close();
        }
        // the client only takes note of Gerrit's header, the text it passes on is still Base64
        return content.isBase64() ? Base64.getMimeDecoder().decode(bytes.toByteArray()) : bytes.toByteArray();
    }

    /**
     * The files Gerrit lists between two revisions of a change, a renamed one under its old path too. Runs in the
     * calling thread.
     *
     * @return null when Gerrit could not be asked
     */
    @Nullable
    public Set<String> getFilePaths(int changeNr, String revision, String baseRevision, Project project) {
        // RevisionApi.files(base) is not implemented by the REST client
        String url = String.format("/changes/%s/revisions/%s/files?base=%s",
            changeNr, Url.encode(revision), Url.encode(baseRevision));
        try {
            JsonElement files = gerritApi(project).restClient().getRequest(url);
            if (files != null && files.isJsonObject()) {
                return filePaths(files.getAsJsonObject());
            }
            LOG.warn("Unexpected answer for the files between revisions " + baseRevision + " and " + revision);
        } catch (RestApiException | JsonParseException e) {
            LOG.warn("Failed to get the files between revisions " + baseRevision + " and " + revision, e);
        }
        return null;
    }

    @VisibleForTesting
    static Set<String> filePaths(JsonObject files) {
        Set<String> paths = new HashSet<>();
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            paths.add(file.getKey());
            if (file.getValue().isJsonObject()) {
                JsonObject info = file.getValue().getAsJsonObject();
                // the source of a copy stays in place, and is listed on its own when it differs
                if (isString(info.get("status"), "R") && isString(info.get("old_path"), null)) {
                    paths.add(info.get("old_path").getAsString());
                }
            }
        }
        return paths;
    }

    private static boolean isString(@Nullable JsonElement element, @Nullable String value) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
            && (value == null || value.equals(element.getAsString()));
    }

    public void saveDraftComment(final int changeNr,
                                 final String revision,
                                 final DraftInput draftInput,
                                 final Project project,
                                 final Consumer<CommentInfo> consumer) {
        callGerrit(() -> {
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
        }, consumer, project, "Failed to save draft comment");
    }

    public void deleteDraftComment(final int changeNr,
                                   final String revision,
                                   final String draftCommentId,
                                   final Project project,
                                   final Consumer<Void> consumer) {
        callGerrit(() -> {
            gerritApi(project).changes().id(changeNr).revision(revision).draft(draftCommentId).delete();
            return null;
        }, consumer, project, "Failed to delete draft comment");
    }

    /*
     * The drafts which the review dialog lists and changes are accessed with a modal progress: the consumer of
     * accessGerrit is called by invokeLater, which waits until the review dialog is closed.
     */

    public Map<String, List<CommentInfo>> getDraftCommentsWithModalProgress(final int changeNr,
                                                                            final String revision,
                                                                            final Project project) {
        return accessToGerritWithModalProgress(project,
            () -> gerritApi(project).changes().id(changeNr).revision(revision).drafts());
    }

    public CommentInfo saveDraftCommentWithModalProgress(final int changeNr,
                                                         final String revision,
                                                         final DraftInput draftInput,
                                                         final Project project) {
        return accessToGerritWithModalProgress(project,
            () -> gerritApi(project).changes().id(changeNr).revision(revision).draft(draftInput.id).update(draftInput));
    }

    public void deleteDraftCommentWithModalProgress(final int changeNr,
                                                    final String revision,
                                                    final String draftCommentId,
                                                    final Project project) {
        accessToGerritWithModalProgress(project, () -> {
            gerritApi(project).changes().id(changeNr).revision(revision).draft(draftCommentId).delete();
            return null;
        });
    }

    /**
     * Tries the credentials, which need not be stored yet, on Gerrit; fails where it refuses them or cannot be asked.
     */
    public boolean testConnection(GerritAuthData gerritAuthData) throws RestApiException {
        // we need to test with a temporary client with probably new (unsaved) credentials
        GerritApi tempClient = createClientWithCustomAuthData(gerritAuthData);
        Changes.QueryRequest query = tempClient.changes().query();
        if (gerritAuthData.isLoginAndPasswordAvailable()) {
            query.withQuery("reviewer:self");
        }
        query.withLimit(1).get();
        return true;
    }

    public boolean checkCredentials(Project project, final GerritAuthData gerritAuthData) {
        String host = gerritAuthData.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        Boolean result = accessToGerritWithModalProgress(project, () -> {
            ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to Gerrit");
            return testConnection(gerritAuthData);
        });
        return result == null ? false : result;
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
     * A failure is reported with the error message, and the consumer is not called.
     */
    private <T> void callGerrit(final ThrowableComputable<T, RestApiException> call,
                                final Consumer<T> consumer,
                                final Project project,
                                final String errorMessage) {
        accessGerrit(() -> {
            try {
                return call.compute();
            } catch (RestApiException e) {
                throw new RuntimeException(e);
            }
        }, consumer, project, errorMessage);
    }

    /**
     * @param errorMessage if the provided supplier throws an exception, this error message is displayed (if it is not null)
     *                     and the provided consumer will not be executed.
     */
    private <T> void accessGerrit(final Supplier<T> supplier,
                              final Consumer<T> consumer,
                              final Project project,
                              final String errorMessage) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            new Task.Backgroundable(project, "Accessing Gerrit", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    if (project.isDisposed()) {
                        return;
                    }
                    try {
                        final T result = supplier.get();
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (!project.isDisposed()) {
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
            }.queue();
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
