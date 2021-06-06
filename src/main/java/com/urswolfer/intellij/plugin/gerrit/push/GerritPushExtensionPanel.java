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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Urs Wolfer
 */
public class GerritPushExtensionPanel extends JPanel {

    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
    private static final String GITREVIEW_FILENAME = ".gitreview";

    private final boolean pushToGerritByDefault;

    private JPanel indentedSettingPanel;

    private JCheckBox pushToGerritCheckBox;
    private JCheckBox privateCheckBox;
    private JCheckBox unmarkPrivateCheckBox;
    private JCheckBox publishDraftCommentsCheckBox;
    private JCheckBox wipCheckBox;
    private JCheckBox draftChangeCheckBox;
    private JCheckBox submitChangeCheckBox;
    private JTextField branchTextField;
    private JTextField topicTextField;
    private JTextField hashTagTextField;
    private JTextField reviewersTextField;
    private JTextField ccTextField;
    private Map<GerritPushTargetPanel, String> gerritPushTargetPanels = Maps.newHashMap();
    private boolean initialized = false;

    public GerritPushExtensionPanel(boolean pushToGerritByDefault) {
        this.pushToGerritByDefault = pushToGerritByDefault;
        createLayout();

        pushToGerritCheckBox.setSelected(pushToGerritByDefault);
        pushToGerritCheckBox.addActionListener(new SettingsStateActionListener());
        setSettingsEnabled(pushToGerritCheckBox.isSelected());

        addChangeListener();
    }

    public void registerGerritPushTargetPanel(GerritPushTargetPanel gerritPushTargetPanel, String branch) {
        if (initialized) { // a new dialog gets initialized; start again
            initialized = false;
            gerritPushTargetPanels.clear();
        }

        if (branch != null) {
            branch = branch.replaceAll("^refs/(for|drafts)/", "");
            branch = branch.replaceAll("%.*$", "");
        }

        gerritPushTargetPanels.put(gerritPushTargetPanel, branch);
    }

    public void initialized() {
        initialized = true;

        // force a deferred update (changes are monitored only after full construction of dialog)
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (gerritPushTargetPanels.size() == 1) {
                    String branchName = gerritPushTargetPanels.values().iterator().next();
                    Optional<String> gitReviewBranchName = getGitReviewBranchName();
                    branchTextField.setText(gitReviewBranchName.or(branchName));
                }
                initDestinationBranch();
            }
        });
    }

    private Optional<String> getGitReviewBranchName() {
        Optional<String> branchName = Optional.absent();

        DataContext dataContext = DataManager.getInstance().getDataContext(this);
        Optional<Project> openedProject = dataContext != null ?
            Optional.fromNullable(CommonDataKeys.PROJECT.getData(dataContext)) : Optional.<Project>absent();

        if (openedProject.isPresent()) {
            String gitReviewFilePath = Joiner.on(File.separator).join(
                openedProject.get().getBasePath(), GITREVIEW_FILENAME);

            File gitReviewFile = new File(gitReviewFilePath);
            if (gitReviewFile.exists() && gitReviewFile.isFile()) {
                FileInputStream fileInputStream = null;
                try {
                    fileInputStream = new FileInputStream(gitReviewFilePath);

                    Properties properties = new Properties();
                    properties.load(fileInputStream);
                    branchName = Optional.fromNullable(Strings.emptyToNull(properties.getProperty("defaultbranch")));
                } catch (IOException e) {
                    //no need to handle as branch name is already absent and ready to be returned
                } finally {
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e) {
                            //no need to handle as branch name is already absent and ready to be returned
                        }
                    }
                }
            }
        }

        return branchName;
    }

    private void createLayout() {
        JPanel mainPanel = new JPanel();
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        pushToGerritCheckBox = new JCheckBox("Push to Gerrit");
        mainPanel.add(pushToGerritCheckBox);

        indentedSettingPanel = new JPanel(new GridLayoutManager(12, 2));

        privateCheckBox = new JCheckBox("Private (Gerrit 2.15+)");
        privateCheckBox.setToolTipText("Push a private change or to turn a change private.");
        indentedSettingPanel.add(privateCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        unmarkPrivateCheckBox = new JCheckBox("Unmark Private (Gerrit 2.15+)");
        unmarkPrivateCheckBox.setToolTipText("Unmark an existing change private.");
        indentedSettingPanel.add(unmarkPrivateCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        wipCheckBox = new JCheckBox("WIP (Work-In-Progress Changes) (Gerrit 2.15+)");
        wipCheckBox.setToolTipText("Push a wip change or to turn a change to wip.");
        indentedSettingPanel.add(wipCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        publishDraftCommentsCheckBox = new JCheckBox("Publish Draft Comments (Gerrit 2.15+)");
        publishDraftCommentsCheckBox.setToolTipText("If you have draft comments on the change(s) that are updated by the push, the publish-comments option will cause them to be published.");
        indentedSettingPanel.add(publishDraftCommentsCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        draftChangeCheckBox = new JCheckBox("Draft-Change (Gerrit older than 2.15)");
        draftChangeCheckBox.setToolTipText("Publish change as draft (reviewers cannot submit change).");
        indentedSettingPanel.add(draftChangeCheckBox, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        submitChangeCheckBox = new JCheckBox("Submit Change");
        submitChangeCheckBox.setToolTipText("Changes can be directly submitted on push. This is primarily useful for " +
                "teams that don't want to do code review but want to use Gerrit’s submit strategies to handle " +
                "contention on busy branches. Using submit creates a change and submits it immediately, if the caller " +
                "has submit permission.");
        indentedSettingPanel.add(submitChangeCheckBox, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        branchTextField = addTextField(
                "Branch:",
                "The push destination branch.",
                7);

        topicTextField = addTextField(
                "Topic:",
                "A short topic associated with all of the changes in the same group, such as the local topic branch name.",
                8);

        hashTagTextField = addTextField(
                "Hashtag (Gerrit 2.15+):",
                "Include a hashtag associated with all of the changes in the same group.",
                9);

        reviewersTextField = addTextField(
                "Reviewers (user names, comma separated):",
                "Users which will be added as reviewers.",
                10);

        ccTextField = addTextField(
                "CC (user names, comma separated):",
                "Users which will receive carbon copies of the notification message.",
                11);

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
        privateCheckBox.addActionListener(gerritPushChangeListener);
        unmarkPrivateCheckBox.addActionListener(gerritPushChangeListener);
        wipCheckBox.addActionListener(gerritPushChangeListener);
        publishDraftCommentsCheckBox.addActionListener(gerritPushChangeListener);
        draftChangeCheckBox.addActionListener(gerritPushChangeListener);
        submitChangeCheckBox.addActionListener(gerritPushChangeListener);

        ChangeTextActionListener gerritPushTextChangeListener = new ChangeTextActionListener();
        branchTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        topicTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        hashTagTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        reviewersTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        ccTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
    }

    private String getRef() {
        String ref = "%s";
        if (pushToGerritCheckBox.isSelected()) {
            if (draftChangeCheckBox.isSelected()) {
                ref = "refs/drafts/";
            } else {
                ref = "refs/for/";
            }
            if (!branchTextField.getText().isEmpty()) {
                ref += branchTextField.getText();
            } else {
                ref += "%s";
            }
            List<String> gerritSpecs = Lists.newArrayList();
            if (privateCheckBox.isSelected()) {
                gerritSpecs.add("private");
            } else if (unmarkPrivateCheckBox.isSelected()) {
                gerritSpecs.add("remove-private");
            }
            if (wipCheckBox.isSelected()) {
                gerritSpecs.add("wip");
            }
            if (publishDraftCommentsCheckBox.isSelected()) {
                gerritSpecs.add("publish-comments");
            }
            if (submitChangeCheckBox.isSelected()) {
                gerritSpecs.add("submit");
            }
            if (!topicTextField.getText().isEmpty()) {
                gerritSpecs.add("topic=" + topicTextField.getText());
            }
            if (!hashTagTextField.getText().isEmpty()) {
                gerritSpecs.add("hashtag=" + hashTagTextField.getText());
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

    private void handlePrivateCheckBoxExclusive() {
        privateCheckBox.setEnabled(!unmarkPrivateCheckBox.isSelected());
        unmarkPrivateCheckBox.setEnabled(!privateCheckBox.isSelected());
    }

    private void handleCommaSeparatedUserNames(List<String> gerritSpecs, JTextField textField, String option) {
        Iterable<String> items = COMMA_SPLITTER.split(textField.getText());
        for (String item : items) {
            gerritSpecs.add(option + '=' + item);
        }
    }

    private void initDestinationBranch() {
        for (Map.Entry<GerritPushTargetPanel, String> entry : gerritPushTargetPanels.entrySet()) {
            entry.getKey().initBranch(String.format(getRef(), entry.getValue()), pushToGerritByDefault);
        }
    }

    private void updateDestinationBranch() {
        for (Map.Entry<GerritPushTargetPanel, String> entry : gerritPushTargetPanels.entrySet()) {
            entry.getKey().updateBranch(String.format(getRef(), entry.getValue()));
        }
    }

    private void setSettingsEnabled(boolean enabled) {
        UIUtil.setEnabled(indentedSettingPanel, enabled, true);
        if (enabled) {
            handlePrivateCheckBoxExclusive();
        }
    }

    /**
     * Updates destination branch text field after every config change.
     */
    private class ChangeActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            updateDestinationBranch();
            handlePrivateCheckBoxExclusive();
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
