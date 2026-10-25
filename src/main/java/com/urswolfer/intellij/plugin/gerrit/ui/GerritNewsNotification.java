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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Tells a user once about what the plugin has gained, and where to ask for more. Meant for those who installed it
 * long ago and stopped using it for something it lacked then, so it stays out of the way: one balloon per
 * {@link #NEWS_ID} for the whole IDE, only in a project which is on Gerrit, and a while after the start, when the
 * review notifications and the commit-msg hook warning have had their say. The plugin keeps no record of an earlier
 * version, so a fresh install gets it too.
 *
 * Raise {@link #NEWS_ID} with the texts when there is something new to tell, never to repeat the same.
 *
 * @author Urs Wolfer
 */
public final class GerritNewsNotification {
    static final int NEWS_ID = 1;
    private static final String SHOWN_KEY = "gerrit.news.shown";
    private static final String GROUP = "gerrit.news";
    private static final long DELAY_SECONDS = 60;
    private static final String REPOSITORY_URL = "https://github.com/uwolfer/gerrit-intellij-plugin";

    private GerritNewsNotification() {}

    public static void showOnce(@NotNull Project project) {
        if (ApplicationManager.getApplication().isUnitTestMode()
            || !isDue(PropertiesComponent.getInstance().getInt(SHOWN_KEY, 0), NEWS_ID)) {
            return;
        }
        ProjectLevelVcsManager.getInstance(project).runAfterInitialization(
            () -> AppExecutorUtil.getAppScheduledExecutorService().schedule(
                () -> show(project), DELAY_SECONDS, TimeUnit.SECONDS));
    }

    static boolean isDue(int shownId, int currentId) {
        return shownId < currentId;
    }

    private static void show(Project project) {
        if (project.isDisposed() || !GerritProjectSettings.isEnabled(project) || !isOnGerrit(project)) {
            return;
        }
        NotificationBuilder notification = new NotificationBuilder(GROUP, project,
            GerritBundle.message("news.title"), GerritBundle.message("news.text"));
        notification.action(NotificationAction.createSimpleExpiring(GerritBundle.message("news.whatsNew"),
            () -> BrowserUtil.browse(REPOSITORY_URL + "/releases")));
        notification.action(NotificationAction.createSimpleExpiring(GerritBundle.message("news.feedback"),
            () -> BrowserUtil.browse(REPOSITORY_URL + "/issues/new/choose")));
        if (markShown()) {
            NotificationService.getInstance().notifyInformation(notification);
        }
    }

    /**
     * Marked when shown, not when clicked: an ignored balloon is an answer too. Locked, as projects restored together
     * all get here at the same moment.
     */
    private static synchronized boolean markShown() {
        PropertiesComponent properties = PropertiesComponent.getInstance();
        if (!isDue(properties.getInt(SHOWN_KEY, 0), NEWS_ID)) {
            return false;
        }
        properties.setValue(SHOWN_KEY, NEWS_ID, 0);
        return true;
    }

    private static boolean isOnGerrit(Project project) {
        for (GitRepository repository : GitUtil.getRepositories(project)) {
            if (GerritRemotes.getGerritProject(repository, null) != null) {
                return true;
            }
        }
        return false;
    }
}
