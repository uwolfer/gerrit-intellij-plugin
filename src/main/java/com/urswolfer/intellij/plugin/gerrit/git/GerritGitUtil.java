/*
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

package com.urswolfer.intellij.plugin.gerrit.git;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.collect.Iterables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitExecutionException;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandlerPasswordRequestAware;
import git4idea.commands.GitStandardProgressAnalyzer;
import git4idea.commands.GitTask;
import git4idea.commands.GitTaskResultHandlerAdapter;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitFetchResult;
import git4idea.util.GitCommitCompareInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Urs Wolfer
 */
public class GerritGitUtil {
    private static final Logger LOG = Logger.getInstance(GerritGitUtil.class);

    public static void fetchChange(final Project project, final String branch) {
        GitVcs.runInBackground(new Task.Backgroundable(project, "Fetching...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
                final Collection<GitRepository> repositoriesFromRoots = repositoryManager.getRepositories();

                final GitRepository gitRepository = Iterables.get(repositoriesFromRoots, 0);
                final VirtualFile virtualFile = gitRepository.getGitDir();
                final GitRemote gitRemote = Iterables.get(gitRepository.getRemotes(), 0);
                final String url = Iterables.get(gitRepository.getRemotes(), 0).getFirstUrl();
                GerritGitUtil.fetchNatively(virtualFile, gitRemote, url, branch, project, indicator);
            }
        });
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
                Collection<Exception> errors = Collections.emptyList();
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
        }
        catch (VcsException e) {
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
