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

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.inject.Inject;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsShortCommitDetailsImpl;
import com.intellij.vcs.log.impl.VcsUserImpl;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.GitCommit;
import git4idea.GitExecutionException;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerListener;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.commands.GitStandardProgressAnalyzer;
import git4idea.commands.GitTask;
import git4idea.commands.GitTaskResultHandlerAdapter;
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector;
import git4idea.history.GitHistoryUtils;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitFetchResult;
import git4idea.update.GitFetcher;
import git4idea.util.GitCommitCompareInfo;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Urs Wolfer
 */
public class GerritGitUtil {
    @Inject
    private Git git;
    @Inject
    private FileDocumentManager fileDocumentManager;
    @Inject
    private Application application;
    @Inject
    private VirtualFileManager virtualFileManager;
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private NotificationService notificationService;

    public Iterable<GitRepository> getRepositories(Project project) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        return repositoryManager.getRepositories();
    }

    public Optional<GitRepository> getRepositoryForGerritProject(Project project, String gerritProjectName) {
        final Iterable<GitRepository> repositoriesFromRoots = getRepositories(project);
        for (GitRepository repository : repositoriesFromRoots) {
            for (GitRemote remote : repository.getRemotes()) {
                if (remote.getName().equals(gerritProjectName)) {
                    return Optional.of(repository);
                }
                for (String remoteUrl : remote.getUrls()) {
                    remoteUrl = UrlUtils.stripGitExtension(remoteUrl);
                    if (remoteUrl != null && remoteUrl.endsWith(gerritProjectName)) {
                        return Optional.of(repository);
                    }
                }
            }
        }
        return Optional.absent();
    }

    public Optional<GitRemote> getRemoteForChange(Project project, GitRepository gitRepository, FetchInfo fetchInfo) {
        String url = fetchInfo.url;
        for (GitRemote remote : gitRepository.getRemotes()) {
            List<String> repositoryUrls = new ArrayList<String>();
            repositoryUrls.addAll(remote.getUrls());
            repositoryUrls.addAll(remote.getPushUrls());
            for (String repositoryUrl : repositoryUrls) {
                if (UrlUtils.urlHasSameHost(repositoryUrl, url)
                    || UrlUtils.urlHasSameHost(repositoryUrl, gerritSettings.getHost())) {
                    return Optional.of(remote);
                }
            }
        }
        NotificationBuilder notification = new NotificationBuilder(project, "Error",
            String.format("Could not fetch commit because no remote url matches Gerrit host.<br/>" +
                "Git repository: '%s'.", gitRepository.getPresentableUrl()));
        notificationService.notifyError(notification);
        return Optional.absent();
    }

    public void fetchChange(final Project project,
                            final GitRepository gitRepository,
                            final FetchInfo fetchInfo,
                            @Nullable final Callable<Void> successCallable) {
        GitVcs.runInBackground(new Task.Backgroundable(project, "Fetching...", false) {
            @Override
            public void onSuccess() {
                super.onSuccess();
                try {
                    if (successCallable != null) {
                        successCallable.call();
                    }
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Optional<GitRemote> remote = getRemoteForChange(project, gitRepository, fetchInfo);
                if (!remote.isPresent()) {
                    return;
                }
                GitFetchResult result = fetchNatively(gitRepository, remote.get(), fetchInfo.ref);
                if (!result.isSuccess()) {
                    GitFetcher.displayFetchResult(project, result, null, result.getErrors());
                }
            }
        });
    }

    public void cherryPickChange(final Project project, final ChangeInfo changeInfo, final String revisionId) {
        fileDocumentManager.saveAllDocuments();
        ChangeListManagerImpl.getInstanceImpl(project).blockModalNotifications();

        new Task.Backgroundable(project, "Cherry-picking...", false) {
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    Optional<GitRepository> gitRepositoryOptional = getRepositoryForGerritProject(project, changeInfo.project);
                    if (!gitRepositoryOptional.isPresent()) {
                        NotificationBuilder notification = new NotificationBuilder(project, "Error",
                            String.format("No repository found for Gerrit project: '%s'.", changeInfo.project));
                        notificationService.notifyError(notification);
                        return;
                    }
                    GitRepository gitRepository = gitRepositoryOptional.get();

                    final VirtualFile virtualFile = gitRepository.getRoot();

                    final String notLoaded = "Not loaded";
                    VcsUser notLoadedUser = new VcsUserImpl(notLoaded, notLoaded);
                    VcsShortCommitDetails gitCommit = new VcsShortCommitDetailsImpl(
                        HashImpl.build(revisionId), Collections.<Hash>emptyList(), 0, virtualFile, notLoaded, notLoadedUser, notLoadedUser, 0);

                    cherryPick(gitRepository, gitCommit, git, project);
                } finally {
                    application.invokeLater(new Runnable() {
                        public void run() {
                            virtualFileManager.syncRefresh();
                            ChangeListManagerImpl.getInstanceImpl(project).unblockModalNotifications();
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
                               @NotNull Git git, @NotNull Project project) {
        GitSimpleEventDetector conflictDetector = new GitSimpleEventDetector(CHERRY_PICK_CONFLICT);
        GitSimpleEventDetector localChangesOverwrittenDetector = new GitSimpleEventDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK);
        GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
                new GitUntrackedFilesOverwrittenByOperationDetector(repository.getRoot());
        GitCommandResult result = git.cherryPick(repository, commit.getId().asString(), false,
                conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector);
        if (result.success()) {
            return true;
        } else if (conflictDetector.hasHappened()) {
            return new CherryPickConflictResolver(project, git, repository.getRoot(),
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
            notificationService.notifyError(new NotificationBuilder(project, "Cherry-Pick Error",
                    "Your local changes would be overwritten by cherry-pick.<br/>Commit your changes or stash them to proceed."));
            return false;
        } else {
            notificationService.notifyError(new NotificationBuilder(project, "Cherry-Pick Error",
                    result.getErrorOutputAsHtmlString()));
            return false;
        }
    }


    /**
     * Copy of: git4idea.cherrypick.GitCherryPicker.CherryPickConflictResolver (which is private)
     */
    private static class CherryPickConflictResolver extends GitConflictResolver {

        public CherryPickConflictResolver(@NotNull Project project, @NotNull Git git, @NotNull VirtualFile root,
                                          @NotNull String commitHash, @NotNull String commitAuthor, @NotNull String commitMessage) {
            super(project, git, Collections.singleton(root), makeParams(commitHash, commitAuthor, commitMessage));
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

    /**
     * Almost a copy of git4idea.update.GitFetcher#fetchNatively().
     * Modifications:
     * * removal of GitFetchPruneDetector
     * * do not prepend "refs/heads/..." (with getFetchSpecForBranch).
     */
    @NotNull
    private static GitFetchResult fetchNatively(@NotNull GitRepository repository, @NotNull GitRemote remote, @Nullable String branch) {
        Git git = ServiceManager.getService(Git.class);
        GitCommandResult result = git.fetch(repository, remote,
            Collections.<GitLineHandlerListener>emptyList(), new String[]{branch});

        GitFetchResult fetchResult;
        if (result.success()) {
            fetchResult = GitFetchResult.success();
        } else if (result.cancelled()) {
            fetchResult = GitFetchResult.cancel();
        } else {
            fetchResult = GitFetchResult.error(result.getErrorOutputAsJoinedString());
        }
        return fetchResult;
    }

    @NotNull
    public Pair<List<GitCommit>, List<GitCommit>> loadCommitsToCompare(@NotNull GitRepository repository, @NotNull final String branchName, @NotNull final Project project) {
        final List<GitCommit> headToBranch;
        final List<GitCommit> branchToHead;
        try {
            headToBranch = GitHistoryUtils.history(project, repository.getRoot(), ".." + branchName);
            branchToHead = GitHistoryUtils.history(project, repository.getRoot(), branchName + "..");
        } catch (VcsException e) {
            // we treat it as critical and report an error
            throw new GitExecutionException("Couldn't get [git log .." + branchName + "] on repository [" + repository.getRoot() + "]", e);
        }
        return Pair.create(headToBranch, branchToHead);
    }

    @NotNull
    public GitCommitCompareInfo loadCommitsToCompare(Collection<GitRepository> repositories, String branchName, @NotNull final Project project) {
        GitCommitCompareInfo compareInfo = new GitCommitCompareInfo();
        for (GitRepository repository : repositories) {
            compareInfo.put(repository, loadCommitsToCompare(repository, branchName, project));
//            compareInfo.put(repository, loadTotalDiff(repository, branchName));
        }
        return compareInfo;
    }

    public boolean checkoutNewBranch(GitRepository repository, String branch) throws VcsException {
        FormattedGitLineHandlerListener listener = new FormattedGitLineHandlerListener();
        GitCommandResult gitCommandResult = git.checkout(repository, "FETCH_HEAD", branch, false, false, listener);
        if (gitCommandResult.success()) {
            return true;
        } else if (gitCommandResult.getErrorOutputAsJoinedString().contains("already exists")){
            return false;
        } else {
            throw new VcsException(listener.getHtmlMessage());
        }

    }

    public void setUpstreamBranch(GitRepository repository, String remoteBranch) throws VcsException {
        FormattedGitLineHandlerListener listener = new FormattedGitLineHandlerListener();
        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
        h.setSilent(false);
        h.setStdoutSuppressed(false);
        h.addParameters("-u", "remotes/" + remoteBranch);
        h.endOptions();
        h.addLineListener(listener);
        GitCommandResult gitCommandResult = git.runCommand(new Computable<GitLineHandler>() {
            @Override
            public GitLineHandler compute() {
                return h;
            }
        });
        if (!gitCommandResult.success()) {
            throw new VcsException(listener.getHtmlMessage());
        }
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
