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
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Urs Wolfer
 */
public class GerritPushExtensionPanel extends JPanel {

    private final boolean pushToGerritByDefault;
    private final JTextField destinationBranchTextField;
    private final JCheckBox manualPush;

    private JPanel indentedSettingPanel;

    private JCheckBox pushToGerritCheckBox;
    private JCheckBox draftChangeCheckBox;
    private JCheckBox submitChangeCheckBox;
    private JTextField topicTextField;
    private JTextField reviewersTextField;
    private JTextField ccTextField;

    private Optional<String> originalDestinationBranch = Optional.absent();

    public GerritPushExtensionPanel(boolean pushToGerritByDefault,
                                    JTextField destinationBranchTextField,
                                    JCheckBox manualPush) {
        this.pushToGerritByDefault = pushToGerritByDefault;
        this.destinationBranchTextField = destinationBranchTextField;
        this.manualPush = manualPush;

        destinationBranchTextField.getDocument().addDocumentListener(new LoadDestinationBranchListener());

        createLayout();

        pushToGerritCheckBox.addActionListener(new SettingsStateActionListener());
        addChangeListener();
    }

    private void createLayout() {
        JPanel mainPanel = new JPanel();
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        pushToGerritCheckBox = new JCheckBox("Push to Gerrit");
        mainPanel.add(pushToGerritCheckBox);

        indentedSettingPanel = new JPanel(new GridLayoutManager(5, 2));

        draftChangeCheckBox = new JCheckBox("Draft-Change");
        draftChangeCheckBox.setToolTipText("Publish change as draft (reviewers cannot submit change).");
        indentedSettingPanel.add(draftChangeCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        submitChangeCheckBox = new JCheckBox("Submit Change");
        submitChangeCheckBox.setToolTipText("Changes can be directly submitted on push. This is primarily useful for " +
                "teams that don't want to do code review but want to use Gerrit’s submit strategies to handle " +
                "contention on busy branches. Using submit creates a change and submits it immediately, if the caller " +
                "has submit permission.");
        indentedSettingPanel.add(submitChangeCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        indentedSettingPanel.add(new JLabel("Topic:"), new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        indentedSettingPanel.add(topicTextField = new JTextField(), new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        topicTextField.setToolTipText("A short tag associated with all of the changes in the same group, such as the " +
                "local topic branch name.");

        indentedSettingPanel.add(new JLabel("Reviewers (user names, comma separated):"), new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        indentedSettingPanel.add(reviewersTextField = new JTextField(), new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        reviewersTextField.setToolTipText("Users which will be addeds as reviewers.");

        indentedSettingPanel.add(new JLabel("CC (user names, comma separated):"), new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        indentedSettingPanel.add(ccTextField = new JTextField(), new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        ccTextField.setToolTipText("Users which will receive carbon copies of the notification message.");

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

    private void addChangeListener() {
        ChangeActionListener gerritPushChangeListener = new ChangeActionListener();
        pushToGerritCheckBox.addActionListener(gerritPushChangeListener);
        draftChangeCheckBox.addActionListener(gerritPushChangeListener);
        submitChangeCheckBox.addActionListener(gerritPushChangeListener);

        ChangeTextActionListener gerritPushTextChangeListener = new ChangeTextActionListener();
        topicTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        reviewersTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        ccTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
    }

    private String getRef() {
        String ref = "%s";
        if (pushToGerritCheckBox.isSelected()) {
            manualPush.setSelected(true);
            if (draftChangeCheckBox.isSelected()) {
                ref = "refs/drafts/" + ref;
            } else {
                ref = "refs/for/" + ref;
            }
            java.util.List<String> gerritSpecs = Lists.newArrayList();
            if (submitChangeCheckBox.isSelected()) {
                gerritSpecs.add("submit");
            }
            if (!topicTextField.getText().isEmpty()) {
                gerritSpecs.add("topic=" + topicTextField.getText());
            }
            if (!reviewersTextField.getText().isEmpty()) {
                gerritSpecs.add("r=" + reviewersTextField.getText());
            }
            if (!ccTextField.getText().isEmpty()) {
                gerritSpecs.add("cc=" + ccTextField.getText());
            }
            String gerritSpec = Joiner.on(',').join(gerritSpecs);
            if (!Strings.isNullOrEmpty(gerritSpec)) {
                ref += "%%" + gerritSpec;
            }
        } else {
            manualPush.setSelected(false);
        }
        return ref;
    }

    private void updateDestinationBranch() {
        destinationBranchTextField.setText(String.format(getRef(), originalDestinationBranch.get()));
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

    /**
     * Get initial destination branch (loaded async).
     */
    private class LoadDestinationBranchListener implements DocumentListener {
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
            if (!originalDestinationBranch.isPresent()) {
                originalDestinationBranch = Optional.of(destinationBranchTextField.getText());

                pushToGerritCheckBox.setSelected(pushToGerritByDefault);
                setSettingsEnabled(pushToGerritCheckBox.isSelected());

                ApplicationManager.getApplication().invokeLater(new UpdateDestinationBranchRunnable());
            }
        }
    }

    /**
     * Text field content cannot be updated in event handler.
     */
    private class UpdateDestinationBranchRunnable implements Runnable {
        @Override
        public void run() {
            updateDestinationBranch();
        }
    }
}
