/*
 * Copyright 2013-2014 Urs Wolfer
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
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.inject.Inject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import git4idea.branch.GitBrancher;
import git4idea.repo.GitRepository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class CheckoutAction extends AbstractChangeAction {
    @Inject
    private GerritGitUtil gerritGitUtil;
    @Inject
    private FetchAction fetchAction;
    @Inject
    private SelectedRevisions selectedRevisions;

    public CheckoutAction() {
        super("Checkout", "Checkout change", AllIcons.Actions.CheckOut);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        final Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        final Project project = anActionEvent.getRequiredData(PlatformDataKeys.PROJECT);

        getChangeDetail(selectedChange.get(), project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(final ChangeInfo changeDetails) {
                Callable<Void> successCallable = new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        GitBrancher brancher = ServiceManager.getService(project, GitBrancher.class);
                        Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.
                                getRepositoryForGerritProject(project, changeDetails.project);
                        String branchName = buildBranchName(changeDetails);
                        List<GitRepository> gitRepositories = Collections.singletonList(gitRepositoryOptional.get());
                        gerritGitUtil.deleteBranchIfExists(gitRepositoryOptional.get(), branchName);
                        brancher.checkoutNewBranchStartingFrom(branchName, "FETCH_HEAD", gitRepositories, null);
                        return null;
                    }
                };
                fetchAction.fetchChange(selectedChange.get(), project, successCallable);
            }
        });
    }

    private String buildBranchName(ChangeInfo changeDetails) {
        RevisionInfo revisionInfo = changeDetails.revisions.get(selectedRevisions.get(changeDetails));
        String branchName = "review/" + changeDetails.owner.name.toLowerCase().replaceAll(" ","_") + "/" + changeDetails.topic;
        if ( revisionInfo._number != changeDetails.revisions.size() ) {
            branchName += "-patch" + revisionInfo._number;
        }
        return branchName;
    }

    public static class Proxy extends CheckoutAction {
        private final CheckoutAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(CheckoutAction.class);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
