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
import com.intellij.openapi.util.text.StringUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.settings.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.settings.GerritSettings;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubSettingsConfigurable
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class GerritSettingsConfigurable implements SearchableConfigurable {
    public static final String NAME = "Gerrit";
    private static final String DEFAULT_PASSWORD_TEXT = "************";
    private SettingsPanel settingsPane;

    private final Project project;
    private final GerritSettings gerritSettings;
    private final GerritUpdatesNotificationComponent gerritUpdatesNotificationComponent;

    public GerritSettingsConfigurable(Project project, GerritSettings gerritSettings, GerritUpdatesNotificationComponent gerritUpdatesNotificationComponent) {
        this.project = project;
        this.gerritSettings = gerritSettings;
        this.gerritUpdatesNotificationComponent = gerritUpdatesNotificationComponent;
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
            settingsPane = GerritModule.getInstance(SettingsPanel.Factory.class).build(project);
        }
        return settingsPane.getPanel();
    }

    public boolean isModified() {
        GerritProjectSettings projectSettings = gerritSettings.forProject(project);
        return settingsPane != null && (!Comparing.equal(projectSettings.getLogin(), settingsPane.getLogin()) ||
                isPasswordModified() ||
                !Comparing.equal(projectSettings.getHost(), settingsPane.getHost()) ||
                !Comparing.equal(gerritSettings.getAutomaticRefresh(), settingsPane.getAutomaticRefresh()) ||
                !Comparing.equal(gerritSettings.getListAllChanges(), settingsPane.getListAllChanges()) ||
                !Comparing.equal(gerritSettings.getRefreshTimeout(), settingsPane.getRefreshTimeout()) ||
                !Comparing.equal(gerritSettings.getReviewNotifications(), settingsPane.getReviewNotifications()) ||
                !Comparing.equal(gerritSettings.getPushToGerrit(), settingsPane.getPushToGerrit()) ||
                !Comparing.equal(gerritSettings.getShowChangeNumberColumn(), settingsPane.getShowChangeNumberColumn()) ||
                !Comparing.equal(gerritSettings.getShowChangeIdColumn(), settingsPane.getShowChangeIdColumn()) ||
                !Comparing.equal(gerritSettings.getShowTopicColumn(), settingsPane.getShowTopicColumn()) ||
                !Comparing.equal(gerritSettings.getShowProjectColumn(), settingsPane.getShowProjectColumn()) ||
                !Comparing.equal(projectSettings.getCloneBaseUrl(), settingsPane.getCloneBaseUrl()));
    }

    private boolean isPasswordModified() {
        return settingsPane.isPasswordModified();
    }

    public void apply() throws ConfigurationException {
        if (settingsPane != null) {
            GerritProjectSettings projectSettings = gerritSettings.forProject(project);
            if (isPasswordModified()) {
                projectSettings.setCredentials(settingsPane.getHost(), settingsPane.getLogin(), settingsPane.getPassword());
                settingsPane.resetPasswordModification();
            } else {
                projectSettings.setCredentials(settingsPane.getHost(), settingsPane.getLogin());
            }
            gerritSettings.setListAllChanges(settingsPane.getListAllChanges());
            gerritSettings.setAutomaticRefresh(settingsPane.getAutomaticRefresh());
            gerritSettings.setRefreshTimeout(settingsPane.getRefreshTimeout());
            gerritSettings.setReviewNotifications(settingsPane.getReviewNotifications());
            gerritSettings.setPushToGerrit(settingsPane.getPushToGerrit());
            gerritSettings.setShowChangeNumberColumn(settingsPane.getShowChangeNumberColumn());
            gerritSettings.setShowChangeIdColumn(settingsPane.getShowChangeIdColumn());
            gerritSettings.setShowTopicColumn(settingsPane.getShowTopicColumn());
            gerritSettings.setShowProjectColumn(settingsPane.getShowProjectColumn());
            projectSettings.setCloneBaseUrl(settingsPane.getCloneBaseUrl());

            gerritUpdatesNotificationComponent.handleConfigurationChange();
        }
    }

    public void reset() {
        if (settingsPane != null) {
            GerritProjectSettings projectSettings = gerritSettings.forProject(project);
            String login = projectSettings.getLogin();
            settingsPane.setLogin(login);
            settingsPane.setPassword(StringUtil.isEmptyOrSpaces(login) ? "" : DEFAULT_PASSWORD_TEXT);
            settingsPane.resetPasswordModification();
            settingsPane.setHost(projectSettings.getHost());
            settingsPane.setListAllChanges(gerritSettings.getListAllChanges());
            settingsPane.setAutomaticRefresh(gerritSettings.getAutomaticRefresh());
            settingsPane.setRefreshTimeout(gerritSettings.getRefreshTimeout());
            settingsPane.setReviewNotifications(gerritSettings.getReviewNotifications());
            settingsPane.setPushToGerrit(gerritSettings.getPushToGerrit());
            settingsPane.setShowChangeNumberColumn(gerritSettings.getShowChangeNumberColumn());
            settingsPane.setShowChangeIdColumn(gerritSettings.getShowChangeIdColumn());
            settingsPane.setShowTopicColumn(gerritSettings.getShowTopicColumn());
            settingsPane.setShowProjectColumn(gerritSettings.getShowProjectColumn());
            settingsPane.setCloneBaseUrl(projectSettings.getCloneBaseUrl());
        }
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
