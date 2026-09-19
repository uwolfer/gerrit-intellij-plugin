/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubSettingsPanel
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class SettingsPanel {

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

    private final Project project;

    private final CollectionListModel<GerritAccount> accountModel = new CollectionListModel<>();
    private final JBList<GerritAccount> accountList = new JBList<>(accountModel);
    private final Map<String, String> editedPasswords = new HashMap<>();
    private final Set<String> removedAccountIds = new HashSet<>();
    private GerritAccount projectAccount;
    private JPanel wrapper;

    public SettingsPanel(Project project) {
        this.project = project;

        automaticRefreshCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAutomaticRefresh();
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
     * The accounts are a list rather than a chooser over one set of fields, and their details live in a dialog.
     * Selecting a row then says nothing about which account the project uses, so looking at an account cannot
     * change what the project talks to.
     */
    private JComponent createAccountPane() {
        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setCellRenderer(new ColoredListCellRenderer<GerritAccount>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends GerritAccount> list, GerritAccount account,
                                                 int index, boolean selected, boolean hasFocus) {
                boolean usedHere = account.equals(projectAccount);
                append(account.toString(), usedHere
                    ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                if (usedHere) {
                    append("  used by this project", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        });
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                return editSelectedAccount();
            }
        }.installOn(accountList);

        JPanel accountPane = ToolbarDecorator.createDecorator(accountList)
            .setAddAction(button -> addAccount())
            .setEditAction(button -> editSelectedAccount())
            .setRemoveAction(button -> removeSelectedAccount())
            .addExtraAction(new AnActionButton("Use for This Project", AllIcons.Actions.Checked) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    GerritAccount selected = accountList.getSelectedValue();
                    if (selected != null) {
                        projectAccount = selected;
                        accountList.repaint();
                    }
                }

                @Override
                public boolean isEnabled() {
                    GerritAccount selected = accountList.getSelectedValue();
                    return selected != null && !selected.equals(projectAccount);
                }
            })
            .disableUpDownActions()
            .createPanel();
        accountPane.setBorder(IdeBorderFactory.createTitledBorder("Gerrit Accounts"));
        accountPane.setPreferredSize(new Dimension(-1, JBUI.scale(160)));
        return accountPane;
    }

    private void addAccount() {
        GerritAccountDialog dialog = new GerritAccountDialog(project, null, "");
        if (!dialog.showAndGet()) {
            return;
        }
        GerritAccount account = GerritAccount.create(dialog.getHost(), dialog.getLogin(), dialog.getCloneBaseUrl());
        accountModel.add(account);
        editedPasswords.put(account.id, dialog.getPassword());
        if (projectAccount == null) { // the first account is the one this project uses, without anyone saying so
            projectAccount = account;
        }
        accountList.setSelectedValue(account, true);
    }

    private boolean editSelectedAccount() {
        GerritAccount account = accountList.getSelectedValue();
        if (account == null) {
            return false;
        }
        String password = editedPasswords.containsKey(account.id)
            ? editedPasswords.get(account.id) : passwordOf(account);
        GerritAccountDialog dialog = new GerritAccountDialog(project, account, password);
        if (!dialog.showAndGet()) {
            return false;
        }
        account.host = dialog.getHost();
        account.login = dialog.getLogin();
        account.cloneBaseUrl = dialog.getCloneBaseUrl();
        if (dialog.isPasswordModified()) {
            editedPasswords.put(account.id, dialog.getPassword());
        }
        accountList.repaint();
        return true;
    }

    private void removeSelectedAccount() {
        GerritAccount account = accountList.getSelectedValue();
        if (account == null) {
            return;
        }
        if (Messages.showYesNoDialog(pane, "Remove the account for " + account + "?",
            "Remove Gerrit Account", Messages.getQuestionIcon()) != Messages.YES) {
            return;
        }
        removedAccountIds.add(account.id);
        editedPasswords.remove(account.id);
        accountModel.remove(account);
        if (account.equals(projectAccount)) {
            projectAccount = accountModel.getSize() == 1 ? accountModel.getElementAt(0) : null;
        }
        accountList.repaint();
    }

    /**
     * Reading blocks on the credential store, so the account dialog is filled behind a modal progress rather than
     * on the event dispatch thread.
     */
    private String passwordOf(GerritAccount account) {
        return ProgressManager.getInstance().<String, RuntimeException>runProcessWithProgressSynchronously(
            () -> GerritAccounts.getInstance().getPassword(account), "Reading Gerrit Credentials", false, project);
    }

    /**
     * @param accounts copies the dialog may edit freely; nothing is stored before the settings are applied
     */
    public void setAccounts(List<GerritAccount> accounts, @Nullable GerritAccount usedByProject) {
        editedPasswords.clear();
        removedAccountIds.clear();
        accountModel.replaceAll(accounts);
        projectAccount = usedByProject;
        accountList.clearSelection();
        accountList.repaint();
    }

    public List<GerritAccount> getAccounts() {
        return new ArrayList<>(accountModel.getItems());
    }

    public Set<String> getRemovedAccountIds() {
        return removedAccountIds;
    }

    public Map<String, String> getEditedPasswords() {
        return editedPasswords;
    }

    @Nullable
    public GerritAccount getProjectAccount() {
        return projectAccount;
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
}
