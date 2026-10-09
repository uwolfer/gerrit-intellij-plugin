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

import com.google.gerrit.extensions.common.AccountInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.ui.avatar.AvatarIcons;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JCheckBox;
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
import java.awt.Rectangle;
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
    private static final int AVATAR_SIZE = 16;

    private JSpinner refreshTimeoutSpinner;
    private JPanel pane;
    private JCheckBox notificationOnNewReviewsCheckbox;
    private JCheckBox automaticRefreshCheckbox;
    private JCheckBox listAllChangesCheckbox;
    private JCheckBox pushToGerritCheckbox;
    private JCheckBox showAvatarsCheckBox;
    private JCheckBox showCommentsInEditorCheckBox;
    private JLabel minutesLabel;
    private JLabel listAllHint;

    private final Project project;

    private final CollectionListModel<GerritAccount> accountModel = new CollectionListModel<>();
    private final JBList<GerritAccount> accountList = new JBList<>(accountModel);
    private final GerritAccountDetails accountDetails = new GerritAccountDetails(this::accountDetailsChanged);
    private final AvatarIcons avatarIcons = new AvatarIcons(accountList, AVATAR_SIZE);
    private final Map<String, String> editedPasswords = new HashMap<>();
    private final Set<String> removedAccountIds = new HashSet<>();
    private GerritAccount projectAccount;
    // only a choice the user made is stored; what the page shows may just be the only account or the remotes' one
    private boolean projectAccountChosen;
    private JPanel wrapper;
    private final JCheckBox projectEnabledCheckbox = new JCheckBox("Use Gerrit in this project");

    public SettingsPanel(Project project) {
        this.project = project;
        // the settings for new projects end up in the workspace file of the default project, which none inherits
        projectEnabledCheckbox.setVisible(!project.isDefault());

        styleHints(listAllHint);

        automaticRefreshCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAutomaticRefresh();
            }
        });

        // A timeout of 0 or less silently stops the automatic refresh.
        refreshTimeoutSpinner.setModel(new SpinnerNumberModel(MIN_REFRESH_TIMEOUT, MIN_REFRESH_TIMEOUT, MAX_REFRESH_TIMEOUT, 1));
    }

    static void styleHints(JLabel... hints) {
        for (JLabel hint : hints) {
            hint.setForeground(UIUtil.getContextHelpForeground());
            hint.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
        }
    }

    public static void fixUrl(JTextField textField) {
        textField.setText(UrlUtils.normalizeTypedUrl(textField.getText()));
    }

    private void updateAutomaticRefresh() {
        GuiUtils.enableChildren(refreshTimeoutSpinner, automaticRefreshCheckbox.isSelected());
        minutesLabel.setEnabled(automaticRefreshCheckbox.isSelected());
    }

    public JComponent getPanel() {
        if (wrapper == null) {
            JPanel top = new JPanel(new BorderLayout());
            top.add(projectEnabledCheckbox, BorderLayout.NORTH);
            top.add(createAccountPane(), BorderLayout.CENTER);
            wrapper = new JPanel(new BorderLayout());
            wrapper.add(top, BorderLayout.NORTH);
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
        ColoredListCellRenderer<GerritAccount> renderer = new ColoredListCellRenderer<GerritAccount>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends GerritAccount> list, GerritAccount account,
                                                 int index, boolean selected, boolean hasFocus) {
                boolean usedHere = account.equals(projectAccount);
                SimpleTextAttributes main = usedHere
                    ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
                GerritAccountDetails.Result details = accountDetails.get(account, editedPasswords.get(account.id));
                AccountInfo info = details != null ? details.info : null;
                // room for one in every row, once any has one, so that the names line up
                boolean avatars = showAvatarsCheckBox.isSelected() && accountModel.getItems().stream()
                    .map(other -> accountDetails.get(other, editedPasswords.get(other.id)))
                    .anyMatch(other -> other != null && AvatarIcons.hasAvatar(other.info));
                Icon avatar = avatars ? avatarIcons.getIcon(info) : null;
                setIcon(avatar != null || !avatars ? avatar : JBUIScale.scaleIcon(EmptyIcon.create(AVATAR_SIZE)));
                if (info != null && info.name != null && !info.name.isEmpty()) {
                    append(info.name, main);
                    append("  " + account, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else {
                    append(account.toString(), main);
                }
                if (usedHere) {
                    append("  used by this project", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                if (details != null && details.error != null) {
                    append("  " + details.error, SimpleTextAttributes.ERROR_ATTRIBUTES);
                    if (details.refused) {
                        append("  ");
                        append("Log in", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                            (Runnable) () -> editAccount(account));
                    }
                }
            }
        };
        accountList.setCellRenderer(renderer);
        // a renderer only paints; the link of a row is found by rendering the row under the mouse again
        new LinkMouseListenerBase<Object>() {
            @Override
            protected Object getTagAt(@NotNull MouseEvent e) {
                int index = accountList.locationToIndex(e.getPoint());
                Rectangle bounds = index >= 0 ? accountList.getCellBounds(index, index) : null;
                if (bounds == null || !bounds.contains(e.getPoint())) {
                    return null;
                }
                renderer.getListCellRendererComponent(accountList, accountModel.getElementAt(index), index,
                    false, false);
                return renderer.getFragmentTagAt(e.getX() - bounds.x);
            }
        }.installOn(accountList);
        showAvatarsCheckBox.addActionListener(e -> accountList.repaint());
        accountList.getEmptyText().setText("No accounts added");
        accountList.getEmptyText().appendSecondaryText("Add account…", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
            e -> addAccount());
        // the shortcut of the toolbar's Add
        String shortcut = KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.getNewForDialogs());
        if (!shortcut.isEmpty()) {
            accountList.getEmptyText().appendSecondaryText(" (" + shortcut + ")", StatusText.DEFAULT_ATTRIBUTES, null);
        }
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
        accountPane.setPreferredSize(new Dimension(-1, JBUI.scale(160)));
        JPanel pane = new JPanel(new BorderLayout());
        pane.setBorder(IdeBorderFactory.createTitledBorder("Gerrit Accounts"));
        pane.add(accountPane, BorderLayout.CENTER);
        if (PasswordSafe.getInstance().isMemoryOnly()) {
            pane.add(createMemoryOnlyWarning(), BorderLayout.SOUTH);
        }
        return pane;
    }

    private void accountDetailsChanged() {
        accountList.setPaintBusy(accountDetails.isLoading());
        accountList.repaint();
    }

    /**
     * Passwords kept in memory only are gone after a restart, and the accounts with them; the GitHub plugin warns
     * the same way.
     */
    private static JComponent createMemoryOnlyWarning() {
        HyperlinkLabel warning = new HyperlinkLabel();
        warning.setHyperlinkText("Passwords will not be saved for future use: ", "Configure password store", "");
        warning.setIcon(AllIcons.General.Warning);
        warning.addHyperlinkListener(event -> {
            Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(warning));
            // by id: PasswordSafeConfigurable is internal to the platform
            Configurable passwords = settings != null ? settings.find("application.passwordSafe") : null;
            if (passwords != null) {
                settings.select(passwords);
            }
        });
        warning.setBorder(JBUI.Borders.emptyTop(4));
        return warning;
    }

    private void addAccount() {
        GerritAccountDialog dialog = new GerritAccountDialog(project, null, "", accountModel.getItems());
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
        return account != null && editAccount(account);
    }

    private boolean editAccount(GerritAccount account) {
        List<GerritAccount> others = new ArrayList<>(accountModel.getItems());
        others.remove(account);
        GerritAccountDialog dialog = new GerritAccountDialog(project, account, editedPasswords.get(account.id), others);
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
        accountDetails.forget(account);
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
     * Called once the passwords entered on the page are stored, before the page gets the stored accounts.
     */
    public void passwordsStored() {
        for (GerritAccount account : accountModel.getItems()) {
            String password = editedPasswords.get(account.id);
            if (password != null) {
                accountDetails.stored(account, password);
            }
        }
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

    public boolean getProjectEnabled() {
        return projectEnabledCheckbox.isSelected();
    }

    public void setProjectEnabled(boolean enabled) {
        projectEnabledCheckbox.setSelected(enabled);
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

    public boolean getShowAvatars() {
        return showAvatarsCheckBox.isSelected();
    }

    public void setShowAvatars(final boolean showAvatars) {
        showAvatarsCheckBox.setSelected(showAvatars);
    }

    public boolean getShowCommentsInEditor() {
        return showCommentsInEditorCheckBox.isSelected();
    }

    public void setShowCommentsInEditor(final boolean showCommentsInEditor) {
        showCommentsInEditorCheckBox.setSelected(showCommentsInEditor);
    }
}
