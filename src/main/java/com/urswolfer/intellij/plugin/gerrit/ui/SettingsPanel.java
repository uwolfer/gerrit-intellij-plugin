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

import com.google.inject.Inject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.urswolfer.intellij.plugin.gerrit.GerritAuthData;
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
    private JTextField myLoginTextField;
    private JPasswordField myPasswordField;
    private JTextPane myGerritLoginInfoTextField;
    private JPanel myLoginPane;
    private JButton myTestButton;
    private JTextField myHostTextField;
    private JSpinner myRefreshTimeoutSpinner;
    private JPanel mySettingsPane;
    private JPanel myPane;
    private JCheckBox myNotificationOnNewReviewsCheckbox;
    private JCheckBox myAutomaticRefreshCheckbox;

    private boolean myPasswordModified;

    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private Logger log;

    public SettingsPanel() {
        myGerritLoginInfoTextField.setText(
                "* You need to set a HTTP access password for your account in Gerrit " +
                "(Settings > HTTP Password). <strong>If</strong> you have <strong>login issues</strong>, please try your " +
                "HTTP or LDAP Gerrit login data (when available).");
        myGerritLoginInfoTextField.setBackground(myPane.getBackground());
        myTestButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String password = isPasswordModified() ? getPassword() : gerritSettings.getPassword();
                try {
                    GerritAuthData.TempGerritAuthData gerritAuthData = new GerritAuthData.TempGerritAuthData(getHost(), getLogin(), password);
                    if (gerritUtil.checkCredentials(ProjectManager.getInstance().getDefaultProject(), gerritAuthData)) {
                        Messages.showInfoMessage(myPane, "Connection successful", "Success");
                    } else {
                        Messages.showErrorDialog(myPane, "Can't login to " + getHost() + " using given credentials", "Login Failure");
                    }
                } catch (Exception ex) {
                    log.info(ex);
                    Messages.showErrorDialog(myPane, String.format("Can't login to %s: %s", getHost(), gerritUtil.getErrorTextFromException(ex)),
                            "Login Failure");
                }
                setPassword(password);
            }
        });

        myHostTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String text = myHostTextField.getText();
                if (text.endsWith("/")) {
                    myHostTextField.setText(text.substring(0, text.length() - 1));
                }
            }
        });

        myPasswordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                myPasswordModified = true;
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                myPasswordModified = true;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                myPasswordModified = true;
            }
        });

        myAutomaticRefreshCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAutomaticRefresh();
            }
        });
    }

    private void updateAutomaticRefresh() {
        GuiUtils.enableChildren(myRefreshTimeoutSpinner, myAutomaticRefreshCheckbox.isSelected());
    }

    public JComponent getPanel() {
        return myPane;
    }

    public void setLogin(final String login) {
        myLoginTextField.setText(login);
    }

    public void setPassword(final String password) {
        // Show password as blank if password is empty
        myPasswordField.setText(StringUtil.isEmpty(password) ? null : password);
    }

    public String getLogin() {
        return myLoginTextField.getText().trim();
    }

    public String getPassword() {
        return String.valueOf(myPasswordField.getPassword());
    }

    public void setHost(final String host) {
        myHostTextField.setText(host);
    }

    public String getHost() {
        return myHostTextField.getText().trim();
    }

    public void setAutomaticRefresh(final boolean automaticRefresh) {
        myAutomaticRefreshCheckbox.setSelected(automaticRefresh);
        updateAutomaticRefresh();
    }

    public boolean getAutomaticRefresh() {
        return myAutomaticRefreshCheckbox.isSelected();
    }

    public void setRefreshTimeout(final int refreshTimeout) {
        myRefreshTimeoutSpinner.setValue(refreshTimeout);
    }

    public int getRefreshTimeout() {
        return (Integer) myRefreshTimeoutSpinner.getValue();
    }

    public void setReviewNotifications(final boolean reviewNotifications) {
        myNotificationOnNewReviewsCheckbox.setSelected(reviewNotifications);
    }

    public boolean getReviewNotifications() {
        return myNotificationOnNewReviewsCheckbox.isSelected();
    }

    public boolean isPasswordModified() {
        return myPasswordModified;
    }

    public void resetPasswordModification() {
        myPasswordModified = false;
    }
}

