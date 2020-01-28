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

import com.google.common.base.Optional;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class DeleteAction extends AbstractLoggedInChangeAction {

    public DeleteAction() {
        super("Delete Draft", "Delete Draft Change", AllIcons.Actions.Cancel);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selectedChange = getSelectedChange(e);
        if (selectedChange.isPresent() && !canPublish(selectedChange.get())) {
            e.getPresentation().setEnabled(false);
        }
    }

    private boolean canPublish(ChangeInfo selectedChange) {
        if (!ChangeStatus.DRAFT.equals(selectedChange.status)) {
            return false;
        }
        if (selectedChange.actions == null) {
            // if there are absolutely no actions, assume an older Gerrit instance
            // which does not support receiving actions
            // return false once we drop Gerrit < 2.9 support
            return true;
        }
        ActionInfo deleteAction = selectedChange.actions.get("/");
        if (deleteAction == null) {
            return false;
        }
        if (!"DELETE".equalsIgnoreCase(deleteAction.method)) {
            return false;
        }
        return Boolean.TRUE.equals(deleteAction.enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        gerritUtil.delete(selectedChange.get().id, project);
    }

    public static class Proxy extends DeleteAction {
        private final DeleteAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(DeleteAction.class);
        }

        @Override
        public void update(AnActionEvent e) {
            delegate.update(e);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
