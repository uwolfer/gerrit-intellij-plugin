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
import com.google.inject.Inject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.reset.GitNewResetDialog;
import git4idea.reset.GitResetMode;
import git4idea.reset.GitResetOperation;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author JJ Brown
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class ResetAction extends AbstractChangeAction {
    @Inject
    private GerritGitUtil gerritGitUtil;
    @Inject
    private FetchAction fetchAction;
    @Inject
    private NotificationService notificationService;

    public ResetAction() {
        super("Reset", "Reset branch to here", AllIcons.Actions.Reset);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
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
                        try {
                            Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.
                                getRepositoryForGerritProject(project, changeDetails.project);
                            if (!gitRepositoryOptional.isPresent()) {
                                NotificationBuilder notification = new NotificationBuilder(project, "Error",
                                    String.format("No repository found for Gerrit project: '%s'.", changeDetails.project));
                                notificationService.notifyError(notification);
                                return null;
                            }
                            final GitRepository repository = gitRepositoryOptional.get();

                            FetchInfo firstFetchInfo = gerritUtil.getFirstFetchInfo(changeDetails);
                            final Optional<GitRemote> remote = gerritGitUtil.getRemoteForChange(project, repository, firstFetchInfo);
                            if (!remote.isPresent()) {
                                return null;
                            }

                            final Optional<VcsFullCommitDetails> gitCommitOptional = gerritGitUtil.loadGitCommitInfo(project, repository, selectedChange.get());
                            if (!gitCommitOptional.isPresent()) {
                                // Since we JUST fetched it, it should definitely exist locally,
                                // so this error case should never happen unless something got silently corrupted.
                                NotificationBuilder notification = new NotificationBuilder(project, "Error",
                                    String.format("Could not load commit '%s' from local repository.", changeDetails.id));
                                notificationService.notifyError(notification);
                                return null;
                            }

                            // emulating the work of the GitResetAction class
                            final Map<GitRepository, VcsFullCommitDetails> commits = Collections.singletonMap(repository, gitCommitOptional.get());
                            final GitVcsSettings settings = GitVcsSettings.getInstance(project);
                            final GitResetMode defaultMode = ObjectUtils.notNull(settings.getResetMode(), GitResetMode.getDefault());
                            askUserForResetMode(project, commits, defaultMode, new Consumer<GitResetMode>() {
                                @Override
                                public void consume(final GitResetMode selectedMode) {
                                    settings.setResetMode(selectedMode);
                                    (new Task.Backgroundable(project, "Git reset", false) {
                                        public void run(@NotNull ProgressIndicator indicator) {
                                            (new GitResetOperation(project, commits, selectedMode, indicator)).execute();
                                        }
                                    }).queue();
                                }
                            });

                            return null;
                        } catch (Exception e) {
                            NotificationBuilder notification = new NotificationBuilder(project, "Error",
                                String.format("Could not load commit '%s' from local repository: %s", changeDetails.id, e.getMessage()));
                            notificationService.notifyError(notification);
                            return null;
                        }
                    }
                };
                fetchAction.fetchChange(selectedChange.get(), project, fetchCallback);
            }
        });
    }

    private static void askUserForResetMode(Project project, Map<GitRepository, VcsFullCommitDetails> commits, GitResetMode defaultResetMode, Consumer<GitResetMode> onModeSelected) {
        // I have to extend their dialog to re-use it, since that dialog's constructor has protected access.
        // If this ever breaks, we'll just have to re-create something similar.
        class GerritResetDialog extends GitNewResetDialog {
            GerritResetDialog(Project project, Map<GitRepository, VcsFullCommitDetails> commits, GitResetMode defaultGitResetMode) {
                super(project, commits, defaultGitResetMode);
            }
        }
        GitNewResetDialog dialog = new GerritResetDialog(project, commits, defaultResetMode);
        dialog.show();
        if (dialog.isOK()) {
            onModeSelected.consume(dialog.getResetMode());
        }
    }

    public static class Proxy extends ResetAction {
        private final ResetAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(ResetAction.class);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
