/*
 * Copyright 2013-2015 Urs Wolfer
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.urswolfer.intellij.plugin.gerrit.git;

import static git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT;
import static git4idea.commands.GitSimpleEventDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsShortCommitDetailsImpl;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.GitCommit;
import git4idea.GitLocalBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerListener;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector;
import git4idea.fetch.GitFetchResult;
import git4idea.fetch.GitFetchSupport;
import git4idea.history.GitHistoryUtils;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Urs Wolfer
 */
@Service(Service.Level.APP)
public final class GerritGitUtil {

    public static GerritGitUtil getInstance() {
        return ApplicationManager.getApplication().getService(GerritGitUtil.class);
    }

    public Iterable<GitRepository> getRepositories(Project project) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        return repositoryManager.getRepositories();
    }

    public Optional<GitRepository> getRepositoryForChange(Project project, ChangeInfo change) {
        GerritSettings settings = GerritSettings.getInstance();
        return getRepositoryForChange(getRepositories(project), project.getBasePath(),
            settings.getHost(), settings.getCloneBaseUrl(), change);
    }

    @VisibleForTesting
    static Optional<GitRepository> getRepositoryForChange(Iterable<GitRepository> repositories,
                                                          @Nullable String projectBasePath,
                                                          @Nullable String gerritHost,
                                                          @Nullable String cloneBaseUrl,
                                                          ChangeInfo change) {
        ProjectMatcher matcher = new ProjectMatcher(change.project, gerritHost, cloneBaseUrl);
        // a weaker match is still accepted on its own, but it must not win over a root whose remote really is the
        // project: "my-app" and "team/app" both end with "app"
        List<GitRepository> candidates = new ArrayList<>();
        RemoteMatch best = RemoteMatch.SUFFIX;
        for (GitRepository repository : repositories) {
            RemoteMatch match = matcher.match(repository);
            if (match.compareTo(best) > 0) {
                candidates.clear();
                best = match;
            }
            if (match == best) {
                candidates.add(repository);
            }
        }
        RemoteMatch candidateMatch = best;
        return pickPreferredRepository(candidates, projectBasePath, change.branch,
            remote -> matcher.match(remote) == candidateMatch);
    }

    private enum RemoteMatch { NONE, SUFFIX, SEGMENT, EXACT }

    private static final class ProjectMatcher {
        private final String gerritProjectName;
        private final List<String> gerritBaseUrls = new ArrayList<>();

        ProjectMatcher(String gerritProjectName, @Nullable String gerritHost, @Nullable String cloneBaseUrl) {
            this.gerritProjectName = gerritProjectName;
            // the host and the clone base URL can differ in their path as well, so a remote is resolved against
            // each one it lives on
            for (String baseUrl : Arrays.asList(gerritHost, cloneBaseUrl)) {
                if (!StringUtil.isEmpty(baseUrl)) {
                    gerritBaseUrls.add(baseUrl);
                }
            }
        }

        RemoteMatch match(GitRepository repository) {
            RemoteMatch best = RemoteMatch.NONE;
            for (GitRemote remote : repository.getRemotes()) {
                best = max(best, match(remote));
            }
            return best;
        }

        RemoteMatch match(GitRemote remote) {
            RemoteMatch best = remote.getName().equals(gerritProjectName) ? RemoteMatch.SEGMENT : RemoteMatch.NONE;
            for (String remoteUrl : remote.getUrls()) {
                remoteUrl = UrlUtils.stripGitExtension(remoteUrl);
                if (remoteUrl != null && remoteUrl.endsWith(gerritProjectName)) {
                    best = max(best, match(remoteUrl));
                }
            }
            return best;
        }

        private RemoteMatch match(String remoteUrl) {
            List<String> projectNames = projectNamesOnGerrit(remoteUrl);
            if (projectNames.contains(gerritProjectName)) {
                return RemoteMatch.EXACT;
            }
            if (!projectNames.isEmpty()) {
                // on the Gerrit host, the URL names its project exactly, and that is another one
                return RemoteMatch.SUFFIX;
            }
            // the separators cover "host/project", scp-like "host:project" and local Windows paths
            int separator = remoteUrl.length() - gerritProjectName.length() - 1;
            if (separator < 0 || "/:\\".indexOf(remoteUrl.charAt(separator)) >= 0) {
                return RemoteMatch.SEGMENT;
            }
            return RemoteMatch.SUFFIX;
        }

        private List<String> projectNamesOnGerrit(String remoteUrl) {
            String url = UrlUtils.normalizeScpLikeUrl(remoteUrl);
            List<String> projectNames = new ArrayList<>();
            for (String baseUrl : gerritBaseUrls) {
                try {
                    if (UrlUtils.urlHasSameHost(url, baseUrl)) {
                        projectNames.add(UrlUtils.stripAuthenticationPrefix(url, GerritUtil.getProjectName(baseUrl, null, url)));
                    }
                } catch (IllegalArgumentException e) {
                    // java.net.URI rejects some remotes git accepts; they are matched by the weaker rules only
                }
            }
            return projectNames;
        }

        private static RemoteMatch max(RemoteMatch a, RemoteMatch b) {
            return a.compareTo(b) >= 0 ? a : b;
        }
    }

    private static Optional<GitRepository> pickPreferredRepository(List<GitRepository> candidates,
                                                                   @Nullable String projectBasePath,
                                                                   @Nullable String branch,
                                                                   Predicate<GitRemote> isMatchingRemote) {
        // several roots can carry the same project, e.g. submodules kept as branches of the main repository; the
        // branch a root tracks tells them apart, failing that the root the IDE project was opened on is the one the
        // user works in, and then the outermost one, as a submodule is nested inside its superproject
        List<GitRepository> onBranch = candidates.stream()
            .filter(candidate -> tracksBranch(candidate, branch, isMatchingRemote))
            .collect(Collectors.toList());
        List<GitRepository> preferred = onBranch.isEmpty() ? candidates : onBranch;
        if (projectBasePath != null) {
            for (GitRepository candidate : preferred) {
                if (FileUtil.pathsEqual(rootPath(candidate), projectBasePath)) {
                    return Optional.of(candidate);
                }
            }
        }
        return preferred.stream()
            .filter(candidate -> preferred.stream().noneMatch(other ->
                FileUtil.isAncestor(rootPath(other), rootPath(candidate), true)))
            // the platform keeps its roots in a hash map, so its order must not decide
            .min(Comparator.comparing(GerritGitUtil::rootPath));
    }

    private static boolean tracksBranch(GitRepository repository,
                                        @Nullable String branch,
                                        Predicate<GitRemote> isMatchingRemote) {
        GitLocalBranch currentBranch = repository.getCurrentBranch();
        if (branch == null || currentBranch == null) {
            return false;
        }
        // CheckoutAction sets this upstream on the branch it creates, so a checked out change keeps its root
        GitBranchTrackInfo trackInfo = repository.getBranchTrackInfo(currentBranch.getName());
        return trackInfo != null
            && isMatchingRemote.test(trackInfo.getRemote())
            && trackInfo.getRemoteBranch().getNameForRemoteOperations().equals(branch);
    }

    private static String rootPath(GitRepository repository) {
        return repository.getRoot().getPath();
    }

    public Optional<GitRemote> getRemoteForChange(Project project, GitRepository gitRepository, FetchInfo fetchInfo) {
        List<GitRemote> remotes = getRemotesForChange(gitRepository, fetchInfo);
        if (remotes.isEmpty()) {
            notifyNoRemoteForChange(project, gitRepository);
            return Optional.empty();
        }
        return Optional.of(remotes.get(0));
    }

    /**
     * @return the remotes on the Gerrit host, the one the current branch tracks first
     */
    @VisibleForTesting
    List<GitRemote> getRemotesForChange(GitRepository gitRepository, FetchInfo fetchInfo) {
        List<GitRemote> remotes = new ArrayList<GitRemote>();
        for (GitRemote remote : gitRepository.getRemotes()) {
            if (isOnGerritHost(remote, fetchInfo.url)) {
                remotes.add(remote);
            }
        }
        // several remotes can point to the Gerrit host, for example over SSH and HTTP, and not every one of
        // them has to be reachable; the one the user works with is the most likely to be
        Optional<GitRemote> trackedRemote = getTrackedRemote(gitRepository);
        if (trackedRemote.isPresent() && remotes.remove(trackedRemote.get())) {
            remotes.add(0, trackedRemote.get());
        }
        return remotes;
    }

    private static boolean isOnGerritHost(GitRemote remote, String fetchUrl) {
        List<String> repositoryUrls = new ArrayList<String>();
        repositoryUrls.addAll(remote.getUrls());
        repositoryUrls.addAll(remote.getPushUrls());
        for (String repositoryUrl : repositoryUrls) {
            if (UrlUtils.urlHasSameHost(repositoryUrl, fetchUrl)
                || UrlUtils.urlHasSameHost(repositoryUrl, GerritSettings.getInstance().getCloneBaseUrlOrHost())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<GitRemote> getTrackedRemote(GitRepository gitRepository) {
        GitLocalBranch currentBranch = gitRepository.getCurrentBranch();
        if (currentBranch == null) {
            return Optional.empty();
        }
        GitBranchTrackInfo trackInfo = gitRepository.getBranchTrackInfo(currentBranch.getName());
        return trackInfo != null ? Optional.of(trackInfo.getRemote()) : Optional.empty();
    }

    private static void notifyNoRemoteForChange(Project project, GitRepository gitRepository) {
        NotificationBuilder notification = new NotificationBuilder(project, "Error",
            String.format("Could not fetch commit because no remote url matches Gerrit host.<br/>" +
                "Git repository: '%s'.", gitRepository.getPresentableUrl()));
        NotificationService.getInstance().notifyError(notification);
    }

    public void fetchChange(final Project project,
                            final GitRepository gitRepository,
                            final FetchInfo fetchInfo,
                            final String commitHash,
                            @Nullable final Callable<Void> fetchCallback) {
        GitVcs.runInBackground(new Task.Backgroundable(project, "Fetching...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                boolean commitIsFetched = checkIfCommitIsFetched(gitRepository, commitHash);
                fetchIfMissing(project, gitRepository, fetchInfo, commitIsFetched, fetchCallback);
            }
        });
    }

    // The callers work with the commit hash, so a commit which is already local needs no fetch.
    @VisibleForTesting
    void fetchIfMissing(Project project,
                        GitRepository gitRepository,
                        FetchInfo fetchInfo,
                        boolean commitIsFetched,
                        @Nullable Callable<Void> fetchCallback) {
        if (commitIsFetched) {
            runCallback(fetchCallback);
            return;
        }
        List<GitRemote> remotes = getRemotesForChange(gitRepository, fetchInfo);
        if (remotes.isEmpty()) {
            notifyNoRemoteForChange(project, gitRepository);
            return;
        }
        List<GitFetchResult> failedFetches = new ArrayList<GitFetchResult>();
        for (GitRemote remote : remotes) {
            GitFetchResult result = GitFetchSupport.fetchSupport(project).fetch(gitRepository, remote, fetchInfo.ref);
            if (succeeded(result)) {
                runCallback(fetchCallback);
                return;
            }
            failedFetches.add(result);
        }
        // a failed fetch leaves the commit missing, so the callers would fail
        for (GitFetchResult failedFetch : failedFetches) {
            failedFetch.showNotificationIfFailed();
        }
    }

    private static boolean succeeded(GitFetchResult result) {
        // the only way to check without notifying; the Kotlin implementation throws a VcsException
        // without declaring it, so it cannot be caught as such
        try {
            result.throwExceptionIfFailed();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void runCallback(@Nullable Callable<Void> fetchCallback) {
        try {
            if (fetchCallback != null) {
                fetchCallback.call();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void cherryPickChange(final Project project, final ChangeInfo changeInfo, final String revisionId) {
        FileDocumentManager.getInstance().saveAllDocuments();
        ChangeListManagerEx.getInstanceEx(project).blockModalNotifications();

        new Task.Backgroundable(project, "Cherry-picking...", false) {
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    Optional<GitRepository> gitRepositoryOptional = getRepositoryForChange(project, changeInfo);
                    if (!gitRepositoryOptional.isPresent()) {
                        NotificationBuilder notification = new NotificationBuilder(project, "Error",
                            String.format("No repository found for Gerrit project: '%s'.", changeInfo.project));
                        NotificationService.getInstance().notifyError(notification);
                        return;
                    }
                    GitRepository gitRepository = gitRepositoryOptional.get();

                    final VirtualFile virtualFile = gitRepository.getRoot();

                    final String notLoaded = "Not loaded";
                    VcsUser notLoadedUser = project.getService(VcsUserRegistry.class).createUser(notLoaded, notLoaded);
                    VcsShortCommitDetails gitCommit = new VcsShortCommitDetailsImpl(
                        HashImpl.build(revisionId), Collections.<Hash>emptyList(), 0, virtualFile, notLoaded, notLoadedUser, notLoadedUser, 0);

                    cherryPick(gitRepository, gitCommit, project);
                } finally {
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        public void run() {
                            VirtualFileManager.getInstance().syncRefresh();
                            ChangeListManagerEx.getInstanceEx(project).unblockModalNotifications();
                        }
                    });
                }
            }
        }.queue();
    }

    /**
     * A lot of this code is based on: git4idea.cherrypick.GitCherryPicker#cherryPick() (which is private)
     */
    private boolean cherryPick(@NotNull GitRepository repository, @NotNull VcsShortCommitDetails commit,
                               @NotNull Project project) {
        GitSimpleEventDetector conflictDetector = new GitSimpleEventDetector(CHERRY_PICK_CONFLICT);
        GitSimpleEventDetector localChangesOverwrittenDetector = new GitSimpleEventDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK);
        GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
                new GitUntrackedFilesOverwrittenByOperationDetector(repository.getRoot());
        GitCommandResult result = Git.getInstance().cherryPick(repository, commit.getId().asString(), false, true,
                conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector);
        if (result.success()) {
            return true;
        } else if (conflictDetector.hasHappened()) {
            return new CherryPickConflictResolver(project, repository.getRoot(),
                    commit.getId().toShortString(), commit.getAuthor().getName(),
                    commit.getSubject()).merge();
        } else if (untrackedFilesDetector.wasMessageDetected()) {
            String description = "Some untracked working tree files would be overwritten by cherry-pick.<br/>" +
                    "Please move, remove or add them before you can cherry-pick. <a href='view'>View them</a>";

            GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, repository.getRoot(),
                untrackedFilesDetector.getRelativeFilePaths(),
                "cherry-pick", description);
            return false;
        } else if (localChangesOverwrittenDetector.hasHappened()) {
            NotificationService.getInstance().notifyError(new NotificationBuilder(project, "Cherry-Pick Error",
                    "Your local changes would be overwritten by cherry-pick.<br/>Commit your changes or stash them to proceed."));
            return false;
        } else {
            NotificationService.getInstance().notifyError(new NotificationBuilder(project, "Cherry-Pick Error",
                    result.getErrorOutputAsHtmlString()));
            return false;
        }
    }


    /**
     * Copy of: git4idea.cherrypick.GitCherryPicker.CherryPickConflictResolver (which is private)
     */
    private static class CherryPickConflictResolver extends GitConflictResolver {

        public CherryPickConflictResolver(@NotNull Project project, @NotNull VirtualFile root,
                                          @NotNull String commitHash, @NotNull String commitAuthor, @NotNull String commitMessage) {
            super(project, Collections.singleton(root), makeParams(commitHash, commitAuthor, commitMessage));
        }

        private static Params makeParams(String commitHash, String commitAuthor, String commitMessage) {
            Params params = new Params();
            params.setErrorNotificationTitle("Cherry-picked with conflicts");
            params.setMergeDialogCustomizer(new CherryPickMergeDialogCustomizer(commitHash, commitAuthor, commitMessage));
            return params;
        }

        @Override
        protected void notifyUnresolvedRemain() {
            // we show a [possibly] compound notification after cherry-picking all commits.
        }
    }


    /**
     * Copy of: git4idea.cherrypick.GitCherryPicker.CherryPickMergeDialogCustomizer (which is private)
     */
    private static class CherryPickMergeDialogCustomizer extends MergeDialogCustomizer {

        private String myCommitHash;
        private String myCommitAuthor;
        private String myCommitMessage;

        public CherryPickMergeDialogCustomizer(String commitHash, String commitAuthor, String commitMessage) {
            myCommitHash = commitHash;
            myCommitAuthor = commitAuthor;
            myCommitMessage = commitMessage;
        }

        @Override
        public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
            return "<html>Conflicts during cherry-picking commit <code>" + myCommitHash + "</code> made by " + myCommitAuthor + "<br/>" +
                    "<code>\"" + myCommitMessage + "\"</code></html>";
        }

        @Override
        public String getLeftPanelTitle(VirtualFile file) {
            return "Local changes";
        }

        @Override
        public String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
            return "<html>Changes from cherry-pick <code>" + myCommitHash + "</code>";
        }
    }

    public boolean checkIfCommitIsFetched(GitRepository repository, String commitHash) {
        FormattedGitLineHandlerListener listener = new FormattedGitLineHandlerListener();
        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.SHOW);
        h.setSilent(false);
        h.setStdoutSuppressed(false);
        h.addParameters(commitHash);
        h.addParameters("--format=short");
        h.endOptions();
        h.addLineListener(listener);
        GitCommandResult gitCommandResult = Git.getInstance().runCommand(new Computable<GitLineHandler>() {
            @Override
            public GitLineHandler compute() {
                return h;
            }
        });
        boolean success = gitCommandResult.success();
        List<String> output = gitCommandResult.getOutput();
        boolean isCommit = !output.isEmpty() && output.get(0).startsWith("commit");
        return success && isCommit;
    }

    public Optional<GitCommit> loadCommit(Project project, GitRepository repository, String commitHash) throws VcsException {
        return GitHistoryUtils.history(project, repository.getRoot(), commitHash, "--max-count=1").stream().findFirst();
    }

    @NotNull
    private Pair<List<GitCommit>, List<GitCommit>> loadCommitsToCompare(@NotNull GitRepository repository, @NotNull final String branchName, @NotNull final Project project) {
        final List<GitCommit> headToBranch;
        final List<GitCommit> branchToHead;
        try {
            headToBranch = GitHistoryUtils.history(project, repository.getRoot(), ".." + branchName);
            branchToHead = GitHistoryUtils.history(project, repository.getRoot(), branchName + "..");
        } catch (VcsException e) {
            // we treat it as critical and report an error
            throw new RuntimeException("Couldn't get [git log .." + branchName + "] on repository [" + repository.getRoot() + "]", e);
        }
        return Pair.create(headToBranch, branchToHead);
    }

    @NotNull
    public CommitCompareInfo loadCommitsToCompare(Collection<GitRepository> repositories, String branchName, @NotNull final Project project) {
        CommitCompareInfo compareInfo = new CommitCompareInfo();
        for (GitRepository repository : repositories) {
            Pair<List<GitCommit>, List<GitCommit>> listListPair = loadCommitsToCompare(repository, branchName, project);
            compareInfo.put(repository, listListPair.first, listListPair.second);
        }
        return compareInfo;
    }

    public void setUpstreamBranch(GitRepository repository, String remoteBranch) throws VcsException {
        FormattedGitLineHandlerListener listener = new FormattedGitLineHandlerListener();
        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
        h.setSilent(false);
        h.setStdoutSuppressed(false);
        h.addParameters("-u", "remotes/" + remoteBranch);
        h.endOptions();
        h.addLineListener(listener);
        GitCommandResult gitCommandResult = Git.getInstance().runCommand(new Computable<GitLineHandler>() {
            @Override
            public GitLineHandler compute() {
                return h;
            }
        });
        if (!gitCommandResult.success()) {
            throw new VcsException(listener.getHtmlMessage());
        }
    }

    /**
     * @return the newest commit HEAD shares with {@code ref}, or {@code null} if they share none
     */
    @Nullable
    public String getMergeBase(GitRepository repository, String ref) throws VcsException {
        GitRevisionNumber mergeBase = GitHistoryUtils.getMergeBase(repository.getProject(), repository.getRoot(), "HEAD", ref);
        return mergeBase != null ? mergeBase.asString() : null;
    }

    /**
     * @param path relative to the repository root
     */
    public boolean existsInRevision(GitRepository repository, String revision, String path) throws VcsException {
        GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.LS_TREE);
        h.setSilent(true);
        h.addParameters("--name-only", revision);
        h.endOptions();
        h.addParameters(path);
        return !Git.getInstance().runCommand(h).getOutputOrThrow().isEmpty();
    }

    /**
     * @param path relative to the repository root
     * @return whether the file on disk is not what {@code revision} has, committed or not
     */
    public boolean differsFromRevision(GitRepository repository, String revision, String path) throws VcsException {
        GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.DIFF);
        h.setSilent(true);
        h.addParameters("--name-only", revision);
        h.endOptions();
        h.addParameters(path);
        return !Git.getInstance().runCommand(h).getOutputOrThrow().isEmpty();
    }

    private static class FormattedGitLineHandlerListener implements GitLineHandlerListener {

        private List<String> messages = new ArrayList<String>();

        @Override
        public void onLineAvailable(String s, Key key) {
            if ( s.startsWith("\t") ) {
                s = "<b>" + s.substring(1) + "</b>";
            }
            messages.add(s);
        }

        @Override
        public void processTerminated(int i) {

        }

        @Override
        public void startFailed(Throwable throwable) {

        }

        public String getHtmlMessage() {
            return StringUtil.join(messages, "<br/>");
        }
    }
}
