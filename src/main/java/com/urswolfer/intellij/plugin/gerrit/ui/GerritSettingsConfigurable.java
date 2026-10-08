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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
                !Comparing.equal(gerritSettings.getShowAvatars(), settingsPane.getShowAvatars()));
    }

    /** Whether a setting changed which the list of changes is built from: the accounts and the columns. */
    private boolean isListModified() {
        return accountsModified() ||
                !Comparing.equal(gerritSettings.getListAllChanges(), settingsPane.getListAllChanges()) ||
                !Comparing.equal(gerritSettings.getShowAvatars(), settingsPane.getShowAvatars());
    }

    private boolean accountsModified() {
        if (!settingsPane.getRemovedAccountIds().isEmpty() || !settingsPane.getEditedPasswords().isEmpty()) {
            return true;
        }
        if (settingsPane.isProjectAccountChosen()
            && !Comparing.equal(settingsPane.getProjectAccount(), projectAccount().get())) {
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
                || !a.cloneBaseUrl.equals(b.cloneBaseUrl) || !a.gitilesUrl.equals(b.gitilesUrl)) {
                return true;
            }
        }
        return false;
    }

    public void apply() throws ConfigurationException {
        if (settingsPane != null) {
            boolean listChanged = isListModified();
            applyAccounts();

            gerritSettings.setListAllChanges(settingsPane.getListAllChanges());
            gerritSettings.setAutomaticRefresh(settingsPane.getAutomaticRefresh());
            gerritSettings.setRefreshTimeout(settingsPane.getRefreshTimeout());
            gerritSettings.setReviewNotifications(settingsPane.getReviewNotifications());
            gerritSettings.setPushToGerrit(settingsPane.getPushToGerrit());
            GerritPushExtension.setPushToGerritByDefault(settingsPane.getPushToGerrit());
            gerritSettings.setShowAvatars(settingsPane.getShowAvatars());

            GerritUpdatesNotificationComponent.configurationChanged();
            if (listChanged) {
                ApplicationManager.getApplication().getMessageBus()
                        .syncPublisher(GerritListSettingsListener.TOPIC).listSettingsChanged();
            }
        }
    }

    private void applyAccounts() {
        GerritAccounts accounts = GerritAccounts.getInstance();
        List<GerritAccount> edited = settingsPane.getAccounts();
        GerritAccount usedByProject = settingsPane.getProjectAccount();

        List<GerritAccount> removed = new ArrayList<>();
        for (String removedId : settingsPane.getRemovedAccountIds()) {
            GerritAccount account = accounts.findById(removedId);
            if (account != null) {
                removed.add(account);
            }
        }
        accounts.setAccounts(edited);

        Map<String, String> passwords = settingsPane.getEditedPasswords();
        if (!removed.isEmpty() || !passwords.isEmpty()) {
            // the credential store blocks, which must not happen on the event dispatch thread
            ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                for (GerritAccount account : removed) {
                    accounts.clearPasswordOfRemoved(account);
                }
                for (Map.Entry<String, String> entry : passwords.entrySet()) {
                    GerritAccount account = accounts.findById(entry.getKey());
                    if (account != null) {
                        accounts.setPassword(account, entry.getValue());
                    }
                }
            }, "Saving Gerrit Credentials", false, project);
        }
        // With one account left there is no choice to store. Otherwise a choice the user made is stored, and so is the
        // account the page showed as used when nothing would resolve to it any more - a second account was added -
        // but not one the remotes still lead to: that answer follows the remotes.
        boolean several = accounts.getAccounts().size() > 1;
        if (settingsPane.isProjectAccountChosen()) {
            projectAccount().set(several ? usedByProject : null);
        } else if (several && usedByProject != null && projectAccount().get() == null) {
            projectAccount().set(usedByProject);
        }

        List<GerritAccount> refreshed = copies(accounts.getAccounts());
        // the panel edits copies; handing it the stored accounts would edit them in place, past any Cancel
        settingsPane.setAccounts(refreshed, find(refreshed, projectAccount().get()));
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
            settingsPane.setShowAvatars(gerritSettings.getShowAvatars());
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
