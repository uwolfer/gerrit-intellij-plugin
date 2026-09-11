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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

/**
 * Easy access to open IDE's notification bus.
 *
 * @author Thomas Forrer
 */
@Service(Service.Level.APP)
public final class NotificationService {

    public static NotificationService getInstance() {
        return ApplicationManager.getApplication().getService(NotificationService.class);
    }

    public void notifyError(NotificationBuilder notificationBuilder) {
        notify(notificationBuilder.type(NotificationType.ERROR));
    }
    
    public void notifyWarning(NotificationBuilder notificationBuilder) {
        notify(notificationBuilder.type(NotificationType.WARNING));
    }

    public void notifyInformation(NotificationBuilder notificationBuilder) {
        notify(notificationBuilder.type(NotificationType.INFORMATION));
    }

    public void notify(NotificationBuilder notificationBuilder) {
        notificationBuilder.get().notify(notificationBuilder.getProject());
    }
}
