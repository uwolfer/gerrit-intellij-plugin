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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
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
    private static final int MIN_REFRESH_TIMEOUT = 1;
    private static final int MAX_REFRESH_TIMEOUT = 24 * 60;

    private JSpinner refreshTimeoutSpinner;
    private JPanel pane;
    private JCheckBox notificationOnNewReviewsCheckbox;
    private JCheckBox automaticRefreshCheckbox;
    private JCheckBox listAllChangesCheckbox;
    private JCheckBox pushToGerritCheckbox;
    private JCheckBox showChangeNumberColumnCheckBox;
    private JCheckBox showChangeIdColumnCheckBox;
    private JCheckBox showTopicColumnCheckBox;
    private JCheckBox showAvatarsCheckBox;
    private JComboBox showProjectColumnComboBox;
    private JLabel minutesLabel;
    private JLabel listAllHint;

    private final Project project;

    private final CollectionListModel<GerritAccount> accountModel = new CollectionListModel<>();
    private final JBList<GerritAccount> accountList = new JBList<>(accountModel);
    private final Map<String, String> editedPasswords = new HashMap<>();
    private final Set<String> removedAccountIds = new HashSet<>();
    private GerritAccount projectAccount;
    // only a choice the user made is stored; what the page shows may just be the only account or the remotes' one
    private boolean projectAccountChosen;
    private JPanel wrapper;

    public SettingsPanel(Project project) {
        this.project = project;

        styleHints(listAllHint);

        automaticRefreshCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAutomaticRefresh();
            }
        });

        // A timeout of 0 or less silently stops the automatic refresh.
        refreshTimeoutSpinner.setModel(new SpinnerNumberModel(MIN_REFRESH_TIMEOUT, MIN_REFRESH_TIMEOUT, MAX_REFRESH_TIMEOUT, 1));

        showProjectColumnComboBox.setModel(new EnumComboBoxModel(ShowProjectColumn.class));
    }

    static void styleHints(JLabel... hints) {
        for (JLabel hint : hints) {
            hint.setForeground(UIUtil.getContextHelpForeground());
            hint.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
        }
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
        minutesLabel.setEnabled(automaticRefreshCheckbox.isSelected());
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
                        projectAccountChosen = true;
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
        account.gitilesUrl = dialog.getGitilesUrl();
        accountModel.add(account);
        editedPasswords.put(account.id, dialog.getPassword());
        // the first account is the one this project uses without anyone saying so; one added to several is not
        if (accountModel.getSize() == 1) {
            projectAccount = account;
        }
        accountList.setSelectedValue(account, true);
    }

    private boolean editSelectedAccount() {
        GerritAccount account = accountList.getSelectedValue();
        if (account == null) {
            return false;
        }
        GerritAccountDialog dialog = new GerritAccountDialog(project, account, editedPasswords.get(account.id));
        if (!dialog.showAndGet()) {
            return false;
        }
        account.host = dialog.getHost();
        account.login = dialog.getLogin();
        account.cloneBaseUrl = dialog.getCloneBaseUrl();
        account.gitilesUrl = dialog.getGitilesUrl();
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
            projectAccountChosen = true;
        }
        accountList.repaint();
    }

    /**
     * @param accounts copies the dialog may edit freely; nothing is stored before the settings are applied
     */
    public void setAccounts(List<GerritAccount> accounts, @Nullable GerritAccount usedByProject) {
        editedPasswords.clear();
        removedAccountIds.clear();
        accountModel.replaceAll(accounts);
        projectAccount = usedByProject;
        projectAccountChosen = false;
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

    public boolean isProjectAccountChosen() {
        return projectAccountChosen;
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

    public boolean getShowAvatars() {
        return showAvatarsCheckBox.isSelected();
    }

    public void setShowAvatars(final boolean showAvatars) {
        showAvatarsCheckBox.setSelected(showAvatars);
    }

    public ShowProjectColumn getShowProjectColumn() {
        return (ShowProjectColumn) showProjectColumnComboBox.getModel().getSelectedItem();
    }

    public void setShowProjectColumn(ShowProjectColumn showProjectColumn) {
        showProjectColumnComboBox.getModel().setSelectedItem(showProjectColumn);
    }
}
