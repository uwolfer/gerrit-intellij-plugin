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

import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;

import java.util.Optional;

public class RestoreAction extends AbstractLoggedInChangeAction {

    public RestoreAction() {
        super(AllIcons.Actions.Undo);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selectedChange = getSelectedChange(e);
        if (selectedChange.isPresent() && !ChangeActionAvailability.canRestore(selectedChange.get())) {
            e.getPresentation().setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (project == null || !selectedChange.isPresent()) {
            return;
        }

        ChangeMessageDialog dialog = new ChangeMessageDialog(project, GerritBundle.message("restore.title"), GerritBundle.message("restore.ok"), "");
        if (!dialog.showAndGet()) {
            return;
        }
        RestoreInput restoreInput = new RestoreInput();
        restoreInput.message = dialog.message();

        gerritUtil.postRestore(selectedChange.get().id, restoreInput, project,
            result -> ActionUtil.reloadChanges(project));
    }
}
