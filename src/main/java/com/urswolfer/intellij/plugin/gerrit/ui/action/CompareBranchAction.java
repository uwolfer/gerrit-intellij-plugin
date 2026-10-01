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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.dvcs.ui.CompareBranchesDialog;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.impl.HashImpl;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitCompareBranchesHelper;

import java.util.Collections;
import java.util.Optional;

/**
 * @author Urs Wolfer
 */
public class CompareBranchAction extends AbstractChangeAction {
    private final GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
    private final FetchAction fetchAction = new FetchAction();

    public CompareBranchAction() {
        super("Compare with Branch", "Compare change with current branch", AllIcons.Actions.Diff);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        final Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        final Project project = anActionEvent.getProject();
        fetchAction.fetchChange(selectedChange.get(), project,
            (gitRepository, commitHash) -> diffChange(project, gitRepository, commitHash));
    }

    private void diffChange(final Project project, final GitRepository gitRepository, String commitHash) {
        GitLocalBranch currentBranch = gitRepository.getCurrentBranch();
        final String currentBranchName;
        if (currentBranch != null) {
            currentBranchName = currentBranch.getFullName();
        } else {
            currentBranchName = gitRepository.getCurrentRevision();
        }
        assert currentBranchName != null : "Current branch is neither a named branch nor a revision";

        CommitCompareInfo compareInfo = gerritGitUtil.loadCommitsToCompare(
            Collections.singletonList(gitRepository), commitHash, project);
        // the dialog only displays this name
        String changeName = HashImpl.build(commitHash).toShortString();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                new CompareBranchesDialog(new GitCompareBranchesHelper(project), changeName, currentBranchName, compareInfo, gitRepository, false).show();
            }
        });
    }
}
