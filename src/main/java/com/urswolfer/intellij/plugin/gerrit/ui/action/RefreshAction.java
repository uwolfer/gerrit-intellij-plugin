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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.inject.Inject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindow;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindowFactory;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritUpdatesNotificationComponent;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class RefreshAction extends AnAction implements DumbAware, UpdateInBackground {
    @Inject
    private GerritUpdatesNotificationComponent gerritUpdatesNotificationComponent;

    public RefreshAction() {
        super("Refresh", "Refresh changes list", AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        GerritToolWindowFactory.ProjectService projectService = project.getService(GerritToolWindowFactory.ProjectService.class);
        GerritToolWindow gerritToolWindow = projectService.getGerritToolWindow();
        gerritToolWindow.reloadChanges(project, true);
        gerritUpdatesNotificationComponent.handleNotification();
    }

    public static class Proxy extends RefreshAction {
        private final RefreshAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(RefreshAction.class);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
