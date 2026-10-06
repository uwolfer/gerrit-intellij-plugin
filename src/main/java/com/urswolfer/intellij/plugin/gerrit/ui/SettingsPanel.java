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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBTextField;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GitilesUrls;

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
    private static final Logger LOG = Logger.getInstance(SettingsPanel.class);
    private static final int MIN_REFRESH_TIMEOUT = 1;
    private static final int MAX_REFRESH_TIMEOUT = 24 * 60;

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
    private JBTextField gitilesUrlTextField;

    private boolean passwordModified;
    private boolean updatingPassword;

    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private final GerritUtil gerritUtil = GerritUtil.getInstance();

    private final Project project;

    public SettingsPanel(Project project) {
        this.project = project;

        hostTextField.getEmptyText().setText("https://review.example.org");

        gerritLoginInfoTextField.setText(LoginPanel.LOGIN_CREDENTIALS_INFO);
        gerritLoginInfoTextField.setBackground(pane.getBackground());
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String password = isPasswordModified() ? getPassword()
                    : gerritSettings.getPasswordWithModalProgress(project);
                fixUrl(hostTextField);
                String host = getHost();
                if (host == null || host.isEmpty()) {
                    Messages.showErrorDialog(pane, "Required field URL not specified", "Test Failure");
                    return;
                }
                try {
                    GerritAuthData.Basic gerritAuthData = new GerritAuthData.Basic(host, getLogin(), password) {
                        @Override
                        public boolean isLoginAndPasswordAvailable() {
                            String login = getLogin();
                            return login != null && !login.isEmpty();
                        }
                    };
                    if (gerritUtil.checkCredentials(project, gerritAuthData)) {
                        Messages.showInfoMessage(pane, "Connection successful", "Success");
                    } else {
                        Messages.showErrorDialog(pane, "Can't login to " + host + " using given credentials", "Login Failure");
                    }
                } catch (Exception ex) {
                    LOG.info(ex);
                    Messages.showErrorDialog(pane, String.format("Can't login to %s: %s", host, gerritUtil.getErrorTextFromException(ex)),
                            "Login Failure");
                }
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
                onPasswordEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onPasswordEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onPasswordEdited();
            }
        });
        // The field holds a placeholder while the stored password is untouched; selecting it makes
        // the first keystroke replace it instead of being appended to it (and saved as the password).
        // Deferred, because the mouse press that gave the field focus moves the caret and would
        // drop the selection.
        passwordField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!passwordModified) {
                    SwingUtilities.invokeLater(() -> {
                        if (!passwordModified) {
                            passwordField.selectAll();
                        }
                    });
                }
            }
        });

        automaticRefreshCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAutomaticRefresh();
            }
        });

        // A timeout of 0 or less silently stops the automatic refresh.
        refreshTimeoutSpinner.setModel(new SpinnerNumberModel(MIN_REFRESH_TIMEOUT, MIN_REFRESH_TIMEOUT, MAX_REFRESH_TIMEOUT, 1));

        showProjectColumnComboBox.setModel(new EnumComboBoxModel(ShowProjectColumn.class));

        cloneBaseUrlTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                fixUrl(cloneBaseUrlTextField);
            }
        });

        gitilesUrlTextField.getEmptyText().setText("<Gerrit URL>" + GitilesUrls.PLUGIN_PATH);
        gitilesUrlTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                fixUrl(gitilesUrlTextField);
            }
        });
    }

    /**
     * Applies {@link #fixUrl} to every URL field; the focus listeners miss a value that is
     * submitted without leaving its field.
     */
    public void normalizeUrls() {
        fixUrl(hostTextField);
        fixUrl(cloneBaseUrlTextField);
        fixUrl(gitilesUrlTextField);
    }

    public static void fixUrl(JTextField textField) {
        String text = textField.getText().trim();
        if (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (!text.isEmpty() && !text.contains("://")) {
            text = "https://" + text;
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
        updatingPassword = true;
        try {
            // Show password as blank if password is empty
            passwordField.setText(StringUtil.isEmpty(password) ? null : password);
        } finally {
            updatingPassword = false;
        }
    }

    private void onPasswordEdited() {
        if (!updatingPassword) {
            passwordModified = true;
        }
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
        // Settings saved before the spinner was bounded may hold a value it would not accept.
        refreshTimeoutSpinner.setValue(Math.max(MIN_REFRESH_TIMEOUT, Math.min(MAX_REFRESH_TIMEOUT, refreshTimeout)));
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

    public void setGitilesUrl(final String gitilesUrl) {
        gitilesUrlTextField.setText(gitilesUrl);
    }

    public String getGitilesUrl() {
        return gitilesUrlTextField.getText().trim();
    }

}

