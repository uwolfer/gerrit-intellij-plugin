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

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.intellij.openapi.progress.ProgressManager;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubSettingsPanel
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class SettingsPanel {
    private static final Logger LOG = Logger.getInstance(SettingsPanel.class);
    static final String DEFAULT_PASSWORD_TEXT = "************";

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

    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private final GerritUtil gerritUtil = GerritUtil.getInstance();

    private final Project project;

    private final CollectionComboBoxModel<GerritAccount> accountModel = new CollectionComboBoxModel<>();
    private final ComboBox<GerritAccount> accountComboBox = new ComboBox<>(accountModel);
    private final Map<String, String> editedPasswords = new HashMap<>();
    private final Set<String> removedAccountIds = new HashSet<>();
    private GerritAccount shownAccount;
    private JPanel wrapper;

    public SettingsPanel(Project project) {
        this.project = project;

        hostTextField.getEmptyText().setText("https://review.example.org");

        gerritLoginInfoTextField.setText(LoginPanel.LOGIN_CREDENTIALS_INFO);
        gerritLoginInfoTextField.setBackground(pane.getBackground());
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String password = isPasswordModified() ? getPassword() : passwordOfShownAccount();
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
        if (wrapper == null) {
            wrapper = new JPanel(new BorderLayout());
            wrapper.add(createAccountPane(), BorderLayout.NORTH);
            wrapper.add(pane, BorderLayout.CENTER);
        }
        return wrapper;
    }

    /**
     * The chooser says which account this project talks to, and the fields below it edit that account, so that the
     * page reads the same for the one account most installations have as it does for several.
     */
    private JComponent createAccountPane() {
        accountComboBox.setRenderer(SimpleListCellRenderer.create("", GerritAccount::toString));
        accountComboBox.addActionListener(e -> {
            GerritAccount selected = accountModel.getSelected();
            if (selected != shownAccount) {
                flushFieldsInto(shownAccount);
                stashPasswordOf(shownAccount);
                show(selected);
            }
        });

        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> {
            flushFieldsInto(shownAccount);
            stashPasswordOf(shownAccount);
            GerritAccount account = GerritAccount.create("", "", "");
            accountModel.add(account);
            accountModel.setSelectedItem(account);
            show(account);
        });

        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> {
            GerritAccount selected = accountModel.getSelected();
            if (selected == null) {
                return;
            }
            if (Messages.showYesNoDialog(pane, "Remove the account for " + selected + "?",
                "Remove Gerrit Account", Messages.getQuestionIcon()) != Messages.YES) {
                return;
            }
            removedAccountIds.add(selected.id);
            editedPasswords.remove(selected.id);
            accountModel.remove(selected);
            GerritAccount next = accountModel.getSize() > 0 ? accountModel.getElementAt(0) : null;
            accountModel.setSelectedItem(next);
            show(next);
        });

        JPanel accountPane = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, 0));
        accountPane.setBorder(IdeBorderFactory.createTitledBorder("Account for this project"));
        accountPane.add(accountComboBox, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, 0));
        buttons.add(addButton, BorderLayout.WEST);
        buttons.add(removeButton, BorderLayout.EAST);
        accountPane.add(buttons, BorderLayout.EAST);
        return accountPane;
    }

    /**
     * Reading blocks on the credential store, so the Test button asks for the stored password behind a modal
     * progress rather than on the event dispatch thread.
     */
    private String passwordOfShownAccount() {
        if (shownAccount == null) {
            return "";
        }
        GerritAccount account = shownAccount;
        return ProgressManager.getInstance().<String, RuntimeException>runProcessWithProgressSynchronously(
            () -> GerritAccounts.getInstance().getPassword(account), "Reading Gerrit Credentials", false, project);
    }

    private void flushFieldsInto(GerritAccount account) {
        if (account == null) {
            return;
        }
        account.host = getHost();
        account.login = getLogin();
        account.cloneBaseUrl = getCloneBaseUrl();
    }

    /**
     * Kept apart from the fields: the dialog asks whether anything changed on a timer, and remembering a typed
     * password as a side effect of being asked would leave it remembered after it was taken back again.
     */
    private void stashPasswordOf(GerritAccount account) {
        if (account != null && isPasswordModified()) {
            editedPasswords.put(account.id, getPassword());
        }
    }

    private void show(GerritAccount account) {
        shownAccount = account;
        hostTextField.setText(account != null ? account.host : "");
        loginTextField.setText(account != null ? account.login : "");
        cloneBaseUrlTextField.setText(account != null ? account.cloneBaseUrl : "");
        String edited = account != null ? editedPasswords.get(account.id) : null;
        if (edited != null) {
            setPassword(edited);
            passwordModified = true;
        } else {
            setPassword(account != null && !account.login.isEmpty() ? DEFAULT_PASSWORD_TEXT : "");
            resetPasswordModification();
        }
    }

    /**
     * There is no account to type into on a fresh install. Rather than making the account first and entering its
     * details second, the fields make the account as soon as anything is entered in them.
     *
     * Called only while applying: growing an account as a side effect of being asked whether anything changed is
     * how the dialog ends up storing things nobody asked it to store.
     */
    public void materializeTypedAccount() {
        if (shownAccount != null || !hasTypedFieldsWithoutAccount()) {
            return;
        }
        GerritAccount account = GerritAccount.create("", "", "");
        shownAccount = account; // set first, so selecting it does not make the listener swap the fields out
        accountModel.add(account);
        accountModel.setSelectedItem(account);
    }

    public boolean hasTypedFieldsWithoutAccount() {
        return shownAccount == null
            && (!getHost().isEmpty() || !getLogin().isEmpty() || !getCloneBaseUrl().isEmpty() || isPasswordModified());
    }

    /**
     * @param accounts copies the dialog may edit freely; nothing is stored before the settings are applied
     */
    public void setAccounts(List<GerritAccount> accounts, GerritAccount selected) {
        editedPasswords.clear();
        removedAccountIds.clear();
        accountModel.replaceAll(accounts);
        accountModel.setSelectedItem(selected);
        show(selected);
    }

    public List<GerritAccount> getAccounts() {
        flushFieldsInto(shownAccount);
        return new ArrayList<>(accountModel.getItems());
    }

    public Set<String> getRemovedAccountIds() {
        return removedAccountIds;
    }

    /**
     * Only for applying the settings: it takes the password currently typed as edited.
     */
    public Map<String, String> collectEditedPasswords() {
        stashPasswordOf(shownAccount);
        return editedPasswords;
    }

    public boolean hasEditedPasswords() {
        return isPasswordModified() || !editedPasswords.isEmpty();
    }

    public GerritAccount getSelectedAccount() {
        flushFieldsInto(shownAccount);
        return accountModel.getSelected();
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

