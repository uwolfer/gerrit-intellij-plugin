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
import com.google.inject.Inject;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class GerritUpdatesNotificationComponent implements ProjectComponent, Consumer<List<ChangeInfo>> {
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private NotificationService notificationService;

    private Timer timer;
    private Set<String> notifiedChanges = new HashSet<String>();
    private Project project;

    @Override
    public void projectOpened() {
        handleNotification();
        setupRefreshTask();
    }

    @Override
    public void projectClosed() {
        cancelPendingNotificationTasks();
        notifiedChanges.clear();
    }

    @Override
    public void initComponent() {
    }

    @Override
    public void disposeComponent() {
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "GerritUpdatesNotificationComponent";
    }

    public void handleConfigurationChange() {
        cancelPendingNotificationTasks();
        setupRefreshTask();
    }

    public void handleNotification() {
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

    private void cancelPendingNotificationTasks() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void setupRefreshTask() {
        long refreshTimeout = gerritSettings.getRefreshTimeout();
        if (gerritSettings.getAutomaticRefresh() && refreshTimeout > 0) {
            if (timer == null) {
                timer = new Timer();
            }
            timer.schedule(new CheckReviewTask(), refreshTimeout * 60 * 1000);
        }
    }

    public void setProject(Project project) {
        this.project = project;
    }

    private class CheckReviewTask extends TimerTask {
        @Override
        public void run() {
            handleNotification();

            long refreshTimeout = gerritSettings.getRefreshTimeout();
            if (gerritSettings.getAutomaticRefresh() && refreshTimeout > 0) {
                timer.schedule(new CheckReviewTask(), refreshTimeout * 60 * 1000);
            }
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    private static class Proxy extends GerritUpdatesNotificationComponent {

        private final GerritUpdatesNotificationComponent delegate;

        public Proxy(Project project) {
            delegate = GerritModule.getInstance(GerritUpdatesNotificationComponent.class);
            delegate.setProject(project);
        }

        @Override
        public void projectOpened() {
            delegate.projectOpened();
        }

        @Override
        public void projectClosed() {
            delegate.projectClosed();
        }

        @Override
        public void initComponent() {
            delegate.initComponent();
        }

        @Override
        public void disposeComponent() {
            delegate.disposeComponent();
        }

        @NotNull
        @Override
        public String getComponentName() {
            return delegate.getComponentName();
        }

        @Override
        public void setProject(Project project) {
            delegate.setProject(project);
        }
    }
}
