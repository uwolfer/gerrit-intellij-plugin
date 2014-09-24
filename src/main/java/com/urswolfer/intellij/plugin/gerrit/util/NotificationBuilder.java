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

package com.urswolfer.intellij.plugin.gerrit.util;

import com.google.common.base.Optional;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

/**
 * @author Thomas Forrer
 */
public final class NotificationBuilder {
    private static final String GERRIT_NOTIFICATION_GROUP = "gerrit";

    private final Project project;
    private final String title;
    private final String message;
    private NotificationType type = NotificationType.INFORMATION;

    private Optional<NotificationListener> listener = Optional.absent();
    private boolean showBalloon = true;

    public NotificationBuilder(Project project, String title, String message) {
        this.project = project;
        this.title = title;
        this.message = message;
    }

    public NotificationBuilder listener(NotificationListener listener) {
        this.listener = Optional.of(listener);
        return this;
    }

    public NotificationBuilder type(NotificationType type) {
        this.type = type;
        return this;
    }

    public NotificationBuilder showBalloon() {
        this.showBalloon = true;
        return this;
    }

    public NotificationBuilder hideBalloon() {
        this.showBalloon = false;
        return this;
    }

    protected Notification get() {
        Notification notification = new Notification(GERRIT_NOTIFICATION_GROUP, title, message, type, listener.orNull());
        if (!showBalloon) {
            notification.expire();
        }
        return notification;
    }

    protected Project getProject() {
        return project;
    }
}
