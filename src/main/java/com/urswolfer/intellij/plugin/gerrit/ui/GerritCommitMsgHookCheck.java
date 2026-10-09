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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.git.GerritCommitMsgHook;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Warns when a repository of Gerrit has no commit-msg hook, as its commits then get no Change-Id, and offers to
 * install it. Repositories not cloned through the plugin are the ones which lack it.
 *
 * @author Urs Wolfer
 */
public final class GerritCommitMsgHookCheck {
    private static final Logger LOG = Logger.getInstance(GerritCommitMsgHookCheck.class);
    private static final String SKIP_KEY_PREFIX = "gerrit.commitMsgHook.skip.";

    private GerritCommitMsgHookCheck() {}

    public static void checkOnStartup(@NotNull Project project) {
        // the Git repositories are only known once the VCS mappings have been set up
        ProjectLevelVcsManager.getInstance(project).runAfterInitialization(
            () -> ApplicationManager.getApplication().executeOnPooledThread(() -> check(project)));
    }

    private static void check(Project project) {
        if (!GerritProjectSettings.isEnabled(project)) {
            return;
        }
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        if (account == null || account.host.isEmpty()) { // with several accounts and no choice there is no one to fetch from
            return;
        }
        Map<GitRepository, Path> missing = new LinkedHashMap<>();
        for (GitRepository repository : GitUtil.getRepositories(project)) {
            try {
                if (GerritRemotes.getGerritProject(repository, null) == null
                    || PropertiesComponent.getInstance(project).getBoolean(skipKey(repository))) {
                    continue;
                }
                Path hookFile = GerritCommitMsgHook.getInstance().getHookFile(repository);
                if (hookFile != null && !GerritCommitMsgHook.getInstance().isInstalled(hookFile)) {
                    missing.put(repository, hookFile);
                }
            } catch (RuntimeException e) { // one unusual repository must not keep the others from being checked
                LOG.info("Could not check the commit-msg hook of " + repository, e);
            }
        }
        if (!missing.isEmpty() && !project.isDisposed()) {
            notifyMissing(project, account, missing);
        }
    }

    private static void notifyMissing(Project project, GerritAccount account, Map<GitRepository, Path> missing) {
        String message = GerritBundle.message("hook.missing.text", missing.keySet().stream().map(r -> r.getRoot().getName()).collect(Collectors.joining(", ")));
        NotificationBuilder notification = new NotificationBuilder(project, GerritBundle.message("hook.missing.title"),
            message);
        notification.action(new NotificationAction(GerritBundle.message("hook.install")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull com.intellij.notification.Notification n) {
                n.expire();
                install(project, account, missing);
            }
        });
        notification.action(new NotificationAction(GerritBundle.message("hook.dontAsk")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull com.intellij.notification.Notification n) {
                n.expire();
                for (GitRepository repository : missing.keySet()) {
                    PropertiesComponent.getInstance(project).setValue(skipKey(repository), true);
                }
            }
        });
        NotificationService.getInstance().notifyWarning(notification);
    }

    private static void install(Project project, GerritAccount account, Map<GitRepository, Path> missing) {
        new Task.Backgroundable(project, GerritBundle.message("hook.progress"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                for (Map.Entry<GitRepository, Path> entry : missing.entrySet()) {
                    try {
                        GerritCommitMsgHook.getInstance().install(entry.getValue(), account);
                        NotificationService.getInstance().notify(new NotificationBuilder(project,
                            GerritBundle.message("hook.installed"), entry.getValue().toString()));
                    } catch (Exception e) {
                        LOG.info(e);
                        NotificationService.getInstance().notifyError(new NotificationBuilder(project,
                            GerritBundle.message("hook.failed"),
                            GerritUtil.getInstance().getErrorTextFromException(e)));
                    }
                }
            }
        }.queue();
    }

    private static String skipKey(GitRepository repository) {
        return SKIP_KEY_PREFIX + repository.getRoot().getPath();
    }
}
