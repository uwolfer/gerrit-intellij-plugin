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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.common.base.Optional;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.util.GitCommitCompareInfo;

import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * @author Urs Wolfer
 */
public class CompareBranchAction extends AbstractChangeAction {

    public CompareBranchAction() {
        super("Compare with Branch", "Compare change with current branch", AllIcons.Actions.DiffWithCurrent);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        Callable<Void> successCallable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
                Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
                if (!selectedChange.isPresent()) {
                    return null;
                }
                diffChange(project, selectedChange.get());
                return null;
            }
        };
        new FetchAction(successCallable).actionPerformed(anActionEvent);
    }

    private void diffChange(Project project, ChangeInfo changeInfo) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        final Collection<GitRepository> repositoriesFromRoots = repositoryManager.getRepositories();

        final GitRepository gitRepository = GerritGitUtil.getRepositoryForGerritProject(project, changeInfo.getProject());

        final String branchName = "FETCH_HEAD";
        GitLocalBranch currentBranch = gitRepository.getCurrentBranch();
        final String currentBranchName;
        if (currentBranch != null) {
            currentBranchName = currentBranch.getFullName();
        } else {
            currentBranchName = gitRepository.getCurrentRevision();
        }
        assert currentBranch != null : "Current branch is neither a named branch nor a revision";

        final GitCommitCompareInfo compareInfo = GerritGitUtil.loadCommitsToCompare(repositoriesFromRoots, branchName, project);
        new GitCompareBranchesDialog(project, branchName, currentBranchName, compareInfo, gitRepository).show();
    }
}
