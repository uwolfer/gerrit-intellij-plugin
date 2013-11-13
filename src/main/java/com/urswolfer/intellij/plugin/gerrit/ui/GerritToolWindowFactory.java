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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;

import java.awt.*;

/**
 * @author Urs Wolfer
 */
public class GerritToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(final Project project, ToolWindow toolWindow) {
        GerritToolWindow gerritToolWindow = GerritModule.getInstance(GerritToolWindow.class);

        Component component = toolWindow.getComponent();
        SimpleToolWindowPanel toolWindowContent = gerritToolWindow.createToolWindowContent(project);
        component.getParent().add(toolWindowContent);
    }
}
