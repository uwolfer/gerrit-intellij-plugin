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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.Hash;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitLocalBranch;
import git4idea.GitVcs;
import git4idea.branch.GitBrancher;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.validators.GitNewBranchNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Urs Wolfer
 */
public class CheckoutAction extends AbstractChangeAction {
    private final GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
    private final FetchAction fetchAction = new FetchAction();
    private final NotificationService notificationService = NotificationService.getInstance();

    public CheckoutAction() {
        super(AllIcons.Actions.CheckOut);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        final Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        final Project project = anActionEvent.getProject();
        if (project == null) {
            return;
        }

        getChangeDetail(selectedChange.get(), project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(final ChangeInfo changeDetails) {
                FetchAction.FetchCallback fetchCallback = new FetchAction.FetchCallback() {
                    @Override
                    public void fetched(final GitRepository repository, final String commitHash) {
                        final GitBrancher brancher = project.getService(GitBrancher.class);
                        final List<GitRepository> gitRepositories = Collections.singletonList(repository);
                        FetchInfo firstFetchInfo = gerritUtil.getFirstFetchInfo(project, changeDetails);
                        final Optional<GitRemote> remote = gerritGitUtil.getRemoteForChange(project, repository, firstFetchInfo);
                        if (!remote.isPresent()) {
                            return;
                        }
                        // FetchAction loads the change again, so a patch set uploaded in between is unknown here
                        RevisionInfo revisionInfo = changeDetails.revisions.get(commitHash);
                        if (revisionInfo == null) {
                            notificationService.notifyError(new NotificationBuilder(project, "Checkout Error",
                                    "Change " + changeDetails._number + " got a new patch set. Refresh and try again."));
                            return;
                        }
                        String branchName = ReviewBranchName.build(changeDetails, revisionInfo._number);
                        GitNewBranchNameValidator newBranchNameValidator = GitNewBranchNameValidator.newInstance(gitRepositories);
                        final ReviewBranchName.Target target = ReviewBranchName.resolve(branchName, commitHash,
                                name -> headOf(repository, name), newBranchNameValidator::checkInput);
                        if (target == null) {
                            String blocking = ReviewBranchName.blockingBranch(branchName, name -> headOf(repository, name) != null);
                            String message = blocking == null
                                    ? "Could not find a free branch name for " + branchName + "."
                                    : "Branch " + blocking + " prevents creating " + branchName + ". Rename or delete it to check out this patch set.";
                            notificationService.notifyError(new NotificationBuilder(project, "Checkout Error", message));
                            return;
                        }
                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                Runnable setUpstream = new Runnable() {
                                    @Override
                                    public void run() {
                                        // GitBrancher runs this after a failed or declined checkout as well
                                        if (!target.name.equals(repository.getCurrentBranchName())) {
                                            return;
                                        }
                                        GitVcs.runInBackground(new Task.Backgroundable(project, "Setting upstream branch...", false) {
                                            @Override
                                            public void run(@NotNull ProgressIndicator indicator) {
                                                try {
                                                    gerritGitUtil.setUpstreamBranch(repository, target.name,
                                                            remote.get().getName() + "/" + changeDetails.branch);
                                                } catch (VcsException e) {
                                                    NotificationBuilder builder = new NotificationBuilder(project, "Checkout Error", e.getMessage());
                                                    notificationService.notifyError(builder);
                                                }
                                            }
                                        });
                                    }
                                };
                                if (target.exists) {
                                    brancher.checkout(target.name, false, gitRepositories, setUpstream);
                                } else {
                                    brancher.checkoutNewBranchStartingFrom(target.name, commitHash, gitRepositories, setUpstream);
                                }
                            }
                        }
                        );
                    }
                };
                fetchAction.fetchChange(selectedChange.get(), project, fetchCallback);
            }
        });
    }

    @Nullable
    private static String headOf(GitRepository repository, String branchName) {
        GitLocalBranch branch = repository.getBranches().findLocalBranch(branchName);
        if (branch == null) {
            return null;
        }
        Hash hash = repository.getBranches().getHash(branch);
        return hash == null ? "" : hash.asString();
    }
}
