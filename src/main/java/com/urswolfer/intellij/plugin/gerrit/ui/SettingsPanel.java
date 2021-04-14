/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Copyright 2013 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBTextField;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubSettingsPanel
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class SettingsPanel {
    private JTextField loginTextField;
    private JPasswordField passwordField;
    private JTextPane gerritLoginInfoTextField;
    private JPanel loginPane;
    private JButton testButton;
    private JBTextField hostTextField;
    private JSpinner refreshTimeoutSpinner;
    private JPanel settingsPane;
    private JPanel pane;
    private JCheckBox notificationOnNewReviewsCheckbox;
    private JCheckBox automaticRefreshCheckbox;
    private JCheckBox listAllChangesCheckbox;
    private JCheckBox pushToGerritCheckbox;
    private JCheckBox showChangeNumberColumnCheckBox;
    private JCheckBox showChangeIdColumnCheckBox;
    private JCheckBox showTopicColumnCheckBox;
    private JComboBox showProjectColumnComboBox;
    private JTextField cloneBaseUrlTextField;

    private boolean passwordModified;

    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private Logger log;

    public SettingsPanel() {
        hostTextField.getEmptyText().setText("https://review.example.org");

        gerritLoginInfoTextField.setText(LoginPanel.LOGIN_CREDENTIALS_INFO);
        gerritLoginInfoTextField.setBackground(pane.getBackground());
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String password = isPasswordModified() ? getPassword() : gerritSettings.getPassword();
                String host = getHost();
                if (Strings.isNullOrEmpty(host)) {
                    Messages.showErrorDialog(pane, "Required field URL not specified", "Test Failure");
                    return;
                }
                try {
                    GerritAuthData.Basic gerritAuthData = new GerritAuthData.Basic(host, getLogin(), password) {
                        @Override
                        public boolean isLoginAndPasswordAvailable() {
                            return !Strings.isNullOrEmpty(getLogin());
                        }
                    };
                    if (gerritUtil.checkCredentials(ProjectManager.getInstance().getDefaultProject(), gerritAuthData)) {
                        Messages.showInfoMessage(pane, "Connection successful", "Success");
                    } else {
                        Messages.showErrorDialog(pane, "Can't login to " + host + " using given credentials", "Login Failure");
                    }
                } catch (Exception ex) {
                    log.info(ex);
                    Messages.showErrorDialog(pane, String.format("Can't login to %s: %s", host, gerritUtil.getErrorTextFromException(ex)),
                            "Login Failure");
                }
                setPassword(password);
            }
        });

        hostTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                fixUrl(hostTextField);
            }
        });

        passwordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                passwordModified = true;
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                passwordModified = true;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                passwordModified = true;
            }
        });

        automaticRefreshCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAutomaticRefresh();
            }
        });

        showProjectColumnComboBox.setModel(new EnumComboBoxModel(ShowProjectColumn.class));

        cloneBaseUrlTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                fixUrl(cloneBaseUrlTextField);
            }
        });
    }

    public static void fixUrl(JTextField textField) {
        String text = textField.getText();
        if (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (!text.isEmpty() && !text.contains("://")) {
            text = "http://" + text;
        }
        textField.setText(text);
    }

    private void updateAutomaticRefresh() {
        GuiUtils.enableChildren(refreshTimeoutSpinner, automaticRefreshCheckbox.isSelected());
    }

    public JComponent getPanel() {
        return pane;
    }

    public void setLogin(final String login) {
        loginTextField.setText(login);
    }

    public void setPassword(final String password) {
        // Show password as blank if password is empty
        passwordField.setText(StringUtil.isEmpty(password) ? null : password);
    }

    public String getLogin() {
        return loginTextField.getText().trim();
    }

    public String getPassword() {
        return String.valueOf(passwordField.getPassword());
    }

    public void setHost(final String host) {
        hostTextField.setText(host);
    }

    public String getHost() {
        return hostTextField.getText().trim();
    }

    public boolean getListAllChanges() {
        return listAllChangesCheckbox.isSelected();
    }

    public void setListAllChanges(boolean listAllChanges) {
        listAllChangesCheckbox.setSelected(listAllChanges);
    }

    public void setAutomaticRefresh(final boolean automaticRefresh) {
        automaticRefreshCheckbox.setSelected(automaticRefresh);
        updateAutomaticRefresh();
    }

    public boolean getAutomaticRefresh() {
        return automaticRefreshCheckbox.isSelected();
    }

    public void setRefreshTimeout(final int refreshTimeout) {
        refreshTimeoutSpinner.setValue(refreshTimeout);
    }

    public int getRefreshTimeout() {
        return (Integer) refreshTimeoutSpinner.getValue();
    }

    public void setReviewNotifications(final boolean reviewNotifications) {
        notificationOnNewReviewsCheckbox.setSelected(reviewNotifications);
    }

    public boolean getReviewNotifications() {
        return notificationOnNewReviewsCheckbox.isSelected();
    }

    public void setPushToGerrit(final boolean pushToGerrit) {
        pushToGerritCheckbox.setSelected(pushToGerrit);
    }

    public boolean getPushToGerrit() {
        return pushToGerritCheckbox.isSelected();
    }

    public boolean getShowChangeNumberColumn() {
        return showChangeNumberColumnCheckBox.isSelected();
    }

    public void setShowChangeNumberColumn(final boolean showChangeNumberColumn) {
        showChangeNumberColumnCheckBox.setSelected(showChangeNumberColumn);
    }

    public boolean getShowChangeIdColumn() {
        return showChangeIdColumnCheckBox.isSelected();
    }

    public void setShowChangeIdColumn(final boolean showChangeIdColumn) {
        showChangeIdColumnCheckBox.setSelected(showChangeIdColumn);
    }

    public boolean getShowTopicColumn() {
        return showTopicColumnCheckBox.isSelected();
    }

    public void setShowTopicColumn(final boolean showTopicColumn) {
        showTopicColumnCheckBox.setSelected(showTopicColumn);
    }

    public ShowProjectColumn getShowProjectColumn() {
        return (ShowProjectColumn) showProjectColumnComboBox.getModel().getSelectedItem();
    }

    public void setShowProjectColumn(ShowProjectColumn showProjectColumn) {
        showProjectColumnComboBox.getModel().setSelectedItem(showProjectColumn);
    }

    public boolean isPasswordModified() {
        return passwordModified;
    }

    public void resetPasswordModification() {
        passwordModified = false;
    }

    public void setCloneBaseUrl(final String cloneBaseUrl) {
        cloneBaseUrlTextField.setText(cloneBaseUrl);
    }

    public String getCloneBaseUrl() {
        return cloneBaseUrlTextField.getText().trim();
    }

}

