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

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Urs Wolfer
 */
public class GerritToolWindowFactory implements ToolWindowFactory, DumbAware {
    public static final String ID = "Gerrit";

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return GerritProjectSettings.isEnabled(project);
    }

    /**
     * Shows or hides the window of a project after it was switched on or off. Only hidden: its content keeps a
     * filter the user may come back to, and does not load anything while the project is off.
     */
    public static void updateAvailability(@NotNull Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (toolWindow != null) {
            toolWindow.setAvailable(GerritProjectSettings.isEnabled(project));
        }
    }

    @Override
    public void createToolWindowContent(final Project project, ToolWindow toolWindow) {
        GerritToolWindow gerritToolWindow = new GerritToolWindow();
        SimpleToolWindowPanel toolWindowContent = gerritToolWindow.createToolWindowContent(project);

        ContentManager contentManager = toolWindow.getContentManager();
        Content content = contentManager.getFactory().createContent(toolWindowContent, "", false);
        content.setDisposer(gerritToolWindow);
        contentManager.addContent(content);
        contentManager.setSelectedContent(content);
    }
}
