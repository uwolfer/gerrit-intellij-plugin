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

package com.urswolfer.intellij.plugin.gerrit;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * Whether the plugin takes part in a project at all, for a project which is not reviewed on Gerrit: its push dialog
 * would otherwise still offer to push for review, to a remote which has no idea what refs/for/ is.
 *
 * Kept in the workspace file, like {@link GerritProjectAccount}: it is a choice of whoever has the plugin installed,
 * and a team which shares the project files does not all have it.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.PROJECT)
@State(name = "GerritProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GerritProjectSettings implements PersistentStateComponent<GerritProjectSettings.ProjectState> {

    public static final class ProjectState {
        @Attribute("enabled") public boolean enabled = true;
    }

    private volatile ProjectState state = new ProjectState();

    public static GerritProjectSettings getInstance(@NotNull Project project) {
        return project.getService(GerritProjectSettings.class);
    }

    public static boolean isEnabled(@NotNull Project project) {
        return !project.isDisposed() && getInstance(project).isEnabled();
    }

    @Override
    public ProjectState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull ProjectState state) {
        this.state = state;
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    public void setEnabled(boolean enabled) {
        // replaced rather than changed: the polls and the hook check read it from pooled threads
        ProjectState changed = new ProjectState();
        changed.enabled = enabled;
        state = changed;
    }
}
