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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
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
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
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
    private static final Logger LOG = Logger.getInstance(GerritGitUtil.class);

    public static GitRepository getRepositoryForGerritProject(Project project, String gerritProjectName) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        final Collection<GitRepository> repositoriesFromRoots = repositoryManager.getRepositories();

        for (GitRepository repository : repositoriesFromRoots) {
            for (GitRemote remote : repository.getRemotes()) {
                for (String remoteUrl : remote.getUrls()) {
                    remoteUrl = remoteUrl.replace(".git", ""); // some repositories end their name with ".git"
                    if (remoteUrl.endsWith(gerritProjectName)) {
                        return repository;
                    }
                }
            }
        }
        throw new RuntimeException(String.format("No repository found for Gerrit project: '%s'.", gerritProjectName));
    }

    public static void fetchChange(final Project project, final GitRepository gitRepository, final String branch, @Nullable final Callable<Void> successCallable) {
        GitVcs.runInBackground(new Task.Backgroundable(project, "Fetching...", false) {
            @Override
            public void onSuccess() {
                super.onSuccess();
                try {
                    if (successCallable != null) {
                        successCallable.call();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                final VirtualFile virtualFile = gitRepository.getGitDir();
                final GitRemote gitRemote = Iterables.get(gitRepository.getRemotes(), 0);
                final String url = Iterables.get(gitRepository.getRemotes(), 0).getFirstUrl();
                GerritGitUtil.fetchNatively(virtualFile, gitRemote, url, branch, project, indicator);
            }
        });
    }

    public static void cherryPickChange(final Project project, final ChangeInfo changeInfo) {
        final Git git = ServiceManager.getService(Git.class);
        final GitPlatformFacade platformFacade = ServiceManager.getService(GitPlatformFacade.class);

        FileDocumentManager.getInstance().saveAllDocuments();
        platformFacade.getChangeListManager(project).blockModalNotifications();

        new Task.Backgroundable(project, "Cherry-picking...", false) {
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    final GitRepository gitRepository = getRepositoryForGerritProject(project, changeInfo.getProject());

                    final VirtualFile virtualFile = gitRepository.getGitDir();

                    final String notLoaded = "Not loaded";
                    String ref = changeInfo.getCurrentRevision();
                    GitHeavyCommit gitCommit = new GitHeavyCommit(virtualFile, AbstractHash.create(ref), new SHAHash(ref), notLoaded, notLoaded, new Date(0), notLoaded,
                            notLoaded, Collections.<String>emptySet(), Collections.<FilePath>emptyList(), notLoaded,
                            notLoaded, Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.<Change>emptyList(), 0);

                    cherryPick(gitRepository, gitCommit, git, platformFacade, project);
                } finally {
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        public void run() {
                            VirtualFileManager.getInstance().syncRefresh();

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
    private static boolean cherryPick(@NotNull GitRepository repository, @NotNull GitHeavyCommit commit,
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

            UntrackedFilesNotifier.notifyUntrackedFilesOverwrittenBy(project, platformFacade, untrackedFilesDetector.getFiles(),
                    "cherry-pick", description);
            return false;
        } else if (localChangesOverwrittenDetector.hasHappened()) {
            GerritUtil.notifyError(project, "Cherry-Pick Error",
                    "Your local changes would be overwritten by cherry-pick.<br/>Commit your changes or stash them to proceed.");
            return false;
        } else {
            GerritUtil.notifyError(project, "Cherry-Pick Error", result.getErrorOutputAsHtmlString());
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
    public static GitFetchResult fetchNatively(@NotNull VirtualFile root,
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
                LOG.info("Cancelled fetch.");
                result.set(GitFetchResult.cancel());
            }

            @Override
            protected void onFailure() {
                LOG.info("Error fetching: " + h.errors());
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
    public static Pair<List<GitCommit>, List<GitCommit>> loadCommitsToCompare(@NotNull GitRepository repository, @NotNull final String branchName, @NotNull final Project project) {
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
    public static GitCommitCompareInfo loadCommitsToCompare(Collection<GitRepository> repositories, String branchName, @NotNull final Project project) {
        GitCommitCompareInfo compareInfo = new GitCommitCompareInfo();
        for (GitRepository repository : repositories) {
            compareInfo.put(repository, loadCommitsToCompare(repository, branchName, project));
//            compareInfo.put(repository, loadTotalDiff(repository, branchName));
        }
        return compareInfo;
    }
}
