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
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtension;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.EditorComments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
                projectSettings().isEnabled() != settingsPane.getProjectEnabled() ||
                !Comparing.equal(gerritSettings.getAutomaticRefresh(), settingsPane.getAutomaticRefresh()) ||
                !Comparing.equal(gerritSettings.getListAllChanges(), settingsPane.getListAllChanges()) ||
                !Comparing.equal(gerritSettings.getRefreshTimeout(), settingsPane.getRefreshTimeout()) ||
                !Comparing.equal(gerritSettings.getReviewNotifications(), settingsPane.getReviewNotifications()) ||
                !Comparing.equal(gerritSettings.getPushToGerrit(), settingsPane.getPushToGerrit()) ||
                !Comparing.equal(gerritSettings.getShowAvatars(), settingsPane.getShowAvatars()) ||
                !Comparing.equal(gerritSettings.getShowCommentsInEditor(), settingsPane.getShowCommentsInEditor()));
    }

    /**
     * Whether a setting changed which the list of changes is built from, other than the accounts: those announce
     * their own changes.
     */
    private boolean isListModified() {
        return !Comparing.equal(gerritSettings.getListAllChanges(), settingsPane.getListAllChanges()) ||
                !Comparing.equal(gerritSettings.getShowAvatars(), settingsPane.getShowAvatars());
    }

    private boolean accountsModified() {
        return accountListModified() || settingsPane.isProjectAccountChosen()
            && !Comparing.equal(settingsPane.getProjectAccount(), projectAccount().get());
    }

    /**
     * Whether the accounts or their passwords changed, rather than only which account this project uses: storing them
     * reloads every open project.
     */
    private boolean accountListModified() {
        if (!settingsPane.getRemovedAccountIds().isEmpty() || !settingsPane.getEditedPasswords().isEmpty()) {
            return true;
        }
        // by id: an account added by another project while the page is open is no edit of this one
        GerritAccounts accounts = GerritAccounts.getInstance();
        for (GerritAccount a : settingsPane.getAccounts()) {
            GerritAccount b = accounts.findById(a.id);
            if (b == null || !a.host.equals(b.host) || !a.login.equals(b.login)
                || !a.cloneBaseUrl.equals(b.cloneBaseUrl) || !a.gitilesUrl.equals(b.gitilesUrl)) {
                return true;
            }
        }
        return false;
    }

    public void apply() throws ConfigurationException {
        if (settingsPane != null) {
            boolean enabledChanged = projectSettings().isEnabled() != settingsPane.getProjectEnabled();
            // a project switched back on has a change list which stopped loading while it was off
            boolean listChanged = isListModified() || enabledChanged && settingsPane.getProjectEnabled();
            boolean editorCommentsChanged =
                gerritSettings.getShowCommentsInEditor() != settingsPane.getShowCommentsInEditor();
            applyAccounts();
            projectSettings().setEnabled(settingsPane.getProjectEnabled());

            gerritSettings.setListAllChanges(settingsPane.getListAllChanges());
            gerritSettings.setAutomaticRefresh(settingsPane.getAutomaticRefresh());
            gerritSettings.setRefreshTimeout(settingsPane.getRefreshTimeout());
            gerritSettings.setReviewNotifications(settingsPane.getReviewNotifications());
            gerritSettings.setPushToGerrit(settingsPane.getPushToGerrit());
            GerritPushExtension.setPushToGerritByDefault(settingsPane.getPushToGerrit());
            gerritSettings.setShowAvatars(settingsPane.getShowAvatars());
            gerritSettings.setShowCommentsInEditor(settingsPane.getShowCommentsInEditor());

            GerritUpdatesNotificationComponent.configurationChanged();
            if (enabledChanged) {
                GerritToolWindowFactory.updateAvailability(project);
                if (settingsPane.getProjectEnabled()) {
                    GerritUpdatesNotificationStartupActivity.start(project);
                }
            }
            if (editorCommentsChanged) { // of every project; the accounts announce themselves
                EditorComments.settingChanged();
            } else if (enabledChanged) {
                EditorComments.settingChanged(project);
            }
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
        Map<String, String> passwords = settingsPane.getEditedPasswords();
        Set<String> removedIds = settingsPane.getRemovedAccountIds();
        if (accountListModified()) { // otherwise only the binding changed, which is the project's own business
            if (passwords.isEmpty() && removedIds.isEmpty()) {
                accounts.update(edited, passwords, removedIds);
            } else { // the credential store blocks, which must not happen on the event dispatch thread
                // a failure is logged rather than thrown here, and leaves the passwords unstored
                AtomicBoolean stored = new AtomicBoolean();
                ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                    accounts.update(edited, passwords, removedIds);
                    stored.set(true);
                }, GerritBundle.message("account.progress.saving"), false, project);
                if (stored.get()) {
                    settingsPane.passwordsStored();
                }
            }
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
            settingsPane.setProjectEnabled(projectSettings().isEnabled());

            settingsPane.setListAllChanges(gerritSettings.getListAllChanges());
            settingsPane.setAutomaticRefresh(gerritSettings.getAutomaticRefresh());
            settingsPane.setRefreshTimeout(gerritSettings.getRefreshTimeout());
            settingsPane.setReviewNotifications(gerritSettings.getReviewNotifications());
            settingsPane.setPushToGerrit(gerritSettings.getPushToGerrit());
            settingsPane.setShowAvatars(gerritSettings.getShowAvatars());
            settingsPane.setShowCommentsInEditor(gerritSettings.getShowCommentsInEditor());
        }
    }

    private GerritProjectSettings projectSettings() {
        return GerritProjectSettings.getInstance(project);
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
