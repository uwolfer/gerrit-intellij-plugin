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
    private SettingsPanel mySettingsPane;

    @Inject
    private GerritSettings mySettings;
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
        if (mySettingsPane == null) {
            mySettingsPane = GerritModule.getInstance(SettingsPanel.class);
        }
        return mySettingsPane.getPanel();
    }

    public boolean isModified() {
        return mySettingsPane != null && (!Comparing.equal(mySettings.getLogin(), mySettingsPane.getLogin()) ||
                isPasswordModified() ||
                !Comparing.equal(mySettings.getHost(), mySettingsPane.getHost()) ||
                !Comparing.equal(mySettings.getAutomaticRefresh(), mySettingsPane.getAutomaticRefresh()) ||
                !Comparing.equal(mySettings.getRefreshTimeout(), mySettingsPane.getRefreshTimeout()) ||
                !Comparing.equal(mySettings.getReviewNotifications(), mySettingsPane.getReviewNotifications()));
    }

    private boolean isPasswordModified() {
        return mySettingsPane.isPasswordModified();
    }

    public void apply() throws ConfigurationException {
        if (mySettingsPane != null) {
            mySettings.setLogin(mySettingsPane.getLogin());
            if (isPasswordModified()) {
                mySettings.setPassword(mySettingsPane.getPassword());
                mySettingsPane.resetPasswordModification();
            }
            mySettings.setHost(mySettingsPane.getHost());
            mySettings.setAutomaticRefresh(mySettingsPane.getAutomaticRefresh());
            mySettings.setRefreshTimeout(mySettingsPane.getRefreshTimeout());
            mySettings.setReviewNotifications(mySettingsPane.getReviewNotifications());

            gerritUpdatesNotificationComponent.handleConfigurationChange();
        }
    }

    public void reset() {
        if (mySettingsPane != null) {
            String login = mySettings.getLogin();
            mySettingsPane.setLogin(login);
            mySettingsPane.setPassword(StringUtil.isEmptyOrSpaces(login) ? "" : DEFAULT_PASSWORD_TEXT);
            mySettingsPane.resetPasswordModification();
            mySettingsPane.setHost(mySettings.getHost());
            mySettingsPane.setAutomaticRefresh(mySettings.getAutomaticRefresh());
            mySettingsPane.setRefreshTimeout(mySettings.getRefreshTimeout());
            mySettingsPane.setReviewNotifications(mySettings.getReviewNotifications());
        }
    }

    public void disposeUIResources() {
        mySettingsPane = null;
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
