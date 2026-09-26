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

    /**
     * Whether accounts are kept and which ones they are is one fact, so it is held in one object behind one field.
     * Read separately, a save could catch the two halves mid-change and store "accounts are kept, there are none" -
     * after which nothing is ever migrated, because that is exactly what a migrated installation with no accounts
     * looks like. Writers replace the whole thing under the lock; readers take one look and see a matching pair.
     */
    static final class Snapshot {
        final boolean seeded;
        final List<GerritAccount> accounts;

        Snapshot(boolean seeded, List<GerritAccount> accounts) {
            this.seeded = seeded;
            this.accounts = Collections.unmodifiableList(new ArrayList<>(accounts));
        }
    }

    private final Object lock = new Object();
    private volatile Snapshot snapshot = new Snapshot(false, Collections.emptyList());

    public static GerritAccounts getInstance() {
        return ApplicationManager.getApplication().getService(GerritAccounts.class);
    }

    @Override
    public AccountsState getState() {
        Snapshot current = snapshot;
        AccountsState state = new AccountsState();
        state.seeded = current.seeded;
        state.accounts = new ArrayList<>(current.accounts);
        return state;
    }

    @Override
    public void loadState(@NotNull AccountsState state) {
        synchronized (lock) {
            snapshot = new Snapshot(state.seeded, state.accounts);
        }
    }

    /**
     * @return the accounts and whether they are kept, as one pair; callers which need both must not ask twice
     */
    Snapshot peek() {
        return snapshot;
    }

    /**
     * @return whether accounts are being kept for this installation, which is what tells an empty list apart from
     *         one which has simply never been filled in
     */
    public boolean isSeeded() {
        return snapshot.seeded;
    }

    public List<GerritAccount> getAccounts() {
        seedFromSettingsOnce();
        return snapshot.accounts;
    }

    public void setAccounts(List<GerritAccount> newAccounts) {
        synchronized (lock) {
            snapshot = new Snapshot(true, newAccounts);
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
        List<GerritAccount> current = snapshot.accounts;
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
            List<GerritAccount> updated = new ArrayList<>(snapshot.accounts);
            int index = updated.indexOf(account);
            if (index >= 0) {
                updated.set(index, account);
            } else {
                updated.add(account);
            }
            snapshot = new Snapshot(true, updated);
        }
    }

    public void remove(GerritAccount account) {
        synchronized (lock) {
            List<GerritAccount> updated = new ArrayList<>(snapshot.accounts);
            updated.remove(account);
            snapshot = new Snapshot(snapshot.seeded, updated);
        }
        // not through forgetPassword: putting the account back is exactly what it must not do here
        clearStoredPassword(account);
    }

    /**
     * Turns the single host and login of a version which had no accounts into one account. Runs once per
     * installation, and only while no account is stored: an empty list which the user emptied themselves stays empty
     * because the settings it would be seeded from are cleared along with it.
     */
    private void seedFromSettingsOnce() {
        if (snapshot.seeded) {
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
            if (snapshot.seeded) {
                return;
            }
            if ((host == null || host.isEmpty()) && (login == null || login.isEmpty())) {
                snapshot = new Snapshot(true, Collections.emptyList());
                return;
            }
            GerritAccount account = GerritAccount.create(host, login, cloneBaseUrl);
            account.usesLegacyPasswordKey = true;
            snapshot = new Snapshot(true, Collections.singletonList(account));
        }
    }

    /**
     * The credential store, behind an interface so that what is done with it can be tested without one.
     */
    interface CredentialStore {
        @Nullable
        Credentials get(CredentialAttributes attributes);

        void set(CredentialAttributes attributes, @Nullable Credentials credentials);
    }

    private volatile CredentialStore credentialStore = new CredentialStore() {
        @Override
        public Credentials get(CredentialAttributes attributes) {
            return PasswordSafe.getInstance().get(attributes);
        }

        @Override
        public void set(CredentialAttributes attributes, Credentials credentials) {
            PasswordSafe.getInstance().set(attributes, credentials);
        }
    };

    void setCredentialStore(CredentialStore credentialStore) { // for tests
        this.credentialStore = credentialStore;
    }

    /**
     * Reading the credential store blocks and must not happen on the event dispatch thread; UI code goes through the
     * modal progress in {@link GerritSettings} instead.
     *
     * The whole lookup runs under the lock: a password set or forgotten between reading the older key and writing
     * what it held to the account's own would otherwise be overwritten, or brought back.
     */
    @NotNull
    public String getPassword(@Nullable GerritAccount account) {
        if (account == null) {
            return "";
        }
        synchronized (lock) {
            Credentials stored = credentialStore.get(attributesFor(account));
            if (stored != null) {
                // an entry which is there decides, even when it holds nothing: that is a password someone cleared
                String password = stored.getPasswordAsString();
                return password != null ? password : "";
            }
            if (account.usesLegacyPasswordKey) {
                String legacy = readLegacy();
                if (legacy != null) {
                    // not through setPassword: this is the move itself, and the account may have to make it again
                    // on another machine, whose credential store did not travel with it
                    credentialStore.set(attributesFor(account), new Credentials(null, legacy));
                    return legacy;
                }
            }
            return "";
        }
    }

    /**
     * Saves a password someone entered. That settles where this account's password lives, so the key an earlier
     * version used stops being consulted - otherwise clearing the password here would hand the old one back.
     */
    public void setPassword(@NotNull GerritAccount account, @Nullable String password) {
        synchronized (lock) {
            credentialStore.set(attributesFor(account), new Credentials(null, password != null ? password : ""));
            if (account.usesLegacyPasswordKey) {
                account.usesLegacyPasswordKey = false;
                if (snapshot.accounts.contains(account)) {
                    put(account);
                }
            }
        }
    }

    public void forgetPassword(@NotNull GerritAccount account) {
        synchronized (lock) {
            clearStoredPassword(account);
            if (account.usesLegacyPasswordKey) {
                account.usesLegacyPasswordKey = false;
                if (snapshot.accounts.contains(account)) {
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
    public void clearStoredPassword(@NotNull GerritAccount account) {
        synchronized (lock) {
            credentialStore.set(attributesFor(account), null);
            if (account.usesLegacyPasswordKey) {
                credentialStore.set(LEGACY_SETTINGS_ATTRIBUTES, null);
                credentialStore.set(LEGACY_CLASS_ATTRIBUTES, null);
            }
        }
    }

    /**
     * The password of an account which predates them can still be sitting under either of the two keys earlier
     * versions used. Neither is cleared once it has been read: that is what lets a downgrade, and a machine the
     * accounts were synced to, still find it.
     */
    @Nullable
    private String readLegacy() {
        String password = read(LEGACY_SETTINGS_ATTRIBUTES);
        return password != null ? password : read(LEGACY_CLASS_ATTRIBUTES);
    }

    @Nullable
    private String read(CredentialAttributes attributes) {
        Credentials credentials = credentialStore.get(attributes);
        String password = credentials != null ? credentials.getPasswordAsString() : null;
        return password == null || password.isEmpty() ? null : password;
    }

    static CredentialAttributes attributesFor(GerritAccount account) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("Gerrit", account.id), account.id);
    }
}
