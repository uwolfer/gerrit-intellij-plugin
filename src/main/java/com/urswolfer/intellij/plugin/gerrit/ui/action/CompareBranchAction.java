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
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.inject.Inject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.util.GitCommitCompareInfo;

import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class CompareBranchAction extends AbstractChangeAction {
    @Inject
    private GerritGitUtil gerritGitUtil;
    @Inject
    private FetchAction fetchAction;
    @Inject
    private NotificationService notificationService;

    public CompareBranchAction() {
        super("Compare with Branch", "Compare change with current branch", AllIcons.Actions.Diff);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        final Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
        Callable<Void> successCallable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                diffChange(project, selectedChange.get());
                return null;
            }
        };
        fetchAction.fetchChange(selectedChange.get(), project, successCallable);
    }

    private void diffChange(final Project project, ChangeInfo changeInfo) {
        Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.getRepositoryForGerritProject(project, changeInfo.project);
        if (!gitRepositoryOptional.isPresent()) {
            NotificationBuilder notification = new NotificationBuilder(project, "Error",
                String.format("No repository found for Gerrit project: '%s'.", changeInfo.project));
            notificationService.notifyError(notification);
            return;
        }
        final GitRepository gitRepository = gitRepositoryOptional.get();

        final String branchName = "FETCH_HEAD";
        GitLocalBranch currentBranch = gitRepository.getCurrentBranch();
        final String currentBranchName;
        if (currentBranch != null) {
            currentBranchName = currentBranch.getFullName();
        } else {
            currentBranchName = gitRepository.getCurrentRevision();
        }
        assert currentBranchName != null : "Current branch is neither a named branch nor a revision";

        final GitCommitCompareInfo compareInfo = gerritGitUtil.loadCommitsToCompare(
            Collections.singletonList(gitRepository), branchName, project);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                new GitCompareBranchesDialog(project, branchName, currentBranchName, compareInfo, gitRepository).show();
            }
        });
    }

    public static class Proxy extends CompareBranchAction {
        private final CompareBranchAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(CompareBranchAction.class);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }

}
