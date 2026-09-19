/*
 * Copyright 2026 Urs Wolfer
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
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Gerrit instances the user has credentials for, and their passwords.
 *
 * Passwords are kept under the account id rather than under its host or login, so editing either keeps the password,
 * and two accounts on the same host stay apart.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.APP)
@State(name = "GerritAccounts", storages = @Storage("gerrit_settings.xml"))
public final class GerritAccounts implements PersistentStateComponent<GerritAccounts.AccountsState> {

    public static final class AccountsState {
        /**
         * Whether the account of a version which had no accounts has already been taken over. Written even when it
         * is false, so that the component is always in the file and the answer survives a restart: without it,
         * removing the last account would look exactly like an installation which has never been migrated, and the
         * removed account would come back on the next start.
         */
        @Property(alwaysWrite = true) @Attribute("seeded") public boolean seeded = false;

        @XCollection(propertyElementName = "accounts") public List<GerritAccount> accounts = new ArrayList<>();
    }


    private static final String GERRIT_SETTINGS_PASSWORD_KEY = "GERRIT_SETTINGS_PASSWORD_KEY";

    /**
     * Where the password of the single configured instance was kept before accounts existed, and before that again
     * under the name of the class which held it. Both are still read, and never written or cleared: a user who skips
     * a release upgrades from whichever of them their installation last wrote.
     */
    private static final CredentialAttributes LEGACY_SETTINGS_ATTRIBUTES = new CredentialAttributes(
            CredentialAttributesKt.generateServiceName("Gerrit", GERRIT_SETTINGS_PASSWORD_KEY),
            GERRIT_SETTINGS_PASSWORD_KEY);
    private static final CredentialAttributes LEGACY_CLASS_ATTRIBUTES = new CredentialAttributes(
            GerritSettings.class.getName(),
            GERRIT_SETTINGS_PASSWORD_KEY);

    private final Object lock = new Object();
    private volatile List<GerritAccount> accounts = Collections.emptyList();
    private volatile boolean seeded;

    public static GerritAccounts getInstance() {
        return ApplicationManager.getApplication().getService(GerritAccounts.class);
    }

    @Override
    public AccountsState getState() {
        AccountsState state = new AccountsState();
        state.seeded = seeded;
        state.accounts = new ArrayList<>(accounts);
        return state;
    }

    @Override
    public void loadState(@NotNull AccountsState state) {
        synchronized (lock) {
            accounts = Collections.unmodifiableList(new ArrayList<>(state.accounts));
            seeded = state.seeded;
        }
    }

    /**
     * @return whether accounts are being kept for this installation, which is what tells an empty list apart from
     *         one which has simply never been filled in
     */
    public boolean isSeeded() {
        return seeded;
    }

    public List<GerritAccount> getAccounts() {
        seedFromSettingsOnce();
        return accounts;
    }

    public void setAccounts(List<GerritAccount> newAccounts) {
        synchronized (lock) {
            accounts = Collections.unmodifiableList(new ArrayList<>(newAccounts));
            seeded = true;
        }
    }

    /**
     * @return the account to use where nothing has picked one, which is the only account whenever there is exactly
     *         one; {@code null} while none is configured
     */
    @Nullable
    public GerritAccount getDefaultAccount() {
        List<GerritAccount> current = getAccounts();
        return current.isEmpty() ? null : current.get(0);
    }

    /**
     * The default account as far as it is already known, without seeding one from the settings of an earlier
     * version. Serialization uses this: growing an account while the state is being written would be a surprising
     * place for it to happen.
     */
    @Nullable
    public GerritAccount peekDefaultAccount() {
        List<GerritAccount> current = accounts;
        return current.isEmpty() ? null : current.get(0);
    }

    @Nullable
    public GerritAccount findById(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (GerritAccount account : getAccounts()) {
            if (account.id.equals(id)) {
                return account;
            }
        }
        return null;
    }

    /**
     * Adds an account, or replaces the stored copy of one which is already known by its id.
     */
    public void put(GerritAccount account) {
        synchronized (lock) {
            List<GerritAccount> updated = new ArrayList<>(accounts);
            int index = updated.indexOf(account);
            if (index >= 0) {
                updated.set(index, account);
            } else {
                updated.add(account);
            }
            accounts = Collections.unmodifiableList(updated);
            seeded = true;
        }
    }

    public void remove(GerritAccount account) {
        synchronized (lock) {
            List<GerritAccount> updated = new ArrayList<>(accounts);
            updated.remove(account);
            accounts = Collections.unmodifiableList(updated);
            // not through forgetPassword: putting the account back is exactly what it must not do here
            clearPasswords(account);
        }
    }

    /**
     * Turns the single host and login of a version which had no accounts into one account. Runs once per
     * installation, and only while no account is stored: an empty list which the user emptied themselves stays empty
     * because the settings it would be seeded from are cleared along with it.
     */
    private void seedFromSettingsOnce() {
        if (seeded) {
            return;
        }
        GerritSettings settings = GerritSettings.getInstance();
        seedFrom(settings.getLegacyHost(), settings.getLegacyLogin(), settings.getLegacyCloneBaseUrl());
    }

    /**
     * Turns the single host and login of a version which had no accounts into one account. Runs once per
     * installation, and only while no account is stored: an empty list which the user emptied themselves stays empty
     * because the settings it would be seeded from are cleared along with it.
     */
    void seedFrom(String host, String login, String cloneBaseUrl) {
        synchronized (lock) {
            if (seeded) {
                return;
            }
            seeded = true;
            if ((host == null || host.isEmpty()) && (login == null || login.isEmpty())) {
                return;
            }
            GerritAccount account = GerritAccount.create(host, login, cloneBaseUrl);
            account.usesLegacyPasswordKey = true;
            accounts = Collections.singletonList(account);
        }
    }

    /**
     * Reading the credential store blocks and must not happen on the event dispatch thread; UI code goes through the
     * modal progress in {@link GerritSettings} instead.
     */
    @NotNull
    public String getPassword(@Nullable GerritAccount account) {
        if (account == null) {
            return "";
        }
        PasswordSafe passwordSafe = PasswordSafe.getInstance();
        String password = read(passwordSafe, attributesFor(account));
        if (password == null && account.usesLegacyPasswordKey) {
            password = readLegacy(passwordSafe);
            if (password != null) {
                setPassword(account, password);
            }
        }
        return password != null ? password : "";
    }

    public void setPassword(@NotNull GerritAccount account, @Nullable String password) {
        synchronized (lock) {
            PasswordSafe.getInstance().set(attributesFor(account), new Credentials(null, password != null ? password : ""));
        }
    }

    public void forgetPassword(@NotNull GerritAccount account) {
        synchronized (lock) {
            clearPasswords(account);
            if (account.usesLegacyPasswordKey) {
                // clearing only the account's own key would let the next read hand the forgotten password back
                account.usesLegacyPasswordKey = false;
                if (accounts.contains(account)) {
                    put(account);
                }
            }
        }
    }

    /**
     * Clears the account's password, and the one an earlier version kept for it. Reading falls back to that older
     * key, so leaving it behind would both hand the password back and leave it in the credential store of someone
     * who asked for it to be gone.
     */
    private void clearPasswords(GerritAccount account) {
        PasswordSafe passwordSafe = PasswordSafe.getInstance();
        passwordSafe.set(attributesFor(account), null);
        if (account.usesLegacyPasswordKey) {
            passwordSafe.set(LEGACY_SETTINGS_ATTRIBUTES, null);
            passwordSafe.set(LEGACY_CLASS_ATTRIBUTES, null);
        }
    }

    /**
     * The password of an account which predates them can still be sitting under either of the two keys earlier
     * versions used. Neither is cleared once it has been read: that is what lets a downgrade, and a second upgrade
     * from a different installation, still find it.
     */
    @Nullable
    private String readLegacy(PasswordSafe passwordSafe) {
        String password = read(passwordSafe, LEGACY_SETTINGS_ATTRIBUTES);
        return password != null ? password : read(passwordSafe, LEGACY_CLASS_ATTRIBUTES);
    }

    @Nullable
    private static String read(PasswordSafe passwordSafe, CredentialAttributes attributes) {
        Credentials credentials = passwordSafe.get(attributes);
        String password = credentials != null ? credentials.getPasswordAsString() : null;
        return password == null || password.isEmpty() ? null : password;
    }

    static CredentialAttributes attributesFor(GerritAccount account) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("Gerrit", account.id), account.id);
    }
}
