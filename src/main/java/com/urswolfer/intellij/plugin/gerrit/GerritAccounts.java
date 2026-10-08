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
import com.intellij.openapi.application.Application;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
     * under the name of the class which held it. Both are still read and never written, and only cleared along with
     * the password of the account which reads them, or of the last account: a user who skips a release upgrades from
     * whichever of them their installation last wrote.
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
    private final Map<String, Integer> passwordVersions = new ConcurrentHashMap<>();
    private boolean loaded;
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
    public void noStateLoaded() { // a state loaded later, such as by syncing settings, is then a change
        synchronized (lock) {
            loaded = true;
        }
    }

    @Override
    public void loadState(@NotNull AccountsState state) {
        boolean reloaded;
        synchronized (lock) {
            reloaded = loaded;
            loaded = true;
            snapshot = new Snapshot(state.seeded, state.accounts);
        }
        // a later load replaces the accounts from outside, such as by syncing settings; the first one is no change,
        // and announcing it would reach listeners which are themselves still being set up
        if (reloaded) {
            publishChange();
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

    /**
     * Stores what the settings page edited. A password is stored before its account shows, so that nothing reads a
     * new account without one, and the password of a removed account is cleared once it is gone. An account added
     * elsewhere while the page was open, such as by the login dialog of another project, is kept. Where a password is
     * entered or an account removed, this blocks on the credential store, which must not happen on the event
     * dispatch thread.
     *
     * @param passwords  by account id, for the accounts whose password was entered
     * @param removedIds the accounts removed on the page
     */
    public void update(List<GerritAccount> edited, Map<String, String> passwords, Set<String> removedIds) {
        RuntimeException failure = null;
        synchronized (lock) {
            for (GerritAccount account : edited) {
                String password = passwords.get(account.id);
                if (password != null) {
                    try {
                        storePassword(account, password);
                    } catch (RuntimeException e) {
                        failure = collect(failure, e);
                    }
                }
            }
            List<GerritAccount> updated = new ArrayList<>(edited);
            List<GerritAccount> removed = new ArrayList<>();
            for (GerritAccount account : snapshot.accounts) {
                if (removedIds.contains(account.id)) {
                    removed.add(account);
                } else if (!updated.contains(account)) {
                    updated.add(account);
                }
            }
            snapshot = new Snapshot(true, updated);
            for (GerritAccount account : removed) {
                try {
                    clearPasswordOfRemoved(account);
                } catch (RuntimeException e) {
                    failure = collect(failure, e);
                }
            }
        }
        publishChange();
        rethrow(failure);
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
     * Adds or replaces an account along with its password, which is stored first, as in {@link #update}. Blocks on
     * the credential store.
     */
    public void put(GerritAccount account, String password) {
        RuntimeException failure = null;
        synchronized (lock) {
            try {
                storePassword(account, password);
            } catch (RuntimeException e) {
                failure = e;
            }
            List<GerritAccount> updated = new ArrayList<>(snapshot.accounts);
            int index = updated.indexOf(account);
            if (index >= 0) {
                updated.set(index, account);
            } else {
                updated.add(account);
            }
            snapshot = new Snapshot(true, updated);
        }
        publishChange();
        rethrow(failure);
    }

    private static RuntimeException collect(@Nullable RuntimeException failure, RuntimeException e) {
        if (failure == null) {
            return e;
        }
        failure.addSuppressed(e);
        return failure;
    }

    /**
     * A credential store which fails costs the password, not the edits around it: they are stored and announced
     * before the failure is reported, as it was when accounts and passwords were saved one after the other.
     */
    private static void rethrow(@Nullable RuntimeException failure) {
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Outside the lock: listeners read the accounts, and one which waited on a thread holding it would deadlock.
     * There is no application in the unit tests.
     */
    private static void publishChange() {
        Application application = ApplicationManager.getApplication();
        if (application != null) {
            application.getMessageBus().syncPublisher(GerritAccountsListener.TOPIC).accountsChanged();
        }
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
     * Reading the credential store blocks and must not happen on the event dispatch thread; UI code goes through a
     * modal progress instead, such as {@link GerritProjectAccount#getPasswordWithModalProgress}.
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
                    // not through storePassword: this is the move itself, and the account may have to make it again
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
    private void storePassword(GerritAccount account, @Nullable String password) {
        credentialStore.set(attributesFor(account), new Credentials(null, password != null ? password : ""));
        passwordVersions.merge(account.id, 1, Integer::sum);
        account.usesLegacyPasswordKey = false;
    }

    /**
     * @return how often the password of the account was saved or cleared since the IDE started, which tells a
     *         password someone entered again apart from the one it replaced even when the two are the same
     */
    public int getPasswordVersion(@NotNull GerritAccount account) {
        return passwordVersions.getOrDefault(account.id, 0);
    }

    /**
     * Clears the account's password, and the one an earlier version kept for it. Reading falls back to that older
     * key, so leaving it behind would both hand the password back and leave it in the credential store of someone
     * who asked for it to be gone.
     */
    private void clearStoredPassword(@NotNull GerritAccount account) {
        synchronized (lock) {
            credentialStore.set(attributesFor(account), null);
            passwordVersions.merge(account.id, 1, Integer::sum);
            if (account.usesLegacyPasswordKey) {
                credentialStore.set(LEGACY_SETTINGS_ATTRIBUTES, null);
                credentialStore.set(LEGACY_CLASS_ATTRIBUTES, null);
            }
        }
    }

    /**
     * Clears the password of an account which is no longer listed, and the key an earlier version used once no
     * account is left at all. Entering a password stops an account from reading that key but leaves it for a
     * downgrade, so removing the last account would otherwise leave the old password behind for good.
     */
    private void clearPasswordOfRemoved(@NotNull GerritAccount account) {
        synchronized (lock) {
            clearStoredPassword(account);
            if (snapshot.accounts.isEmpty()) {
                credentialStore.set(LEGACY_SETTINGS_ATTRIBUTES, null);
                credentialStore.set(LEGACY_CLASS_ATTRIBUTES, null);
            }
        }
    }

    /**
     * The password of an account which predates them can still be sitting under either of the two keys earlier
     * versions used. Reading does not clear them: that is what lets a downgrade, and a machine the accounts were
     * synced to, still find it.
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
