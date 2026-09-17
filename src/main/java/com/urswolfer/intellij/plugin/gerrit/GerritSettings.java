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

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.ui.ShowProjectColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parts based on org.jetbrains.plugins.github.GithubSettings
 *
 * @author oleg
 * @author Urs Wolfer
 */
@Service(Service.Level.APP)
@State(name = "GerritSettings", storages = @Storage("gerrit_settings.xml"))
public final class GerritSettings implements PersistentStateComponent<GerritSettings.SettingsState>, GerritAuthData {

    private static final String GERRIT_SETTINGS_PASSWORD_KEY = "GERRIT_SETTINGS_PASSWORD_KEY";
    private static final CredentialAttributes CREDENTIAL_ATTRIBUTES = new CredentialAttributes(
            CredentialAttributesKt.generateServiceName("Gerrit", GERRIT_SETTINGS_PASSWORD_KEY),
            GERRIT_SETTINGS_PASSWORD_KEY);
    private static final CredentialAttributes LEGACY_CREDENTIAL_ATTRIBUTES = new CredentialAttributes(
            GerritSettings.class.getName(),
            GERRIT_SETTINGS_PASSWORD_KEY);

    /**
     * The settings are written as attributes of the component element, under the names the plugin has used since its
     * first release, so that a file written by any earlier version still loads here and a file written here still
     * loads there. {@code alwaysWrite} is what keeps that second direction working: the serializer would otherwise
     * leave out every value which equals its default, and an earlier version reads a missing attribute as
     * {@code false} or {@code 0} rather than as the default which belongs to it.
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

    private final Object credentialsLock = new Object();
    private boolean legacyCredentialsMigrated;

    public static GerritSettings getInstance() {
        return ApplicationManager.getApplication().getService(GerritSettings.class);
    }

    @Override
    public SettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState state) {
        this.state = state;
    }

    @Override
    @Nullable
    public String getLogin() {
        return state.login;
    }

    /**
     * Reading the credential store blocks and must not happen on the event dispatch thread. The REST client asks for
     * the password through this method while it runs in the background, which is fine; UI code which needs it right
     * away goes through {@link #getPasswordWithModalProgress}.
     */
    @Override
    @NotNull
    public String getPassword() {
        PasswordSafe passwordSafe = PasswordSafe.getInstance();
        Credentials credentials = passwordSafe.get(CREDENTIAL_ATTRIBUTES);
        if (credentials == null) {
            credentials = migrateLegacyCredentials(passwordSafe);
        }
        String password = credentials != null ? credentials.getPasswordAsString() : null;
        return password != null ? password : "";
    }

    /**
     * A modal progress moves the blocking read off the event dispatch thread. The progress window only becomes
     * visible if the credential store really takes a while - an OS keychain may need to be unlocked first.
     */
    @NotNull
    public String getPasswordWithModalProgress(@Nullable Project project) {
        return ProgressManager.getInstance().<String, RuntimeException>runProcessWithProgressSynchronously(
                this::getPassword, "Reading Gerrit Credentials", false, project);
    }

    /**
     * Credentials used to be stored under this class' name; move them over to the generated service name the first
     * time nothing is found there. Concurrent requests are the normal case, so the move runs under a lock, and a
     * caller which finds it already done re-reads the current key instead of reporting nothing: its own lookup ran
     * before the move and missed the entry in flight.
     */
    @Nullable
    private Credentials migrateLegacyCredentials(PasswordSafe passwordSafe) {
        synchronized (credentialsLock) {
            if (legacyCredentialsMigrated) {
                return passwordSafe.get(CREDENTIAL_ATTRIBUTES);
            }
            Credentials credentials = passwordSafe.get(LEGACY_CREDENTIAL_ATTRIBUTES);
            if (credentials != null) {
                passwordSafe.set(CREDENTIAL_ATTRIBUTES, credentials);
                passwordSafe.set(LEGACY_CREDENTIAL_ATTRIBUTES, null);
            }
            legacyCredentialsMigrated = true;
            return credentials;
        }
    }

    @Override
    public boolean isHttpPassword() {
        return false;
    }

    @Override
    public String getHost() {
        return state.host;
    }

    @Override
    public boolean isLoginAndPasswordAvailable() {
        String login = getLogin();
        return login != null && !login.isEmpty();
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

    public void setLogin(final String login) {
        state.login = login != null ? login : "";
    }

    /**
     * Writing blocks just like reading does, so UI code which saves the password goes through a modal progress
     * rather than holding the event dispatch thread while the credential store is written.
     */
    public void setPasswordWithModalProgress(@Nullable Project project, final String password) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> setPassword(password), "Saving Gerrit Credentials", false, project);
    }

    public void setPassword(final String password) {
        PasswordSafe passwordSafe = PasswordSafe.getInstance();
        synchronized (credentialsLock) {
            passwordSafe.set(CREDENTIAL_ATTRIBUTES, new Credentials(null, password != null ? password : ""));
            passwordSafe.set(LEGACY_CREDENTIAL_ATTRIBUTES, null);
            legacyCredentialsMigrated = true;
        }
    }

    public void forgetPassword() {
        PasswordSafe passwordSafe = PasswordSafe.getInstance();
        synchronized (credentialsLock) {
            passwordSafe.set(CREDENTIAL_ATTRIBUTES, null);
            passwordSafe.set(LEGACY_CREDENTIAL_ATTRIBUTES, null);
            legacyCredentialsMigrated = true;
        }
    }

    public void setHost(final String host) {
        state.host = host != null ? host : "";
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

    public void setCloneBaseUrl(String cloneBaseUrl) {
        state.cloneBaseUrl = cloneBaseUrl != null ? cloneBaseUrl : "";
    }

    public String getCloneBaseUrl() {
        return state.cloneBaseUrl;
    }

    public String getCloneBaseUrlOrHost() {
        return state.cloneBaseUrl == null || state.cloneBaseUrl.isEmpty() ? state.host : state.cloneBaseUrl;
    }
}
