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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritChangeListPanel;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritChangeListPanel.ColumnToggle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows and hides the columns of the list of changes, from the menu of its header.
 */
public class ColumnsActionGroup extends ActionGroup implements DumbAware, UpdateInBackground {

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        List<ColumnToggle> columns = e != null ? e.getData(GerritChangeListPanel.COLUMNS) : null;
        if (columns == null) {
            return AnAction.EMPTY_ARRAY;
        }
        return columns.stream().map(ToggleColumnAction::new).toArray(AnAction[]::new);
    }

    private static final class ToggleColumnAction extends ToggleAction implements DumbAware, UpdateInBackground {
        private final ColumnToggle column;

        private ToggleColumnAction(ColumnToggle column) {
            super(column.getName());
            this.column = column;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return column.isVisible();
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            column.setVisible(state);
        }
    }
}
