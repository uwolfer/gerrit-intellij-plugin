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

package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Thomas Forrer
 */
public class IsStarredFilter extends AbstractChangesFilter {
    private static final String STATE_KEY = "starred";

    private boolean value = false;

    @Override
    public AnAction getAction(Project project) {
        return new IsStarredAction();
    }

    @Override
    public void saveState(@NotNull Map<String, String> state) {
        if (value) {
            state.put(STATE_KEY, Boolean.TRUE.toString());
        }
    }

    @Override
    public void restoreState(@NotNull Map<String, String> state, @NotNull FilterEnvironment environment) {
        value = Boolean.parseBoolean(state.get(STATE_KEY));
    }

    private void setValue(boolean value) {
        this.value = value;
        fireFilterChanged();
    }

    @Nullable
    @Override
    public String getSearchQueryPart() {
        return value ? "is:starred" : null;
    }

    public final class IsStarredAction extends ToggleAction implements DumbAware, UpdateInBackground {
        public IsStarredAction() {
            super(GerritBundle.message("filter.starred"), GerritBundle.message("filter.starred.description"), AllIcons.Nodes.Favorite);
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return value;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
            setValue(state);
        }
    }

}
