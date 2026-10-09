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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * Switches the account of the project from the tool window, where the changes it lists are, rather than in the
 * settings. Shown only while there are several accounts to choose between.
 */
public class AccountPopupAction extends BasePopupAction {
    private final Project project;

    public AccountPopupAction(Project project) {
        super(GerritBundle.message("account.popup"));
        this.project = project;
        refresh();
    }

    /**
     * Only on the event dispatch thread: it sets the label.
     */
    void refresh() {
        updateFilterValueLabel(label(GerritProjectAccount.getInstance(project).get()));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setVisible(GerritAccounts.getInstance().getAccounts().size() > 1);
    }

    @Override
    protected void createActions(Consumer<AnAction> actionConsumer) {
        GerritAccount current = GerritProjectAccount.getInstance(project).get();
        for (GerritAccount account : GerritAccounts.getInstance().getAccounts()) {
            // not a toggle action: newer IDEs keep its popup open after a click (KeepPopupOnPerform.IfPreferred),
            // and closing that popup took the next click on this component
            Icon icon = account.equals(current) ? AllIcons.Actions.Checked : null;
            actionConsumer.consume(new DumbAwareAction((String) null, null, icon) {
                {
                    // not as a mnemonic: a login may have an underscore
                    getTemplatePresentation().setText(account.toString(), false);
                }

                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    GerritProjectAccount projectAccount = GerritProjectAccount.getInstance(project);
                    // the account the remotes lead to is not stored, so that it follows them; picking it again must
                    // not pin it
                    if (!account.equals(projectAccount.get())) {
                        // announced, which reloads the list and this label
                        projectAccount.set(account);
                    }
                }
            });
        }
        actionConsumer.consume(Separator.getInstance());
        actionConsumer.consume(new DumbAwareAction(GerritBundle.message("account.popup.manage")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GerritSettingsConfigurable.NAME);
            }
        });
    }

    /**
     * The login and the host, without the scheme, which leaves the toolbar room for the filters.
     */
    static String label(@Nullable GerritAccount account) {
        if (account == null) {
            return GerritBundle.message("account.popup.none");
        }
        if (account.host.isEmpty()) { // taken over from an earlier version
            return account.toString();
        }
        String host = account.host.replaceFirst("^https?://", "").replaceFirst("/+$", "");
        return account.login.isEmpty() ? host : account.login + '@' + host;
    }
}
