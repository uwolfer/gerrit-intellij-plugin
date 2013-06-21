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

import java.util.Collection;

import com.google.common.collect.Iterables;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.TableView;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.util.GitCommitCompareInfo;

/**
 * @author Urs Wolfer
 */
public class CompareAction extends AnAction implements DumbAware {

    private final TableView myTable;
    private final FetchAction fetchAction;

    public CompareAction(TableView table) {
        super("Compare", "Compare change", AllIcons.Actions.DiffWithCurrent);
        this.myTable = table;

        fetchAction = new FetchAction(table); // fetch is required before you can compare
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        fetchAction.actionPerformed(anActionEvent);

        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
        diffChange(project);
    }

    private void diffChange(Project project) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        final Collection<GitRepository> repositoriesFromRoots = repositoryManager.getRepositories();

        final GitRepository gitRepository = Iterables.get(repositoriesFromRoots, 0);

        final String branchName = "FETCH_HEAD";
        final String currentBranch = gitRepository.getCurrentBranch().getFullName();

        final GitCommitCompareInfo compareInfo = GerritGitUtil.loadCommitsToCompare(repositoriesFromRoots, branchName, project);
        new GitCompareBranchesDialog(project, branchName, currentBranch, compareInfo, gitRepository).show();
    }
}
