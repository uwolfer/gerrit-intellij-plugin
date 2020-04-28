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

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Réda Housni Alaoui
 */
class GerritCredentials {

    private static final String HOST = "Host";
    private static final String LOGIN = "Login";
    private static final String PASSWORD_VERSION = "PasswordVersion";
    private static final String PASSWORD_KEY_PREFIX = "GERRIT_SETTINGS_PASSWORD_KEY";

    private final Map<Integer, PasswordMigration> passwordMigrationByVersion = new LinkedHashMap<Integer, PasswordMigration>();
    private final int passwordTargetVersion;

    private Logger log;

    private String host = "";
    private String login = "";
    private int passwordVersion;

    private Optional<String> preloadedPassword;
    private AtomicInteger passwordDialogShowLimit = new AtomicInteger(3);

    GerritCredentials() {
        passwordMigrationByVersion.put(1, new Version1PasswordMigration());
        passwordTargetVersion = Collections.max(passwordMigrationByVersion.keySet());
    }

    public void readFrom(Element element) {
        update(element.getAttributeValue(HOST), element.getAttributeValue(LOGIN));
        this.passwordVersion = Integer.parseInt(element.getAttributeValue(PASSWORD_VERSION));
    }

    public void persistTo(Element element) {
        element.setAttribute(LOGIN, (getLogin() != null ? getLogin() : ""));
        element.setAttribute(HOST, (getHost() != null ? getHost() : ""));
        element.setAttribute(PASSWORD_VERSION, String.valueOf(passwordVersion));
    }

    public void update(String host, String login) {
        this.host = host;
        this.login = login != null ? login : "";
    }

    public void update(String host, String login, final String password) {
        update(host, login);

        String passwordKey = createPasswordStorageKey();
        try {
            PasswordSafe.getInstance().storePassword(null, GerritProjectSettings.class, passwordKey, password != null ? password : "");
        } catch (PasswordSafeException e) {
            log.info("Couldn't set password for key [" + passwordKey + "]", e);
        }
    }

    public void forgetPassword() {
        String passwordKey = createPasswordStorageKey();
        try {
            PasswordSafe.getInstance().removePassword(null, GerritProjectSettings.class, passwordKey);
        } catch (PasswordSafeException e) {
            log.info("Couldn't forget password for key [" + passwordKey + "]", e);
        }
    }

    @NotNull
    public String getPassword() {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            if (preloadedPassword == null) {
                throw new IllegalStateException("Need to call #preloadPassword when password is required in background thread");
            }
        } else {
            if (!preloadPassword()) {
                return "";
            }
        }
        return preloadedPassword.or("");
    }

    public boolean preloadPassword() {
        migratePassword();

        String passwordKey = createPasswordStorageKey();
        String password = null;
        try {
            password = PasswordSafe.getInstance().getPassword(null, GerritProjectSettings.class, passwordKey);
        } catch (PasswordSafeException e) {
            log.info("Couldn't get password for key [" + passwordKey + "]", e);
        }
        if (Strings.isNullOrEmpty(password) && !Strings.isNullOrEmpty(getLogin())) {
            if (passwordDialogShowLimit.decrementAndGet() <= 0) {
                return false;
            }
            password = Messages.showPasswordDialog(
                String.format("Password for accessing Gerrit required (Login: %s, URL: %s).", getLogin(), getHost()),
                "Gerrit Password");
            if (password == null) {
                return false;
            }
        }
        preloadedPassword = Optional.fromNullable(password);
        return true;
    }

    private String createPasswordStorageKey() {
        return PASSWORD_KEY_PREFIX + ":" + host + ":" + login;
    }

    public String getHost() {
        return host;
    }

    public String getLogin() {
        return login;
    }

    public void setLog(Logger log) {
        this.log = log;
    }

    private void migratePassword() {
        if (passwordVersion >= passwordTargetVersion) {
            return;
        }

        for (Map.Entry<Integer, PasswordMigration> migration : passwordMigrationByVersion.entrySet()) {
            if (passwordVersion >= migration.getKey()) {
                continue;
            }
            migration.getValue().migrate(this);
        }
        this.passwordVersion = passwordTargetVersion;
    }

    private static class Version1PasswordMigration implements PasswordMigration {

        @Override
        public void migrate(GerritCredentials credentials) {
            String legacyPassword = null;
            try {
                legacyPassword = PasswordSafe.getInstance().getPassword(null,
                    com.urswolfer.intellij.plugin.gerrit.GerritSettings.class, "GERRIT_SETTINGS_PASSWORD_KEY");
            } catch (PasswordSafeException e) {
                // Do nothing
            }

            if (legacyPassword == null) {
                return;
            }

            credentials.update(credentials.getHost(), credentials.getLogin(), legacyPassword);
        }
    }

    private interface PasswordMigration {
        void migrate(GerritCredentials credentials);
    }
}
