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

import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public class RebaseAction extends AbstractLoggedInChangeAction {

    public RebaseAction() {
        super(AllIcons.Vcs.Branch);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selectedChange = getSelectedChange(e);
        if (selectedChange.isPresent() && !ChangeActionAvailability.canRebase(selectedChange.get())) {
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
        ChangeInfo change = selectedChange.get();

        RebaseDialog dialog = new RebaseDialog(project, change.branch, ChangeActionAvailability.canRebaseOnTip(change));
        if (!dialog.showAndGet()) {
            return;
        }
        RebaseInput rebaseInput = new RebaseInput();
        rebaseInput.base = dialog.base();
        rebaseInput.allowConflicts = dialog.allowConflicts();

        // the reload brings the new patch set, also to what is shown of the change and what a checked out one is
        // compared with
        gerritUtil.postRebase(change.id, rebaseInput, project, result -> ActionUtil.reloadChanges(project));
    }

    /**
     * An empty base is the tip of the target branch, which also breaks the dependency on a parent change; without
     * one, Gerrit rebases on the parent change when there is one.
     */
    static String base(boolean onOther, String text) {
        return onOther ? Whitespace.trim(text) : "";
    }

    private static class RebaseDialog extends DialogWrapper {
        private final JBRadioButton onTip;
        private final JBRadioButton onOther = new JBRadioButton(GerritBundle.message("rebase.other"));
        private final JBTextField baseField = new JBTextField(30);
        private final JBCheckBox allowConflicts = new JBCheckBox(GerritBundle.message("rebase.conflicts"));

        RebaseDialog(Project project, @Nullable String branch, boolean canRebaseOnTip) {
            super(project, true);
            setTitle(GerritBundle.message("rebase.title"));
            setOKButtonText(GerritBundle.message("rebase.ok"));
            onTip = new JBRadioButton(branch != null ? GerritBundle.message("rebase.onBranch", branch) : GerritBundle.message("rebase.onTarget"), canRebaseOnTip);
            onTip.setEnabled(canRebaseOnTip);
            onOther.setSelected(!canRebaseOnTip);
            ButtonGroup group = new ButtonGroup();
            group.add(onTip);
            group.add(onOther);
            baseField.setEnabled(!canRebaseOnTip);
            onOther.addItemListener(e -> {
                baseField.setEnabled(onOther.isSelected());
                if (onOther.isSelected()) {
                    baseField.requestFocusInWindow();
                }
            });
            init();
        }

        String base() {
            return RebaseAction.base(onOther.isSelected(), baseField.getText());
        }

        boolean allowConflicts() {
            return allowConflicts.isSelected();
        }

        @Nullable
        @Override
        protected ValidationInfo doValidate() {
            return onOther.isSelected() && base().isEmpty()
                ? new ValidationInfo(GerritBundle.message("rebase.validation"), baseField)
                : null;
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = GridBagConstraints.RELATIVE;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            panel.add(onTip, c);
            panel.add(onOther, c);
            c.insets = JBUI.insetsLeft(24);
            panel.add(baseField, c);
            c.insets = JBUI.insetsTop(8);
            panel.add(allowConflicts, c);
            c.insets = JBUI.insetsLeft(24);
            panel.add(new JBLabel(GerritBundle.message("rebase.conflicts.hint")), c);
            return panel;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return onTip.isEnabled() ? onTip : baseField;
        }
    }
}
