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
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Project service, started by {@link GerritUpdatesNotificationStartupActivity} and disposed with its project.
 *
 * @author Urs Wolfer
 */
public final class GerritUpdatesNotificationComponent implements Consumer<List<ChangeInfo>>, Disposable {
    private final Project project;
    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private final NotificationService notificationService = NotificationService.getInstance();

    private final Set<String> notifiedChanges = Collections.synchronizedSet(new HashSet<String>());
    /** Registered against this service, so the project disposing it also drops any pending poll. */
    private final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    /** Bumped whenever pending polls are dropped, so a poll already running does not schedule the next one. */
    private long scheduleGeneration;

    public GerritUpdatesNotificationComponent(Project project) {
        this.project = project;
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
        restartRefreshTask();
    }

    @Override
    public void dispose() {
        notifiedChanges.clear();
    }

    public synchronized void handleConfigurationChange() {
        restartRefreshTask();
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
        scheduleGeneration++;
        if (!alarm.isDisposed()) {
            alarm.cancelAllRequests();
        }
    }

    /** Both entry points go through here, so an already running poll is never left behind next to a new one. */
    private synchronized void restartRefreshTask() {
        cancelPendingNotificationTasks();
        scheduleRefreshTask();
    }

    private synchronized void scheduleRefreshTask() {
        long refreshTimeout = gerritSettings.getRefreshTimeout();
        if (gerritSettings.getAutomaticRefresh() && refreshTimeout > 0 && !alarm.isDisposed()) {
            final long generation = scheduleGeneration;
            alarm.addRequest(new Runnable() {
                @Override
                public void run() {
                    handleNotification();
                    rescheduleRefreshTask(generation);
                }
            }, refreshTimeout * 60 * 1000);
        }
    }

    /** Ignores a poll cancelled or replaced while it was running, which would otherwise double the polling. */
    private synchronized void rescheduleRefreshTask(long generation) {
        if (generation == scheduleGeneration) {
            scheduleRefreshTask();
        }
    }
}
