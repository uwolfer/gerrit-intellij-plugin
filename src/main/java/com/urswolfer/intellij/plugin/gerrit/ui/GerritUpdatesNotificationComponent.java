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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Project service, started by {@link GerritUpdatesNotificationStartupActivity} and disposed with its project.
 *
 * @author Urs Wolfer
 */
public final class GerritUpdatesNotificationComponent implements Consumer<List<ChangeInfo>>, Disposable {
    private final Project project;
    private final GerritUtil gerritUtil;
    private final GerritSettings gerritSettings;
    private final NotificationService notificationService;

    private final Set<String> notifiedChanges = Collections.synchronizedSet(new HashSet<String>());
    private Timer timer;

    public GerritUpdatesNotificationComponent(Project project) {
        this.project = project;
        gerritUtil = GerritModule.getInstance(GerritUtil.class);
        gerritSettings = GerritModule.getInstance(GerritSettings.class);
        notificationService = GerritModule.getInstance(NotificationService.class);
    }

    public static GerritUpdatesNotificationComponent getInstance(Project project) {
        return project.getService(GerritUpdatesNotificationComponent.class);
    }

    /** The settings are application wide, so every open project has to pick up a change. */
    public static void configurationChanged() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                getInstance(project).handleConfigurationChange();
            }
        }
    }

    public synchronized void projectOpened() {
        handleNotification();
        setupRefreshTask();
    }

    @Override
    public void dispose() {
        cancelPendingNotificationTasks();
        notifiedChanges.clear();
    }

    public synchronized void handleConfigurationChange() {
        cancelPendingNotificationTasks();
        setupRefreshTask();
    }

    public void handleNotification() {
        if (project.isDisposed()) {
            return;
        }

        if (!gerritSettings.getReviewNotifications()) {
            return;
        }

        if (Strings.isNullOrEmpty(gerritSettings.getHost())
                || Strings.isNullOrEmpty(gerritSettings.getLogin())) {
            return;
        }

        gerritUtil.getChangesToReview(project, this);
    }

    @Override
    public void consume(List<ChangeInfo> changes) {
        boolean newChange = false;
        for (ChangeInfo change : changes) {
            if (!notifiedChanges.contains(change.id)) {
                newChange = true;
                break;
            }
        }
        if (newChange) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("<ul>");
            for (ChangeInfo change : changes) {
                stringBuilder
                        .append("<li>")
                        .append(!notifiedChanges.contains(change.changeId) ? "<strong>NEW: </strong>" : "")
                        .append(change.project)
                        .append(": ")
                        .append(change.subject)
                        .append(" (Owner: ").append(change.owner.name).append(')')
                        .append("</li>");

                notifiedChanges.add(change.id);
            }
            stringBuilder.append("</ul>");
            NotificationBuilder notification = new NotificationBuilder(
                    project,
                    "Gerrit Changes waiting for my review",
                    stringBuilder.toString()
            );
            notificationService.notifyInformation(notification);
        }
    }

    private synchronized void cancelPendingNotificationTasks() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private synchronized void setupRefreshTask() {
        long refreshTimeout = gerritSettings.getRefreshTimeout();
        if (gerritSettings.getAutomaticRefresh() && refreshTimeout > 0) {
            if (timer == null) {
                timer = new Timer();
            }
            timer.schedule(new CheckReviewTask(timer), refreshTimeout * 60 * 1000);
        }
    }

    /** Ignores a task whose timer has been cancelled or replaced meanwhile, which would double the polling. */
    private synchronized void rescheduleRefreshTask(Timer scheduledBy) {
        if (timer == scheduledBy) {
            setupRefreshTask();
        }
    }

    private class CheckReviewTask extends TimerTask {
        private final Timer scheduledBy;

        private CheckReviewTask(Timer scheduledBy) {
            this.scheduledBy = scheduledBy;
        }

        @Override
        public void run() {
            handleNotification();
            rescheduleRefreshTask(scheduledBy);
        }
    }
}
