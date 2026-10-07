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
import com.intellij.openapi.progress.ProgressManager;
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
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GitilesUrls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
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

    private static final String STORED_PASSWORD_PLACEHOLDER = "************";

    private final Project project;
    @Nullable private final GerritAccount account;
    private final boolean showsStoredPassword;
    private final JBTextField hostTextField = new JBTextField();
    private final JBTextField loginTextField = new JBTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JBTextField cloneBaseUrlTextField = new JBTextField();
    private final JBTextField gitilesUrlTextField = new JBTextField();
    private final JButton testButton = new JButton("Test");

    private boolean passwordModified;

    /**
     * @param password what was entered for the account so far, or {@code null} for the stored one, which is only read
     *                 when Test needs it: reading can mean unlocking the OS keychain
     */
    public GerritAccountDialog(Project project, @Nullable GerritAccount account, @Nullable String password) {
        super(project, true);
        this.project = project;
        this.account = account;
        showsStoredPassword = password == null;

        hostTextField.getEmptyText().setText("https://review.example.org");
        cloneBaseUrlTextField.getEmptyText().setText("https://git.example.org");
        gitilesUrlTextField.getEmptyText().setText("<Gerrit URL>" + GitilesUrls.PLUGIN_PATH);
        if (account != null) {
            hostTextField.setText(account.host);
            loginTextField.setText(account.login);
            cloneBaseUrlTextField.setText(account.cloneBaseUrl);
            gitilesUrlTextField.setText(account.gitilesUrl);
        }
        passwordField.setText(password != null ? password
            : account != null && !account.login.isEmpty() ? STORED_PASSWORD_PLACEHOLDER : "");
        passwordModified = false;
        passwordField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                passwordModified = true;
            }
        });
        // The field holds a placeholder for the stored password while it is untouched; selecting it makes the first keystroke
        // replace it instead of being appended to it (and saved as the password). Deferred, because the mouse
        // press that gave the field focus moves the caret and would drop the selection.
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
        for (JBTextField urlField : new JBTextField[]{hostTextField, cloneBaseUrlTextField, gitilesUrlTextField}) {
            urlField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    SettingsPanel.fixUrl(urlField);
                }
            });
        }
        testButton.addActionListener(e -> testConnection());

        setTitle(account == null ? "Add Gerrit Account" : "Edit Gerrit Account");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        // the columns give the dialog its width: unwrapped, the text would stretch it across the screen
        JTextArea info = new JTextArea(LoginPanel.LOGIN_CREDENTIALS_INFO, 0, 60);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setMargin(new Insets(5, 0, 0, 0));
        info.setOpaque(false);
        info.setEditable(false);
        info.setFont(UIUtil.getLabelFont());

        JBLabel cloneBaseUrlHint = new JBLabel("Set only if it differs from the Gerrit web URL.");
        JBLabel gitilesUrlHint = new JBLabel("Set only if Gitiles is not served by the Gerrit plugin.");
        SettingsPanel.styleHints(cloneBaseUrlHint, gitilesUrlHint);

        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(label("Web URL:", 'W', hostTextField), hostTextField)
            .addLabeledComponent(label("Login:", 'L', loginTextField), loginTextField)
            .addLabeledComponent(label("Password:", 'P', passwordField), passwordField)
            .addComponentToRightColumn(testButton)
            .addLabeledComponent(label("Clone base URL:", 'U', cloneBaseUrlTextField), cloneBaseUrlTextField)
            .addComponentToRightColumn(cloneBaseUrlHint)
            .addLabeledComponent(label("Gitiles URL:", 'G', gitilesUrlTextField), gitilesUrlTextField)
            .addComponentToRightColumn(gitilesUrlHint)
            .addComponentFillVertically(info, 0)
            .getPanel();
        return panel;
    }

    /**
     * Bound to its field, so that the mnemonic moves there and the field is announced under the label's name.
     */
    private static JBLabel label(String text, char mnemonic, JComponent field) {
        JBLabel label = new JBLabel(text);
        label.setDisplayedMnemonic(mnemonic);
        label.setLabelFor(field);
        return label;
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
     * The focus listeners miss a value which is submitted without leaving its field.
     */
    @Override
    protected void doOKAction() {
        SettingsPanel.fixUrl(hostTextField);
        SettingsPanel.fixUrl(cloneBaseUrlTextField);
        SettingsPanel.fixUrl(gitilesUrlTextField);
        super.doOKAction();
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
        String password = showsStoredPassword && !passwordModified && account != null
            ? ProgressManager.getInstance().<String, RuntimeException>runProcessWithProgressSynchronously(
                () -> GerritAccounts.getInstance().getPassword(account), "Reading Gerrit Credentials", false, project)
            : getPassword();
        GerritAuthData.Basic authData = new GerritAuthData.Basic(host, getLogin(), password) {
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

    public String getGitilesUrl() {
        return gitilesUrlTextField.getText().trim();
    }

    public String getPassword() {
        return String.valueOf(passwordField.getPassword());
    }

    public boolean isPasswordModified() {
        return passwordModified;
    }
}
