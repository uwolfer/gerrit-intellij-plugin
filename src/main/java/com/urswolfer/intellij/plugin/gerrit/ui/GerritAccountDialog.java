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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextPane;
import javax.swing.event.DocumentEvent;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Edits one Gerrit account. The settings page lists the accounts and leaves their details to this dialog, the way
 * the platform's own account pages do: the list says which accounts there are and which one a project uses, and
 * nothing on it changes just because a row was selected.
 *
 * @author Urs Wolfer
 */
public class GerritAccountDialog extends DialogWrapper {

    private static final Logger LOG = Logger.getInstance(GerritAccountDialog.class);

    private final Project project;
    private final JBTextField hostTextField = new JBTextField();
    private final JBTextField loginTextField = new JBTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JBTextField cloneBaseUrlTextField = new JBTextField();
    private final JButton testButton = new JButton("Test");

    private boolean passwordModified;

    public GerritAccountDialog(Project project, @Nullable GerritAccount account, String password) {
        super(project, true);
        this.project = project;

        hostTextField.getEmptyText().setText("https://review.example.org");
        if (account != null) {
            hostTextField.setText(account.host);
            loginTextField.setText(account.login);
            cloneBaseUrlTextField.setText(account.cloneBaseUrl);
        }
        passwordField.setText(password);
        passwordModified = false;
        passwordField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                passwordModified = true;
            }
        });
        hostTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                SettingsPanel.fixUrl(hostTextField);
            }
        });
        testButton.addActionListener(e -> testConnection());

        setTitle(account == null ? "Add Gerrit Account" : "Edit Gerrit Account");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JTextPane info = new JTextPane();
        info.setText(LoginPanel.LOGIN_CREDENTIALS_INFO);
        info.setMargin(new Insets(5, 0, 0, 0));
        info.setBackground(UIUtil.TRANSPARENT_COLOR);
        info.setEditable(false);

        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(new JBLabel("URL:"), hostTextField)
            .addLabeledComponent(new JBLabel("Login:"), loginTextField)
            .addLabeledComponent(new JBLabel("Password:"), passwordField)
            .addLabeledComponent(new JBLabel("Clone base URL:"), cloneBaseUrlTextField)
            .addComponentToRightColumn(new JBLabel("Set a clone base URL if it differs from the Gerrit web URL."))
            .addComponentToRightColumn(testButton)
            .addComponentFillVertically(info, 0)
            .getPanel();
        panel.setPreferredSize(UIUtil.PANEL_REGULAR_INSETS != null ? panel.getPreferredSize() : null);
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return hostTextField.getText().isEmpty() ? hostTextField : loginTextField;
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (getHost().isEmpty()) {
            return new ValidationInfo("Enter the Gerrit URL.", hostTextField);
        }
        return null;
    }

    /**
     * Tests what is in the fields rather than what is stored: the point is to find out whether these credentials
     * work before they are saved.
     */
    private void testConnection() {
        SettingsPanel.fixUrl(hostTextField);
        String host = getHost();
        if (host.isEmpty()) {
            Messages.showErrorDialog(getContentPanel(), "Required field URL not specified", "Test Failure");
            return;
        }
        GerritAuthData.Basic authData = new GerritAuthData.Basic(host, getLogin(), getPassword()) {
            @Override
            public boolean isLoginAndPasswordAvailable() {
                return !getLogin().isEmpty();
            }
        };
        GerritUtil gerritUtil = GerritUtil.getInstance();
        try {
            if (gerritUtil.checkCredentials(project, authData)) {
                Messages.showInfoMessage(getContentPanel(), "Connection successful", "Success");
            } else {
                Messages.showErrorDialog(getContentPanel(),
                    "Can't login to " + host + " using given credentials", "Login Failure");
            }
        } catch (Exception e) {
            LOG.info(e);
            Messages.showErrorDialog(getContentPanel(),
                String.format("Can't login to %s: %s", host, gerritUtil.getErrorTextFromException(e)), "Login Failure");
        }
    }

    public String getHost() {
        return hostTextField.getText().trim();
    }

    public String getLogin() {
        return loginTextField.getText().trim();
    }

    public String getCloneBaseUrl() {
        return cloneBaseUrlTextField.getText().trim();
    }

    public String getPassword() {
        return String.valueOf(passwordField.getPassword());
    }

    public boolean isPasswordModified() {
        return passwordModified;
    }
}
