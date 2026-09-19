/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import com.intellij.openapi.progress.ProgressManager;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubSettingsConfigurable
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class GerritSettingsConfigurable implements SearchableConfigurable {
    public static final String NAME = "Gerrit";
    private SettingsPanel settingsPane;

    private final Project project;

    private final GerritSettings gerritSettings = GerritSettings.getInstance();

    public GerritSettingsConfigurable(Project project) {
        this.project = project;
    }

    @NotNull
    public String getDisplayName() {
        return NAME;
    }

    @NotNull
    public String getHelpTopic() {
        return "settings.gerrit";
    }

    public JComponent createComponent() {
        if (settingsPane == null) {
            settingsPane = new SettingsPanel(project);
        }
        return settingsPane.getPanel();
    }

    public boolean isModified() {
        return settingsPane != null && (accountsModified() ||
                !Comparing.equal(gerritSettings.getAutomaticRefresh(), settingsPane.getAutomaticRefresh()) ||
                !Comparing.equal(gerritSettings.getListAllChanges(), settingsPane.getListAllChanges()) ||
                !Comparing.equal(gerritSettings.getRefreshTimeout(), settingsPane.getRefreshTimeout()) ||
                !Comparing.equal(gerritSettings.getReviewNotifications(), settingsPane.getReviewNotifications()) ||
                !Comparing.equal(gerritSettings.getPushToGerrit(), settingsPane.getPushToGerrit()) ||
                !Comparing.equal(gerritSettings.getShowChangeNumberColumn(), settingsPane.getShowChangeNumberColumn()) ||
                !Comparing.equal(gerritSettings.getShowChangeIdColumn(), settingsPane.getShowChangeIdColumn()) ||
                !Comparing.equal(gerritSettings.getShowTopicColumn(), settingsPane.getShowTopicColumn()) ||
                !Comparing.equal(gerritSettings.getShowProjectColumn(), settingsPane.getShowProjectColumn()));
    }

    private boolean accountsModified() {
        if (!settingsPane.getRemovedAccountIds().isEmpty() || settingsPane.hasEditedPasswords()) {
            return true;
        }
        if (!Comparing.equal(settingsPane.getSelectedAccount(), projectAccount().get())) {
            return true;
        }
        List<GerritAccount> edited = settingsPane.getAccounts();
        List<GerritAccount> stored = GerritAccounts.getInstance().getAccounts();
        if (edited.size() != stored.size()) {
            return true;
        }
        for (int i = 0; i < edited.size(); i++) {
            GerritAccount a = edited.get(i);
            GerritAccount b = stored.get(i);
            if (!a.id.equals(b.id) || !a.host.equals(b.host) || !a.login.equals(b.login)
                || !a.cloneBaseUrl.equals(b.cloneBaseUrl)) {
                return true;
            }
        }
        return false;
    }

    public void apply() throws ConfigurationException {
        if (settingsPane != null) {
            applyAccounts();

            gerritSettings.setListAllChanges(settingsPane.getListAllChanges());
            gerritSettings.setAutomaticRefresh(settingsPane.getAutomaticRefresh());
            gerritSettings.setRefreshTimeout(settingsPane.getRefreshTimeout());
            gerritSettings.setReviewNotifications(settingsPane.getReviewNotifications());
            gerritSettings.setPushToGerrit(settingsPane.getPushToGerrit());
            gerritSettings.setShowChangeNumberColumn(settingsPane.getShowChangeNumberColumn());
            gerritSettings.setShowChangeIdColumn(settingsPane.getShowChangeIdColumn());
            gerritSettings.setShowTopicColumn(settingsPane.getShowTopicColumn());
            gerritSettings.setShowProjectColumn(settingsPane.getShowProjectColumn());

            GerritUpdatesNotificationComponent.configurationChanged();
        }
    }

    private void applyAccounts() {
        GerritAccounts accounts = GerritAccounts.getInstance();
        List<GerritAccount> edited = settingsPane.getAccounts();
        GerritAccount selected = settingsPane.getSelectedAccount();

        for (String removedId : settingsPane.getRemovedAccountIds()) {
            GerritAccount removed = accounts.findById(removedId);
            if (removed != null) {
                accounts.remove(removed);
            }
        }
        accounts.setAccounts(edited);

        Map<String, String> passwords = settingsPane.collectEditedPasswords();
        if (!passwords.isEmpty()) {
            // writing blocks on the credential store, which must not happen on the event dispatch thread
            ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                for (Map.Entry<String, String> entry : passwords.entrySet()) {
                    GerritAccount account = accounts.findById(entry.getKey());
                    if (account != null) {
                        accounts.setPassword(account, entry.getValue());
                    }
                }
            }, "Saving Gerrit Credentials", false, project);
        }
        projectAccount().set(selected);
        settingsPane.setAccounts(copies(accounts.getAccounts()), accounts.findById(selected != null ? selected.id : null));
    }

    public void reset() {
        if (settingsPane != null) {
            GerritAccounts accounts = GerritAccounts.getInstance();
            GerritAccount current = projectAccount().get();
            List<GerritAccount> copies = copies(accounts.getAccounts());
            settingsPane.setAccounts(copies, find(copies, current));

            settingsPane.setListAllChanges(gerritSettings.getListAllChanges());
            settingsPane.setAutomaticRefresh(gerritSettings.getAutomaticRefresh());
            settingsPane.setRefreshTimeout(gerritSettings.getRefreshTimeout());
            settingsPane.setReviewNotifications(gerritSettings.getReviewNotifications());
            settingsPane.setPushToGerrit(gerritSettings.getPushToGerrit());
            settingsPane.setShowChangeNumberColumn(gerritSettings.getShowChangeNumberColumn());
            settingsPane.setShowChangeIdColumn(gerritSettings.getShowChangeIdColumn());
            settingsPane.setShowTopicColumn(gerritSettings.getShowTopicColumn());
            settingsPane.setShowProjectColumn(gerritSettings.getShowProjectColumn());
        }
    }

    private GerritProjectAccount projectAccount() {
        return GerritProjectAccount.getInstance(project);
    }

    private static List<GerritAccount> copies(List<GerritAccount> accounts) {
        List<GerritAccount> copies = new ArrayList<>(accounts.size());
        for (GerritAccount account : accounts) {
            copies.add(account.copy());
        }
        return copies;
    }

    @Nullable
    private static GerritAccount find(List<GerritAccount> accounts, @Nullable GerritAccount account) {
        return account == null ? null : accounts.stream().filter(a -> a.id.equals(account.id)).findFirst().orElse(null);
    }

    public void disposeUIResources() {
        settingsPane = null;
    }

    @NotNull
    public String getId() {
        return getHelpTopic();
    }

    public Runnable enableSearch(String option) {
        return null;
    }
}
