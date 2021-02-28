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
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.inject.Inject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitVcs;
import git4idea.branch.GitBrancher;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.validators.GitNewBranchNameValidator;
import org.jetbrains.annotations.NotNull;

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
    @Inject
    private NotificationService notificationService;

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
                Callable<Void> fetchCallback = new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        final GitBrancher brancher = ServiceManager.getService(project, GitBrancher.class);
                        Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.
                                getRepositoryForGerritProject(project, changeDetails.project);
                        if (!gitRepositoryOptional.isPresent()) {
                            NotificationBuilder notification = new NotificationBuilder(project, "Error",
                                String.format("No repository found for Gerrit project: '%s'.", changeDetails.project));
                            notificationService.notifyError(notification);
                            return null;
                        }
                        String branchName = buildBranchName(changeDetails);
                        String checkedOutBranchName = branchName;
                        final GitRepository repository = gitRepositoryOptional.get();
                        final List<GitRepository> gitRepositories = Collections.singletonList(repository);
                        FetchInfo firstFetchInfo = gerritUtil.getFirstFetchInfo(changeDetails);
                        final Optional<GitRemote> remote = gerritGitUtil.getRemoteForChange(project, repository, firstFetchInfo);
                        if (!remote.isPresent()) {
                            return null;
                        }
                        boolean validName = false;
                        int i = 0;
                        GitNewBranchNameValidator newBranchNameValidator = GitNewBranchNameValidator.newInstance(gitRepositories);
                        while (!validName && i < 100) { // do not loop endless - stop after 100 tries because most probably something went wrong
                            checkedOutBranchName = branchName + (i != 0 ? "_" + i : "");
                            validName = newBranchNameValidator.checkInput(checkedOutBranchName);
                            i++;
                        }
                        final String finalCheckedOutBranchName = checkedOutBranchName;
                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                brancher.checkoutNewBranchStartingFrom(finalCheckedOutBranchName, "FETCH_HEAD", gitRepositories, new Runnable() {
                                    @Override
                                    public void run() {
                                        GitVcs.runInBackground(new Task.Backgroundable(project, "Setting upstream branch...", false) {
                                            @Override
                                            public void run(@NotNull ProgressIndicator indicator) {
                                                try {
                                                    gerritGitUtil.setUpstreamBranch(repository, remote.get().getName() + "/" + changeDetails.branch);
                                                } catch (VcsException e) {
                                                    NotificationBuilder builder = new NotificationBuilder(project, "Checkout Error", e.getMessage());
                                                    notificationService.notifyError(builder);
                                                }
                                            }
                                        });
                                    }
                                });
                            }
                        }
                        );
                        return null;
                    }
                };
                fetchAction.fetchChange(selectedChange.get(), project, fetchCallback);
            }
        });
    }

    private String buildBranchName(ChangeInfo changeDetails) {
        RevisionInfo revisionInfo = changeDetails.revisions.get(selectedRevisions.get(changeDetails));
        String topic = changeDetails.topic;
        if (topic == null) {
            topic = Integer.toString(changeDetails._number);
        }
        String branchName = "review/" + changeDetails.owner.name.toLowerCase() + '/' + topic;
        if (revisionInfo._number != changeDetails.revisions.size()) {
            branchName += "-patch" + revisionInfo._number;
        }
        return branchName.replace(" ", "_").replace("?", "_");
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
