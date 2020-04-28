/*
 * Copyright 2020 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.settings;

import com.google.common.base.Strings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

/**
 * @author Réda Housni Alaoui
 */
@State(
    name = "GerritProjectSettings",
    storages = {
        @Storage(
            file = StoragePathMacros.PROJECT_CONFIG_DIR + "/gerrit_settings.xml"
        )}
)
public class GerritProjectSettings implements PersistentStateComponent<Element>, GerritAuthData {

    private static final String GERRIT_SETTINGS_TAG = "GerritSettings";
    private static final String CLONE_BASE_URL = "CloneBaseUrl";
    private static final String VERSION_ATTRIBUTE = "version";

    private final GerritCredentials credentials = new GerritCredentials();
    private final Map<Integer, Migration> migrationByVersion = new LinkedHashMap<Integer, Migration>();
    private final int targetVersion;

    private String cloneBaseUrl = "";
    private int version;

    private Logger log;

    public GerritProjectSettings() {
        migrationByVersion.put(1, new Version1Migration());
        targetVersion = Collections.max(migrationByVersion.keySet());
        migrate();
    }

    @Nullable
    @Override
    public Element getState() {
        final Element element = new Element(GERRIT_SETTINGS_TAG);
        credentials.persistTo(element);
        element.setAttribute(CLONE_BASE_URL, (getCloneBaseUrl() != null ? getCloneBaseUrl() : ""));
        element.setAttribute(VERSION_ATTRIBUTE, String.valueOf(version));
        return element;
    }

    @Override
    public void loadState(Element element) {
        try {
            credentials.readFrom(element);
            setCloneBaseUrl(element.getAttributeValue(CLONE_BASE_URL));
            version = Integer.parseInt(element.getAttributeValue(VERSION_ATTRIBUTE));
            migrate();
        } catch (Exception e) {
            log.error("Error happened while loading gerrit settings: " + e);
        }
    }

    private void migrate() {
        if (version >= targetVersion) {
            return;
        }

        for (Map.Entry<Integer, Migration> migration : migrationByVersion.entrySet()) {
            if (version >= migration.getKey()) {
                continue;
            }
            migration.getValue().migrate(this);
        }
        this.version = targetVersion;
    }

    public void setCredentials(String host, String login, String password) {
        credentials.update(host, login, password);
    }

    public void setCredentials(String host, String login) {
        credentials.update(host, login);
    }

    public void setCloneBaseUrl(String cloneBaseUrl) {
        this.cloneBaseUrl = cloneBaseUrl;
    }

    public String getCloneBaseUrl() {
        return cloneBaseUrl;
    }

    public String getCloneBaseUrlOrHost() {
        return Strings.isNullOrEmpty(cloneBaseUrl) ? credentials.getHost() : cloneBaseUrl;
    }

    @Override
    public boolean isLoginAndPasswordAvailable() {
        return !Strings.isNullOrEmpty(getLogin());
    }

    @Override
    public String getLogin() {
        return credentials.getLogin();
    }

    @Override
    public String getPassword() {
        return credentials.getPassword();
    }

    @Override
    public boolean isHttpPassword() {
        return false;
    }

    @Override
    public String getHost() {
        return credentials.getHost();
    }

    public void setLog(Logger log) {
        this.log = log;
    }

    public boolean preloadPassword() {
        return credentials.preloadPassword();
    }

    public void forgetPassword() {
        credentials.forgetPassword();
    }

    private static class Version1Migration implements Migration {

        @Override
        public void migrate(GerritProjectSettings projectSettings) {
            GerritSettings gerritSettings = GerritModule.getInstance(GerritSettings.class);
            String legacyHost = gerritSettings.getElement().getAttributeValue("Host");
            String legacyLogin = gerritSettings.getElement().getAttributeValue("Login");
            String legacyCloneBaseUrl = gerritSettings.getElement().getAttributeValue("CloneBaseUrl");

            projectSettings.setCloneBaseUrl(legacyCloneBaseUrl);
            projectSettings.setCredentials(legacyHost, legacyLogin);
        }
    }

    private interface Migration {

        void migrate(GerritProjectSettings projectSettings);

    }
}
