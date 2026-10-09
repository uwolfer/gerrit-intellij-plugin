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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.HeadChanges;
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
@Service(Service.Level.PROJECT)
public final class GerritUpdatesNotificationComponent implements Disposable {
    private final Project project;
    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private final NotificationService notificationService = NotificationService.getInstance();

    private final Set<String> notifiedChanges = Collections.synchronizedSet(new HashSet<String>());
    /** What waited for the review of another account or login says nothing about what is new to this one. */
    private String notifiedAccount = "";
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
        if (!GerritProjectSettings.isEnabled(project)) {
            return;
        }

        if (!gerritSettings.getReviewNotifications()) {
            return;
        }

        String host = GerritProjectAccount.getInstance(project).getHost();
        String login = GerritProjectAccount.getInstance(project).getLogin();
        if (host == null || host.isEmpty() || login == null || login.isEmpty()) {
            return;
        }

        String account = account();
        gerritUtil.getChangesToReview(project, changes -> consume(changes, account));
    }

    /**
     * The account and its login, but not its host: a host written another way is still the same instance, whose
     * pending reviews are no news.
     */
    private String account() {
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        return account != null ? account.id + '\n' + account.login : "";
    }

    private synchronized void consume(List<ChangeInfo> changes, String account) {
        if (!GerritProjectSettings.isEnabled(project)) { // switched off while the query was running
            return;
        }
        if (!account.equals(account())) {
            return; // asked for an account this project has left meanwhile
        }
        if (!account.equals(notifiedAccount)) {
            notifiedChanges.clear();
            notifiedAccount = account;
        }
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
                stringBuilder.append(listItem(change, !notifiedChanges.contains(change.id)));
                notifiedChanges.add(change.id);
            }
            stringBuilder.append("</ul>");
            NotificationBuilder notification = new NotificationBuilder(
                    project,
                    GerritBundle.message("updates.title"),
                    stringBuilder.toString()
            );
            notificationService.notifyInformation(notification);
        }
    }

    /**
     * The notification renders HTML, so a subject like "Use Optional<String>" has to be escaped. Gerrit leaves out
     * the name of an account which has none, and some give a blank one.
     */
    static String listItem(ChangeInfo change, boolean isNew) {
        AccountInfo account = change.owner;
        String owner = !StringUtil.isEmptyOrSpaces(account.name) ? account.name
            : account.email != null ? account.email
            : account.username != null ? account.username
            : String.valueOf(account._accountId);
        return "<li>" + (isNew ? "<strong>" + GerritBundle.message("updates.new") + " </strong>" : "")
            + StringUtil.escapeXmlEntities(change.project) + ": " + StringUtil.escapeXmlEntities(change.subject)
            + " (" + GerritBundle.message("updates.owner", StringUtil.escapeXmlEntities(owner)) + ")</li>";
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
        if (gerritSettings.getAutomaticRefresh() && refreshTimeout > 0 && !alarm.isDisposed()
            && GerritProjectSettings.isEnabled(project)) {
            final long generation = scheduleGeneration;
            alarm.addRequest(new Runnable() {
                @Override
                public void run() {
                    handleNotification();
                    HeadChanges.refreshIfCreated(project); // the comments in the editor, which reviewers add to
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
