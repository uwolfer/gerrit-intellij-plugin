/*
 * Copyright 2013-2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindow;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

@SuppressWarnings("ComponentNotRegistered")
public class RefreshFeatureMergePatchSetAction extends AbstractLoggedInChangeAction {
    private final NotificationService notificationService = NotificationService.getInstance();

    public RefreshFeatureMergePatchSetAction() {
        super("Refresh Feature Merge Patch Set", "Recreate the selected merge using current branch tips", AllIcons.Actions.Refresh);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selected = getSelectedChange(e);
        e.getPresentation().setEnabled(e.getPresentation().isEnabled() && hasSingleSelection(e) && selected.isPresent()
                && selected.get().status == ChangeStatus.NEW);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        Optional<ChangeInfo> selected = getSelectedChange(e);
        if (project == null || !hasSingleSelection(e) || !selected.isPresent()
                || selected.get().status != ChangeStatus.NEW) {
            return;
        }
        final ChangeInfo change = selected.get();
        final GerritToolWindow toolWindow = e.getData(GerritToolWindow.GERRIT_TOOL_WINDOW);
        RefreshMergeDialog dialog = new RefreshMergeDialog(project, change.project, change.branch);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }

        MergePatchSetInput input = createInput(dialog.sourceBranchField.getText());
        gerritUtil.createMergePatchSet(change.id, input, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo refreshedChange) {
                reloadChanges(toolWindow, project);
                notificationService.notifyInformation(new NotificationBuilder(project, "Gerrit Merge Patch Set Refreshed",
                        "Created a new patch set for change " + refreshedChange._number));
            }
        });
    }

    static MergePatchSetInput createInput(String sourceBranch) {
        String source = sourceBranch.trim();
        MergePatchSetInput input = new MergePatchSetInput();
        input.inheritParent = false;
        input.merge = new MergeInput();
        input.merge.source = source;
        input.merge.sourceBranch = source;
        input.merge.allowConflicts = false;
        return input;
    }

    private static boolean hasSingleSelection(AnActionEvent e) {
        Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        return component instanceof TableView && ((TableView) component).getSelectedRowCount() == 1;
    }

    private static void reloadChanges(GerritToolWindow toolWindow, Project project) {
        if (toolWindow != null) {
            toolWindow.reloadChanges(project, false);
        }
    }

    private static class RefreshMergeDialog extends DialogWrapper {
        private final JTextField sourceBranchField = new JTextField();
        private final JTextField projectField;
        private final JTextField targetBranchField;

        RefreshMergeDialog(Project project, String gerritProject, String targetBranch) {
            super(project, true);
            projectField = new JTextField(gerritProject);
            targetBranchField = new JTextField(targetBranch);
            projectField.setEditable(false);
            targetBranchField.setEditable(false);
            setTitle("Refresh Feature Merge Patch Set");
            setOKButtonText("Refresh");
            init();
        }

        @Nullable
        @Override
        protected ValidationInfo doValidate() {
            return sourceBranchField.getText().trim().isEmpty()
                    ? new ValidationInfo("Source branch is required", sourceBranchField) : null;
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            addRow(panel, 0, "Gerrit project:", projectField);
            addRow(panel, 1, "Target branch:", targetBranchField);
            addRow(panel, 2, "Source branch:", sourceBranchField);
            return panel;
        }
    }

    private static void addRow(JPanel panel, int row, String label, JTextField field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(3, 0, 3, 8);
        panel.add(new JLabel(label), labelConstraints);
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(3, 0, 3, 0);
        field.setColumns(35);
        panel.add(field, fieldConstraints);
    }

}
