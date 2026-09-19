/*
 * Copyright 2026 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the Gerrit push settings panel of a project: the push dialog asks for an options panel every time it is
 * opened, while the values entered for the last push are meant to be there again.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.PROJECT)
public final class GerritPushPanelHolder {

    private volatile GerritPushExtensionPanel panel;

    public static GerritPushPanelHolder getInstance(Project project) {
        return project.getService(GerritPushPanelHolder.class);
    }

    public GerritPushExtensionPanel getPanel() {
        if (panel == null) {
            panel = new GerritPushExtensionPanel(GerritSettings.getInstance().getPushToGerrit());
        }
        return panel;
    }

    /**
     * Returns why the Gerrit push settings cannot be used, or {@code null} when a push can go ahead. Called from
     * the thread which checks a push, so it neither creates the panel nor reads its components.
     */
    @Nullable
    public String getValidationError() {
        GerritPushExtensionPanel currentPanel = panel;
        return currentPanel == null ? null : currentPanel.getValidationError();
    }
}
