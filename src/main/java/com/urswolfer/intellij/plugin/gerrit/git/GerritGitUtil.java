/*
 * Copyright 2013 Urs Wolfer
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

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.inject.Inject;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.*;
import git4idea.commands.*;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitHeavyCommit;
import git4idea.history.browser.SHAHash;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitFetchResult;
import git4idea.util.GitCommitCompareInfo;
import git4idea.util.UntrackedFilesNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT;
import static git4idea.commands.GitSimpleEventDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK;

/**
 * @author Urs Wolfer
 */
public class GerritGitUtil {
    @Inject
    private Logger log;
    @Inject
    private Git git;
    @Inject
    private GitPlatformFacade platformFacade;
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
                    if (remoteUrl.endsWith(gerritProjectName)) {
                        return Optional.of(repository);
                    }
                }
            }
        }
        NotificationBuilder notification = new NotificationBuilder(project, "Error",
                String.format("No repository found for Gerrit project: '%s'.", gerritProjectName));
        notificationService.notifyError(notification);
        return Optional.absent();
    }

    public void fetchChange(final Project project,
                            final GitRepository gitRepository,
                            final String url,
                            final String branch,
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
                for (GitRemote remote : gitRepository.getRemotes()) {
                    for (String repositoryUrl : remote.getUrls()) {
                        if (UrlUtils.urlHasSameHost(repositoryUrl, url)
                                || UrlUtils.urlHasSameHost(repositoryUrl, gerritSettings.getHost())) {
                            fetchNatively(gitRepository.getGitDir(), remote, repositoryUrl, branch, project, indicator);
                            return;
                        }
                    }
                }
                NotificationBuilder notification = new NotificationBuilder(project, "Error",
                        String.format("Could not fetch commit because no remote url matches Gerrit host.<br/>" +
                                "Git repository: '%s'.", gitRepository.getPresentableUrl()));
                notificationService.notifyError(notification);
            }
        });
    }

    public void cherryPickChange(final Project project, final ChangeInfo changeInfo, final String revisionId) {
        fileDocumentManager.saveAllDocuments();
        platformFacade.getChangeListManager(project).blockModalNotifications();

        new Task.Backgroundable(project, "Cherry-picking...", false) {
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    Optional<GitRepository> gitRepositoryOptional = getRepositoryForGerritProject(project, changeInfo.project);
                    if (!gitRepositoryOptional.isPresent()) return;
                    GitRepository gitRepository = gitRepositoryOptional.get();

                    final VirtualFile virtualFile = gitRepository.getGitDir();

                    final String notLoaded = "Not loaded";
                    String ref = changeInfo.currentRevision;
                    GitHeavyCommit gitCommit = new GitHeavyCommit(virtualFile, AbstractHash.create(revisionId), new SHAHash(revisionId), notLoaded, notLoaded, new Date(0), notLoaded,
                            notLoaded, Collections.<String>emptySet(), Collections.<FilePath>emptyList(), notLoaded,
                            notLoaded, Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.<Change>emptyList(), 0);

                    cherryPick(gitRepository, gitCommit, git, platformFacade, project);
                } finally {
                    application.invokeLater(new Runnable() {
                        public void run() {
                            virtualFileManager.syncRefresh();
                            platformFacade.getChangeListManager(project).unblockModalNotifications();
                        }
                    });
                }
            }
        }.queue();
    }

    /**
     * A lot of this code is based on: git4idea.cherrypick.GitCherryPicker#cherryPick() (which is private)
     */
    private boolean cherryPick(@NotNull GitRepository repository, @NotNull GitHeavyCommit commit,
                               @NotNull Git git, @NotNull GitPlatformFacade platformFacade, @NotNull Project project) {
        GitSimpleEventDetector conflictDetector = new GitSimpleEventDetector(CHERRY_PICK_CONFLICT);
        GitSimpleEventDetector localChangesOverwrittenDetector = new GitSimpleEventDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK);
        GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
                new GitUntrackedFilesOverwrittenByOperationDetector(repository.getRoot());
        GitCommandResult result = git.cherryPick(repository, commit.getHash().getValue(), false,
                conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector);
        if (result.success()) {
            return true;
        } else if (conflictDetector.hasHappened()) {
            return new CherryPickConflictResolver(project, git, platformFacade, repository.getRoot(),
                    commit.getShortHash().getString(), commit.getAuthor(),
                    commit.getSubject()).merge();
        } else if (untrackedFilesDetector.wasMessageDetected()) {
            String description = "Some untracked working tree files would be overwritten by cherry-pick.<br/>" +
                    "Please move, remove or add them before you can cherry-pick. <a href='view'>View them</a>";

            UntrackedFilesNotifier.notifyUntrackedFilesOverwrittenBy(project, repository.getRoot(),
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

        public CherryPickConflictResolver(@NotNull Project project, @NotNull Git git, @NotNull GitPlatformFacade facade, @NotNull VirtualFile root,
                                          @NotNull String commitHash, @NotNull String commitAuthor, @NotNull String commitMessage) {
            super(project, git, facade, Collections.singleton(root), makeParams(commitHash, commitAuthor, commitMessage));
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

    @NotNull
    public GitFetchResult fetchNatively(@NotNull VirtualFile root,
                                        @NotNull GitRemote remote,
                                        @NotNull String url,
                                        @Nullable String branch,
                                        Project project,
                                        ProgressIndicator progressIndicator) {
        final GitLineHandlerPasswordRequestAware h = new GitLineHandlerPasswordRequestAware(project, root, GitCommand.FETCH);
        h.setUrl(url);
        h.addProgressParameter();

        String remoteName = remote.getName();
        h.addParameters(remoteName);
        if (branch != null) {
            h.addParameters(branch);
        }

        final GitTask fetchTask = new GitTask(project, h, "Fetching " + remote.getFirstUrl());
        fetchTask.setProgressIndicator(progressIndicator);
        fetchTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());

        final AtomicReference<GitFetchResult> result = new AtomicReference<GitFetchResult>();
        fetchTask.execute(true, false, new GitTaskResultHandlerAdapter() {
            @Override
            protected void onSuccess() {
                result.set(GitFetchResult.success());
            }

            @Override
            protected void onCancel() {
                log.info("Cancelled fetch.");
                result.set(GitFetchResult.cancel());
            }

            @Override
            protected void onFailure() {
                log.warn("Error fetching: " + h.errors());
                Collection<Exception> errors = Lists.newArrayList();
                if (!h.hadAuthRequest()) {
                    errors.addAll(h.errors());
                } else {
                    errors.add(new VcsException("Authentication failed"));
                }
                result.set(GitFetchResult.error(errors));
            }
        });

        return result.get();
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
}
