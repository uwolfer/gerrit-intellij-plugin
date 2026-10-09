/*
 * Copyright 2013-2016 Urs Wolfer
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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritChangeListPanel;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritChangesListener;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindow;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindowFactory;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * @author Urs Wolfer
 */
public class ActionUtil {

    private ActionUtil() {}

    public static Optional<ChangeInfo> getSelectedChange(@Nullable AnActionEvent anActionEvent) {
        if (anActionEvent == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(anActionEvent.getData(GerritChangeListPanel.SELECTED_CHANGE));
    }

    /**
     * Reloads the list of changes once Gerrit has applied an action, so that it no longer shows e.g. an abandoned
     * change as open.
     */
    public static void reloadChanges(Project project) {
        project.getMessageBus().syncPublisher(GerritChangesListener.TOPIC).changesModified();
    }

    /**
     * Shows what the query finds in the Gerrit tool window, and brings the window to the front.
     */
    public static void showChanges(Project project, String query) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(GerritToolWindowFactory.ID);
        if (toolWindow == null) {
            return;
        }
        // creates the content on the first call, before the tool window has ever been shown
        Content content = toolWindow.getContentManager().getContent(0);
        if (content == null) {
            return;
        }
        GerritToolWindow gerritToolWindow = GerritToolWindow.GERRIT_TOOL_WINDOW.getData(
            DataManager.getInstance().getDataContext(content.getComponent()));
        if (gerritToolWindow != null) {
            gerritToolWindow.showChanges(project, query);
            toolWindow.activate(null);
        }
    }
}
