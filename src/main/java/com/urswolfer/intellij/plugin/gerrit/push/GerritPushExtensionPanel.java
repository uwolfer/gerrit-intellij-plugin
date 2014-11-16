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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author Urs Wolfer
 */
public class GerritPushExtensionPanel extends JPanel {

    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

    private JPanel indentedSettingPanel;

    private JCheckBox pushToGerritCheckBox;
    private JCheckBox draftChangeCheckBox;
    private JCheckBox submitChangeCheckBox;
    private JTextField branchTextField;
    private JTextField topicTextField;
    private JTextField reviewersTextField;
    private JTextField ccTextField;
    private GerritPushTargetPanel gerritPushTargetPanel;
    private String originalDestinationBranch;

    public GerritPushExtensionPanel(boolean pushToGerritByDefault) {
        createLayout();

        pushToGerritCheckBox.setSelected(pushToGerritByDefault);
        pushToGerritCheckBox.addActionListener(new SettingsStateActionListener());
        setSettingsEnabled(pushToGerritCheckBox.isSelected());

        addChangeListener();
    }

    public void registerGerritPushTargetPanel(GerritPushTargetPanel gerritPushTargetPanel, String branch) {
        if (branch != null) {
            branch = branch.replaceAll("^refs/(for|drafts)/", "");
            branch = branch.replaceAll("%.*$", "");
        }
        this.gerritPushTargetPanel = gerritPushTargetPanel;
        this.originalDestinationBranch = branch;

        branchTextField.setText(branch);
    }

    private void createLayout() {
        JPanel mainPanel = new JPanel();
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        pushToGerritCheckBox = new JCheckBox("Push to Gerrit");
        mainPanel.add(pushToGerritCheckBox);

        indentedSettingPanel = new JPanel(new GridLayoutManager(6, 2));

        draftChangeCheckBox = new JCheckBox("Draft-Change");
        draftChangeCheckBox.setToolTipText("Publish change as draft (reviewers cannot submit change).");
        indentedSettingPanel.add(draftChangeCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        submitChangeCheckBox = new JCheckBox("Submit Change");
        submitChangeCheckBox.setToolTipText("Changes can be directly submitted on push. This is primarily useful for " +
                "teams that don't want to do code review but want to use Gerrit’s submit strategies to handle " +
                "contention on busy branches. Using submit creates a change and submits it immediately, if the caller " +
                "has submit permission.");
        indentedSettingPanel.add(submitChangeCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        branchTextField = addTextField(
                "Branch:",
                "The push destination branch.",
                2);

        topicTextField = addTextField(
                "Topic:",
                "A short tag associated with all of the changes in the same group, such as the local topic branch name.",
                3);

        reviewersTextField = addTextField(
                "Reviewers (user names, comma separated):",
                "Users which will be added as reviewers.",
                4);

        ccTextField = addTextField(
                "CC (user names, comma separated):",
                "Users which will receive carbon copies of the notification message.",
                5);

        final JPanel settingLayoutPanel = new JPanel();
        settingLayoutPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingLayoutPanel.setLayout(new BoxLayout(settingLayoutPanel, BoxLayout.X_AXIS));
        settingLayoutPanel.add(Box.createRigidArea(new Dimension(20, 0)));
        settingLayoutPanel.add(indentedSettingPanel);

        mainPanel.add(settingLayoutPanel);

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(mainPanel);
        add(Box.createHorizontalGlue());
    }

    private JTextField addTextField(String label, String toolTipText, int row) {
        indentedSettingPanel.add(
                new JLabel(label),
                new GridConstraints(row, 0, 1, 1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null, null, null)
        );

        JTextField textField = new JTextField();
        textField.setToolTipText(toolTipText);
        indentedSettingPanel.add(
                textField,
                new GridConstraints(row, 1, 1, 1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        new Dimension(250, 0), null, null)
        );
        return textField;
    }

    private void addChangeListener() {
        ChangeActionListener gerritPushChangeListener = new ChangeActionListener();
        pushToGerritCheckBox.addActionListener(gerritPushChangeListener);
        draftChangeCheckBox.addActionListener(gerritPushChangeListener);
        submitChangeCheckBox.addActionListener(gerritPushChangeListener);

        ChangeTextActionListener gerritPushTextChangeListener = new ChangeTextActionListener();
        branchTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        topicTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        reviewersTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        ccTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
    }

    public String getRef() {
        String ref = "%s";
        if (pushToGerritCheckBox.isSelected()) {
            if (draftChangeCheckBox.isSelected()) {
                ref = "refs/drafts/";
            } else {
                ref = "refs/for/";
            }
            ref += branchTextField.getText();
            List<String> gerritSpecs = Lists.newArrayList();
            if (submitChangeCheckBox.isSelected()) {
                gerritSpecs.add("submit");
            }
            if (!topicTextField.getText().isEmpty()) {
                gerritSpecs.add("topic=" + topicTextField.getText());
            }
            handleCommaSeparatedUserNames(gerritSpecs, reviewersTextField, "r");
            handleCommaSeparatedUserNames(gerritSpecs, ccTextField, "cc");
            String gerritSpec = Joiner.on(',').join(gerritSpecs);
            if (!Strings.isNullOrEmpty(gerritSpec)) {
                ref += "%%" + gerritSpec;
            }
        }
        return ref;
    }

    private void handleCommaSeparatedUserNames(List<String> gerritSpecs, JTextField textField, String option) {
        Iterable<String> items = COMMA_SPLITTER.split(textField.getText());
        for (String item : items) {
            gerritSpecs.add(option + '=' + item);
        }
    }

    private void updateDestinationBranch() {
        gerritPushTargetPanel.updateBranch(String.format(getRef(), originalDestinationBranch));
    }

    private void setSettingsEnabled(boolean enabled) {
        UIUtil.setEnabled(indentedSettingPanel, enabled, true);
    }

    /**
     * Updates destination branch text field after every config change.
     */
    private class ChangeActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            updateDestinationBranch();
        }
    }

    /**
     * Updates destination branch text field after every text-field config change.
     */
    private class ChangeTextActionListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            handleChange();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            handleChange();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            handleChange();
        }

        private void handleChange() {
            updateDestinationBranch();
        }
    }

    /**
     * Activates or deactivates settings according to checkbox.
     */
    private class SettingsStateActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            setSettingsEnabled(pushToGerritCheckBox.isSelected());
        }
    }
}
