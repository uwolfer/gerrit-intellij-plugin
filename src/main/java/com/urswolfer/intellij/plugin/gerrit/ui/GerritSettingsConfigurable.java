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

import com.google.inject.Inject;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfigurableProvider;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubSettingsConfigurable
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class GerritSettingsConfigurable implements SearchableConfigurable, VcsConfigurableProvider {
    public static final String NAME = "Gerrit";
    private static final String DEFAULT_PASSWORD_TEXT = "************";
    private SettingsPanel settingsPane;

    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private GerritUpdatesNotificationComponent gerritUpdatesNotificationComponent;

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
            settingsPane = GerritModule.getInstance(SettingsPanel.class);
        }
        return settingsPane.getPanel();
    }

    public boolean isModified() {
        return settingsPane != null && (!Comparing.equal(gerritSettings.getLogin(), settingsPane.getLogin(), true) ||
                isPasswordModified() ||
                !Comparing.equal(gerritSettings.getHost(), settingsPane.getHost(), true) ||
                !Comparing.equal(gerritSettings.getAutomaticRefresh(), settingsPane.getAutomaticRefresh()) ||
                !Comparing.equal(gerritSettings.getListAllChanges(), settingsPane.getListAllChanges()) ||
                !Comparing.equal(gerritSettings.getRefreshTimeout(), settingsPane.getRefreshTimeout()) ||
                !Comparing.equal(gerritSettings.getReviewNotifications(), settingsPane.getReviewNotifications()) ||
                !Comparing.equal(gerritSettings.getPushToGerrit(), settingsPane.getPushToGerrit()) ||
                !Comparing.equal(gerritSettings.getShowChangeNumberColumn(), settingsPane.getShowChangeNumberColumn()) ||
                !Comparing.equal(gerritSettings.getShowChangeIdColumn(), settingsPane.getShowChangeIdColumn()) ||
                !Comparing.equal(gerritSettings.getShowTopicColumn(), settingsPane.getShowTopicColumn()) ||
                !Comparing.equal(gerritSettings.getShowProjectColumn(), settingsPane.getShowProjectColumn()) ||
                !Comparing.equal(gerritSettings.getCloneBaseUrl(), settingsPane.getCloneBaseUrl(), true));
    }

    private boolean isPasswordModified() {
        return settingsPane.isPasswordModified();
    }

    public void apply() throws ConfigurationException {
        if (settingsPane != null) {
            gerritSettings.setLogin(settingsPane.getLogin());
            if (isPasswordModified()) {
                gerritSettings.setPassword(settingsPane.getPassword());
                settingsPane.resetPasswordModification();
            }
            gerritSettings.setHost(settingsPane.getHost());
            gerritSettings.setListAllChanges(settingsPane.getListAllChanges());
            gerritSettings.setAutomaticRefresh(settingsPane.getAutomaticRefresh());
            gerritSettings.setRefreshTimeout(settingsPane.getRefreshTimeout());
            gerritSettings.setReviewNotifications(settingsPane.getReviewNotifications());
            gerritSettings.setPushToGerrit(settingsPane.getPushToGerrit());
            gerritSettings.setShowChangeNumberColumn(settingsPane.getShowChangeNumberColumn());
            gerritSettings.setShowChangeIdColumn(settingsPane.getShowChangeIdColumn());
            gerritSettings.setShowTopicColumn(settingsPane.getShowTopicColumn());
            gerritSettings.setShowProjectColumn(settingsPane.getShowProjectColumn());
            gerritSettings.setCloneBaseUrl(settingsPane.getCloneBaseUrl());

            gerritUpdatesNotificationComponent.handleConfigurationChange();
        }
    }

    public void reset() {
        if (settingsPane != null) {
            String login = gerritSettings.getLogin();
            settingsPane.setLogin(login);
            settingsPane.setPassword(StringUtil.isEmptyOrSpaces(login) ? "" : DEFAULT_PASSWORD_TEXT);
            settingsPane.resetPasswordModification();
            settingsPane.setHost(gerritSettings.getHost());
            settingsPane.setListAllChanges(gerritSettings.getListAllChanges());
            settingsPane.setAutomaticRefresh(gerritSettings.getAutomaticRefresh());
            settingsPane.setRefreshTimeout(gerritSettings.getRefreshTimeout());
            settingsPane.setReviewNotifications(gerritSettings.getReviewNotifications());
            settingsPane.setPushToGerrit(gerritSettings.getPushToGerrit());
            settingsPane.setShowChangeNumberColumn(gerritSettings.getShowChangeNumberColumn());
            settingsPane.setShowChangeIdColumn(gerritSettings.getShowChangeIdColumn());
            settingsPane.setShowTopicColumn(gerritSettings.getShowTopicColumn());
            settingsPane.setShowProjectColumn(gerritSettings.getShowProjectColumn());
            settingsPane.setCloneBaseUrl(gerritSettings.getCloneBaseUrl());
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

    @Nullable
    @Override
    public Configurable getConfigurable(Project project) {
        return this;
    }

    public static class Proxy extends GerritSettingsConfigurable {
        private GerritSettingsConfigurable delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(GerritSettingsConfigurable.class);
        }

        @Override
        @NotNull
        public String getDisplayName() {
            return delegate.getDisplayName();
        }

        @Override
        @NotNull
        public String getHelpTopic() {
            return delegate.getHelpTopic();
        }

        @Override
        public JComponent createComponent() {
            return delegate.createComponent();
        }

        @Override
        public boolean isModified() {
            return delegate.isModified();
        }

        @Override
        public void apply() throws ConfigurationException {
            delegate.apply();
        }

        @Override
        public void reset() {
            delegate.reset();
        }

        @Override
        public void disposeUIResources() {
            delegate.disposeUIResources();
        }

        @Override
        @NotNull
        public String getId() {
            return delegate.getId();
        }

        @Override
        public Runnable enableSearch(String option) {
            return delegate.enableSearch(option);
        }

        @Nullable
        @Override
        public Configurable getConfigurable(Project project) {
            return delegate.getConfigurable(project);
        }
    }
}
