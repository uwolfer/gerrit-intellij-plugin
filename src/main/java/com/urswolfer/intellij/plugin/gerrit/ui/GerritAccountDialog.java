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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.HttpStatusException;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GitilesUrls;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLException;
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
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final Exception CANCELLED = new Exception("The check was cancelled.");

    private final Project project;
    @Nullable private final GerritAccount account;
    private final Collection<GerritAccount> otherAccounts;
    private final boolean showsStoredPassword;
    private final JBTextField hostTextField = new JBTextField();
    private final JBTextField loginTextField = new JBTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JBTextField cloneBaseUrlTextField = new JBTextField();
    private final JBTextField gitilesUrlTextField = new JBTextField();
    private final JButton testButton = new JButton("Test");

    private boolean passwordModified;
    /** What a check accepted last, so that OK after a successful Test does not ask Gerrit again. */
    @Nullable private String acceptedCredentials;

    /**
     * @param password what was entered for the account so far, or {@code null} for the stored one, which is only read
     *                 when Test needs it: reading can mean unlocking the OS keychain
     * @param otherAccounts the accounts set up besides this one, which it must not repeat
     */
    public GerritAccountDialog(Project project, @Nullable GerritAccount account, @Nullable String password,
                               Collection<GerritAccount> otherAccounts) {
        super(project, true);
        this.project = project;
        this.account = account;
        this.otherAccounts = otherAccounts;
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
        // an account which is a duplicate already, such as from an earlier version, stays editable
        boolean sameAsBefore = account != null && account.isSameAs(getHost(), getLogin());
        for (GerritAccount other : sameAsBefore ? Collections.<GerritAccount>emptyList() : otherAccounts) {
            if (other.isSameAs(getHost(), getLogin())) {
                return new ValidationInfo("This login is already set up for this Gerrit.", loginTextField);
            }
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
        if (credentialsChanged() && !confirmLogin()) {
            return;
        }
        super.doOKAction();
    }

    /**
     * Another url or login of an account, such as the clone base url, is no reason to ask Gerrit again.
     */
    private boolean credentialsChanged() {
        // the stored url compared as the field shows it, which an earlier version did not always store that way
        return account == null || passwordModified
            || !getHost().equalsIgnoreCase(UrlUtils.normalizeTypedUrl(account.host))
            || !getLogin().equals(account.login);
    }

    /**
     * Credentials which do not work are saved only when someone says so: git and the REST api would keep sending
     * them. Saving is still offered, as a refusal can come from a proxy or single sign-on in front of Gerrit, and a
     * Gerrit which cannot be reached may just be down or out of reach for now.
     */
    private boolean confirmLogin() {
        Exception failure = login();
        if (failure == null) {
            return true;
        }
        if (failure == CANCELLED) {
            return false;
        }
        int status = httpStatus(failure);
        String message = (status == 401 || status == 403) && getLogin().isEmpty()
            ? String.format("%s needs a login.", getHost())
            : status == 401 || status == 403
            ? String.format("%s did not accept this login and password.", getHost())
            : isNetworkFailure(failure)
            ? String.format("Can't reach %s: %s", getHost(), reason(failure))
            // it answered, so the url or the server is wrong rather than the network: a login page, for one
            : String.format("%s answered unexpectedly: %s", getHost(), reason(failure));
        // Cancel is the default: Enter must not save credentials which just failed
        int answer = Messages.showDialog(getContentPanel(), message + "\n\nSave the account anyway?", "Login Failure",
            new String[]{"Save Anyway", Messages.getCancelButton()}, 1, Messages.getWarningIcon());
        return answer == 0;
    }

    /**
     * The REST client wraps what went wrong, such as a refused connection, in a "Request failed". The innermost
     * cause says more, with its name, as an UnknownHostException has no more than the host as its message; its
     * first line only, as one about an unexpected answer carries the whole page.
     */
    private static String reason(Throwable failure) {
        Throwable innermost = failure;
        while (innermost.getCause() != null) {
            innermost = innermost.getCause();
        }
        String message = innermost.getMessage();
        String reason = innermost.getClass().getSimpleName() + (message != null ? ": " + message.trim() : "");
        int lineEnd = reason.indexOf('\n');
        reason = lineEnd >= 0 ? reason.substring(0, lineEnd) : reason;
        return reason.length() > 200 ? reason.substring(0, 200) + "…" : reason;
    }

    /**
     * Not any IOException: the REST client also throws one for an answer it cannot parse.
     */
    private static boolean isNetworkFailure(Throwable failure) {
        return ExceptionUtil.findCause(failure, SocketException.class) != null
            || ExceptionUtil.findCause(failure, UnknownHostException.class) != null
            || ExceptionUtil.findCause(failure, InterruptedIOException.class) != null // timeouts
            || ExceptionUtil.findCause(failure, SSLException.class) != null;
    }

    /**
     * @return the status Gerrit answered with, or -1 where it did not answer
     */
    private static int httpStatus(Throwable failure) {
        HttpStatusException answer = ExceptionUtil.findCause(failure, HttpStatusException.class);
        return answer != null ? answer.getStatusCode() : -1;
    }

    private void testConnection() {
        SettingsPanel.fixUrl(hostTextField);
        String host = getHost();
        if (host.isEmpty()) {
            Messages.showErrorDialog(getContentPanel(), "Required field URL not specified", "Test Failure");
            return;
        }
        Exception failure = login();
        if (failure == null) {
            Messages.showInfoMessage(getContentPanel(), "Connection successful", "Success");
        } else if (failure != CANCELLED) {
            Messages.showErrorDialog(getContentPanel(),
                String.format("Can't login to %s: %s", host, reason(failure)), "Login Failure");
        }
    }

    /**
     * Tries what is in the fields rather than what is stored: the point is to find out whether these credentials
     * work before they are saved.
     *
     * @return why Gerrit refused them or could not be asked, {@code null} when it accepted them
     */
    @Nullable
    private Exception login() {
        boolean storedPassword = showsStoredPassword && !passwordModified && account != null;
        // compared before the stored password is read, which can mean unlocking the OS keychain
        // a marker no typed password can be, rather than the stored one
        String credentials = getHost() + '\n' + getLogin() + '\n' + (storedPassword ? "\0" : "\1" + getPassword());
        if (credentials.equals(acceptedCredentials)) {
            return null;
        }
        String password = storedPassword
            ? ProgressManager.getInstance().<String, RuntimeException>runProcessWithProgressSynchronously(
                () -> GerritAccounts.getInstance().getPassword(account), "Reading Gerrit Credentials", false, project)
            : getPassword();
        GerritAuthData.Basic authData = new GerritAuthData.Basic(getHost(), getLogin(), password) {
            @Override
            public boolean isLoginAndPasswordAvailable() {
                return !getLogin().isEmpty();
            }
        };
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        // a task of its own, rather than GerritUtil's, to hear about Cancel. The request does not look at the
        // indicator and runs on until it times out, so it goes to a thread of its own, and only the wait for it ends
        ProgressManager.getInstance().run(new Task.Modal(project, "Trying to Log In to Gerrit", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Future<Exception> request = ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        GerritUtil.getInstance().testConnection(authData);
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                });
                failure.set(ProgressIndicatorUtils.awaitWithCheckCanceled(request, indicator));
            }

            @Override
            public void onCancel() {
                cancelled.set(true);
            }
        });
        if (cancelled.get()) {
            return CANCELLED;
        }
        if (failure.get() == null) {
            acceptedCredentials = credentials;
        } else {
            LOG.info(failure.get());
        }
        return failure.get();
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
