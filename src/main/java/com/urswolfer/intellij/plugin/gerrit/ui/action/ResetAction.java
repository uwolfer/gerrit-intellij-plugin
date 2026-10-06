/*
 * Copyright 2026 Urs Wolfer
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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitCommit;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.reset.GitNewResetDialog;
import git4idea.reset.GitResetMode;
import git4idea.reset.GitResetOperation;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * @author JJ Brown
 */
public class ResetAction extends AbstractChangeAction {
    private final GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
    private final FetchAction fetchAction = new FetchAction();
    private final NotificationService notificationService = NotificationService.getInstance();

    public ResetAction() {
        super(AllIcons.Actions.Rollback);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
        final Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        final Project project = anActionEvent.getProject();
        if (project == null) {
            return;
        }

        fetchAction.fetchChange(selectedChange.get(), project,
            (repository, commitHash) -> resetToFetchedCommit(project, repository, commitHash));
    }

    private void resetToFetchedCommit(Project project, GitRepository repository, String commitHash) {
        Optional<GitCommit> commit;
        try {
            commit = gerritGitUtil.loadCommit(project, repository, commitHash);
        } catch (VcsException e) {
            notifyError(project, String.format("Could not load commit '%s': %s", commitHash, e.getMessage()));
            return;
        }
        if (!commit.isPresent()) {
            notifyError(project, String.format("Could not load commit '%s'.", commitHash));
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> askAndReset(project, repository, commit.get()), project.getDisposed());
    }

    private static void askAndReset(Project project, GitRepository repository, GitCommit commit) {
        GitVcsSettings settings = GitVcsSettings.getInstance(project);
        GitResetMode defaultMode = ObjectUtils.notNull(settings.getResetMode(), GitResetMode.getDefault());
        Map<GitRepository, VcsFullCommitDetails> commits = Collections.singletonMap(repository, commit);
        GitNewResetDialog dialog;
        try {
            // The constructor is protected in 2020.3, where the platform only opens this dialog from its own log
            // action, and public in 2026.2, where the class is final: neither a subclass nor a plain call links
            // against both.
            Constructor<GitNewResetDialog> constructor =
                GitNewResetDialog.class.getDeclaredConstructor(Project.class, Map.class, GitResetMode.class);
            constructor.setAccessible(true);
            dialog = constructor.newInstance(project, commits, defaultMode);
        } catch (ReflectiveOperationException e) {
            throw unchecked(e);
        }
        if (!dialog.showAndGet()) {
            return;
        }
        GitResetMode selectedMode = dialog.getResetMode();
        settings.setResetMode(selectedMode);

        Map<GitRepository, Hash> hashes = Collections.singletonMap(repository, commit.getId());
        new Task.Backgroundable(project, "Resetting...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                GitResetOperation operation = new GitResetOperation(project, hashes, selectedMode, indicator);
                try {
                    // execute() returns void in 2020.3 and boolean in 2026.2, and the return type is part of
                    // the method descriptor a compiled call links against.
                    GitResetOperation.class.getMethod("execute").invoke(operation);
                } catch (ReflectiveOperationException e) {
                    throw unchecked(e);
                }
            }
        }.queue();
    }

    /*
     * Fails the way a plain call would: what the platform threw, ProcessCanceledException included, or the API
     * drift itself, ends up in the IDE error reporter, which names this plugin.
     */
    private static RuntimeException unchecked(ReflectiveOperationException e) {
        Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(cause);
    }

    private void notifyError(Project project, String message) {
        notificationService.notifyError(new NotificationBuilder(project, "Reset Error", message));
    }
}
