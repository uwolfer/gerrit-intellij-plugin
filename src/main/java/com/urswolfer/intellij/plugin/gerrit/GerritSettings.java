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

package com.urswolfer.intellij.plugin.gerrit;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.urswolfer.intellij.plugin.gerrit.ui.ShowProjectColumn;
import org.jetbrains.annotations.NotNull;

/**
 * The preferences which are the same whichever Gerrit instance a project talks to. Host, login and password
 * belong to a {@link GerritAccount}.
 *
 * Parts based on org.jetbrains.plugins.github.GithubSettings
 *
 * @author oleg
 * @author Urs Wolfer
 */
@Service(Service.Level.APP)
@State(name = "GerritSettings", storages = @Storage("gerrit_settings.xml"))
public final class GerritSettings implements PersistentStateComponent<GerritSettings.SettingsState> {

    /**
     * The settings are written as attributes of the component element, under the names the plugin has used since its
     * first release, so that a file written by any earlier version still loads here and a file written here still
     * loads there. {@code alwaysWrite} is what keeps that second direction working: the serializer would otherwise
     * leave out every value which equals its default, and an earlier version reads a missing attribute as
     * {@code false} or {@code 0} rather than as the default which belongs to it.
     *
     * Login, host and clone base url belong to a {@link GerritAccount} now. They are still written here, from the
     * account in use, so that a version without accounts keeps finding them, and they are what the first account is
     * seeded from.
     */
    public static final class SettingsState {
        @Property(alwaysWrite = true) @Attribute("Login") public String login = "";
        @Property(alwaysWrite = true) @Attribute("Host") public String host = "";
        @Property(alwaysWrite = true) @Attribute("ListAllChanges") public boolean listAllChanges = false;
        @Property(alwaysWrite = true) @Attribute("AutomaticRefresh") public boolean automaticRefresh = true;
        @Property(alwaysWrite = true) @Attribute("RefreshTimeout") public int refreshTimeout = 15;
        @Property(alwaysWrite = true) @Attribute("ReviewNotifications") public boolean reviewNotifications = true;
        @Property(alwaysWrite = true) @Attribute("PushToGerrit") public boolean pushToGerrit = false;
        @Property(alwaysWrite = true) @Attribute("ShowChangeNumberColumn") public boolean showChangeNumberColumn = false;
        @Property(alwaysWrite = true) @Attribute("ShowChangeIdColumn") public boolean showChangeIdColumn = false;
        @Property(alwaysWrite = true) @Attribute("ShowTopicColumn") public boolean showTopicColumn = false;
        @Property(alwaysWrite = true)
        @Attribute(value = "ShowProjectColumn", converter = ShowProjectColumnConverter.class)
        public ShowProjectColumn showProjectColumn = ShowProjectColumn.AUTO;
        @Property(alwaysWrite = true) @Attribute("CloneBaseUrl") public String cloneBaseUrl = "";
    }

    /**
     * {@link ShowProjectColumn#toString()} is the label of the settings combo box, and the serializer would write
     * the enum through it. The file has always held the enum name, so it keeps holding the enum name.
     */
    public static final class ShowProjectColumnConverter extends Converter<ShowProjectColumn> {
        @Override
        public ShowProjectColumn fromString(@NotNull String value) {
            try {
                return ShowProjectColumn.valueOf(value);
            } catch (IllegalArgumentException e) { // a value this version does not know about
                return ShowProjectColumn.AUTO;
            }
        }

        @Override
        public String toString(@NotNull ShowProjectColumn value) {
            return value.name();
        }
    }

    private SettingsState state = new SettingsState();

    public static GerritSettings getInstance() {
        return ApplicationManager.getApplication().getService(GerritSettings.class);
    }

    @Override
    public SettingsState getState() {
        GerritAccounts accounts = GerritAccounts.getInstance();
        GerritAccount account = accounts.peekDefaultAccount();
        if (account != null) { // keep what a version without accounts reads pointing at the account in use
            state.host = account.host;
            state.login = account.login;
            state.cloneBaseUrl = account.cloneBaseUrl;
        } else if (accounts.isSeeded()) { // every account is gone, so stop describing one
            state.host = "";
            state.login = "";
            state.cloneBaseUrl = "";
        }
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState state) {
        this.state = state;
    }

    public boolean getListAllChanges() {
        return state.listAllChanges;
    }

    public void setListAllChanges(boolean listAllChanges) {
        state.listAllChanges = listAllChanges;
    }

    public boolean getAutomaticRefresh() {
        return state.automaticRefresh;
    }

    public int getRefreshTimeout() {
        return state.refreshTimeout;
    }

    public boolean getReviewNotifications() {
        return state.reviewNotifications;
    }

    public void setAutomaticRefresh(final boolean automaticRefresh) {
        state.automaticRefresh = automaticRefresh;
    }

    public void setRefreshTimeout(final int refreshTimeout) {
        state.refreshTimeout = refreshTimeout;
    }

    public void setReviewNotifications(final boolean reviewNotifications) {
        state.reviewNotifications = reviewNotifications;
    }

    public void setPushToGerrit(boolean pushToGerrit) {
        state.pushToGerrit = pushToGerrit;
    }

    public boolean getPushToGerrit() {
        return state.pushToGerrit;
    }

    public boolean getShowChangeNumberColumn() {
        return state.showChangeNumberColumn;
    }

    public void setShowChangeNumberColumn(boolean showChangeNumberColumn) {
        state.showChangeNumberColumn = showChangeNumberColumn;
    }

    public boolean getShowChangeIdColumn() {
        return state.showChangeIdColumn;
    }

    public void setShowChangeIdColumn(boolean showChangeIdColumn) {
        state.showChangeIdColumn = showChangeIdColumn;
    }

    public boolean getShowTopicColumn() {
        return state.showTopicColumn;
    }

    public ShowProjectColumn getShowProjectColumn() {
        return state.showProjectColumn;
    }

    public void setShowProjectColumn(ShowProjectColumn showProjectColumn) {
        state.showProjectColumn = showProjectColumn;
    }

    public void setShowTopicColumn(boolean showTopicColumn) {
        state.showTopicColumn = showTopicColumn;
    }

    /**
     * What a version without accounts wrote, which is where the first account is seeded from. Read through
     * {@link GerritAccounts} rather than here.
     */
    String getLegacyHost() {
        return state.host;
    }

    String getLegacyLogin() {
        return state.login;
    }

    String getLegacyCloneBaseUrl() {
        return state.cloneBaseUrl;
    }
}
