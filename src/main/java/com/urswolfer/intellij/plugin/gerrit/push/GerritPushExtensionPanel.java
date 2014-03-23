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

        indentedSettingPanel = new JPanel();
        indentedSettingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        indentedSettingPanel.setLayout(new BoxLayout(indentedSettingPanel, BoxLayout.Y_AXIS));

        draftChangeCheckBox = new JCheckBox("Draft-Change");
        indentedSettingPanel.add(draftChangeCheckBox);

        submitChangeCheckBox = new JCheckBox("Submit Change");
        indentedSettingPanel.add(submitChangeCheckBox);

        indentedSettingPanel.add(createTopicSetting());
        indentedSettingPanel.add(createReviewersSetting());

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

    private JPanel createTopicSetting() {
        JPanel formLayoutedPanel = createFormLayoutedPanel("Topic:");
        topicTextField = new JTextField();
        formLayoutedPanel.add(topicTextField);
        return formLayoutedPanel;
    }

    private JPanel createReviewersSetting() {
        JPanel formLayoutedPanel = createFormLayoutedPanel("Reviewers (Usernames comma-separated):");
        reviewersTextField = new JTextField();
        formLayoutedPanel.add(reviewersTextField);
        return formLayoutedPanel;
    }

    private JPanel createFormLayoutedPanel(String labelText) {
        JPanel formLayoutedPanel = new JPanel();
        formLayoutedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formLayoutedPanel.setLayout(new BoxLayout(formLayoutedPanel, BoxLayout.X_AXIS));
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(280, label.getHeight()));
        formLayoutedPanel.add(label);
        return formLayoutedPanel;
    }

    private void addChangeListener() {
        ChangeActionListener gerritPushChangeListener = new ChangeActionListener();
        pushToGerritCheckBox.addActionListener(gerritPushChangeListener);
        draftChangeCheckBox.addActionListener(gerritPushChangeListener);
        submitChangeCheckBox.addActionListener(gerritPushChangeListener);

        ChangeTextActionListener gerritPushTextChangeListener = new ChangeTextActionListener();
        topicTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        reviewersTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
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
