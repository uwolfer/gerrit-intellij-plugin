/*
 * Copyright 2013-2014 Urs Wolfer
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
import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.ui.SafeHtmlTextEditor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class AbandonAction extends AbstractLoggedInChangeAction {

    public AbandonAction() {
        super("Abandon", "Abandon Change", AllIcons.Actions.Cancel);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selectedChange = getSelectedChange(e);
        if (selectedChange.isPresent() && !canAbandon(selectedChange.get())) {
            e.getPresentation().setEnabled(false);
        }
    }

    private boolean canAbandon(ChangeInfo selectedChange) {
        if (selectedChange.actions == null) {
            // if there are absolutely no actions, assume an older Gerrit instance
            // which does not support receiving actions
            // return false once we drop Gerrit < 2.9 support
            return true;
        }
        ActionInfo abandonAction = selectedChange.actions.get("abandon");
        return abandonAction != null && Boolean.TRUE.equals(abandonAction.enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);

        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }

        AbandonInput abandonInput = new AbandonInput();

        SafeHtmlTextEditor editor = new SafeHtmlTextEditor(project);
        AbandonDialog dialog = new AbandonDialog(project, true, editor);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }
        String message = editor.getMessageField().getText().trim();
        if (!Strings.isNullOrEmpty(message)) {
            abandonInput.message = message;
        }

        gerritUtil.postAbandon(selectedChange.get().id, abandonInput, project);
    }

    private static class AbandonDialog extends DialogWrapper {
        private final SafeHtmlTextEditor editor;

        protected AbandonDialog(Project project, boolean canBeParent, SafeHtmlTextEditor editor) {
            super(project, canBeParent);
            this.editor = editor;
            setTitle("Abandon Change");
            setOKButtonText("Abandon");
            init();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            return editor;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return editor.getMessageField();
        }
    }

    public static class Proxy extends AbandonAction {
        private final AbandonAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(AbandonAction.class);
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
