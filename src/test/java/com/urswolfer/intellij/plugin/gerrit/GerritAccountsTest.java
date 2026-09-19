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
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.List;

public class GerritAccountsTest {

    private static final String PASSWORD_KEY = "GERRIT_SETTINGS_PASSWORD_KEY";

    @Test
    public void testPasswordOfAnAccountIsKeptUnderItsId() {
        GerritAccount account = GerritAccount.create("https://gerrit.example.com", "jdoe", "");

        CredentialAttributes attributes = GerritAccounts.attributesFor(account);

        Assert.assertEquals(attributes.getServiceName(),
            CredentialAttributesKt.generateServiceName("Gerrit", account.id));
        Assert.assertEquals(attributes.getUserName(), account.id);
    }

    /**
     * A user who skips a release upgrades from whichever key their installation last wrote, so both have to keep
     * naming exactly what those versions named.
     */
    @Test
    public void testBothKeysOfEarlierVersionsAreStillRead() throws Exception {
        CredentialAttributes settingsKey = constant("LEGACY_SETTINGS_ATTRIBUTES");
        Assert.assertEquals(settingsKey.getServiceName(),
            CredentialAttributesKt.generateServiceName("Gerrit", PASSWORD_KEY));
        Assert.assertEquals(settingsKey.getUserName(), PASSWORD_KEY);

        CredentialAttributes classKey = constant("LEGACY_CLASS_ATTRIBUTES");
        Assert.assertEquals(classKey.getServiceName(), "com.urswolfer.intellij.plugin.gerrit.GerritSettings");
        Assert.assertEquals(classKey.getUserName(), PASSWORD_KEY);
    }

    @Test
    public void testAccountsRoundTripThroughTheSerializer() {
        GerritAccount account = GerritAccount.create("https://gerrit.example.com", "jdoe", "https://clone.example.com");
        account.usesLegacyPasswordKey = true;

        GerritAccounts.AccountsState state = new GerritAccounts.AccountsState();
        state.seeded = true;
        state.accounts = java.util.Collections.singletonList(account);

        Element element = XmlSerializer.serialize(state);
        GerritAccounts.AccountsState loaded = XmlSerializer.deserialize(element, GerritAccounts.AccountsState.class);
        java.util.List<GerritAccount> read = loaded.accounts;

        Assert.assertTrue(new XMLOutputter().outputString(element).contains("<account"), new XMLOutputter().outputString(element));
        Assert.assertTrue(loaded.seeded);
        Assert.assertEquals(read.size(), 1);
        Assert.assertEquals(read.get(0).id, account.id);
        Assert.assertEquals(read.get(0).login, "jdoe");
        Assert.assertEquals(read.get(0).host, "https://gerrit.example.com");
        Assert.assertEquals(read.get(0).cloneBaseUrl, "https://clone.example.com");
        Assert.assertTrue(read.get(0).usesLegacyPasswordKey);
    }

    /**
     * Upgrading from a version without accounts has to land on the settings the user already had, and the account
     * has to stay marked until its password has been moved across.
     */
    @Test
    public void testSettingsOfAnEarlierVersionBecomeOneAccount() {
        GerritAccounts accounts = new GerritAccounts();

        accounts.seedFrom("https://gerrit.example.com", "jdoe", "https://clone.example.com");

        List<GerritAccount> seeded = accounts.getAccounts();
        Assert.assertEquals(seeded.size(), 1);
        Assert.assertEquals(seeded.get(0).host, "https://gerrit.example.com");
        Assert.assertEquals(seeded.get(0).login, "jdoe");
        Assert.assertEquals(seeded.get(0).cloneBaseUrl, "https://clone.example.com");
        Assert.assertTrue(seeded.get(0).usesLegacyPasswordKey);
        Assert.assertFalse(seeded.get(0).id.isEmpty());
    }

    @Test
    public void testNothingConfiguredSeedsNoAccount() {
        GerritAccounts accounts = new GerritAccounts();

        accounts.seedFrom("", "", "");

        Assert.assertTrue(accounts.getAccounts().isEmpty());
        Assert.assertNull(accounts.getDefaultAccount());
    }

    /**
     * Loading a stored list must not leave the seeding armed, or an upgraded installation would grow a duplicate of
     * the account it already has.
     */
    @Test
    public void testStoredAccountsAreNotSeededOver() {
        GerritAccounts accounts = new GerritAccounts();
        GerritAccount stored = GerritAccount.create("https://other.example.com", "someone", "");

        accounts.loadState(stateOf(true, stored));
        accounts.seedFrom("https://gerrit.example.com", "jdoe", "");

        Assert.assertEquals(accounts.getAccounts().size(), 1);
        Assert.assertEquals(accounts.getDefaultAccount().host, "https://other.example.com");
    }

    /**
     * Removing the last account looked exactly like an installation which had never been migrated, so the account,
     * and the password an earlier version had kept for it, came back on the next start.
     */
    @Test
    public void testRemovingTheLastAccountDoesNotBringItBackOnTheNextStart() {
        GerritAccounts accounts = new GerritAccounts();

        accounts.loadState(stateOf(true)); // what is stored after the user removed their only account
        accounts.seedFrom("https://gerrit.example.com", "jdoe", "");

        Assert.assertTrue(accounts.getAccounts().isEmpty());
        Assert.assertNull(accounts.getDefaultAccount());
    }

    @Test
    public void testSeedingIsRememberedAcrossRestarts() {
        GerritAccounts accounts = new GerritAccounts();
        accounts.seedFrom("https://gerrit.example.com", "jdoe", "");

        Assert.assertTrue(accounts.getState().seeded);
        Assert.assertTrue(accounts.isSeeded());
    }

    // --- what happens to the password itself, through a credential store the test can look inside ---

    @Test
    public void testTheAccountsOwnPasswordIsUsedWhenItHasOne() {
        FakeCredentialStore store = new FakeCredentialStore();
        GerritAccounts accounts = seeded(store);
        GerritAccount account = accounts.getDefaultAccount();
        store.put(GerritAccounts.attributesFor(account), "current");
        store.put(legacySettingsKey(), "old");

        Assert.assertEquals(accounts.getPassword(account), "current");
    }

    @Test
    public void testAPasswordUnderTheKeyOfAnEarlierVersionIsFoundAndMovedAcross() {
        FakeCredentialStore store = new FakeCredentialStore();
        GerritAccounts accounts = seeded(store);
        GerritAccount account = accounts.getDefaultAccount();
        store.put(legacySettingsKey(), "old");

        Assert.assertEquals(accounts.getPassword(account), "old");
        Assert.assertEquals(store.passwordAt(GerritAccounts.attributesFor(account)), "old");
        // and the key it came from is left alone, for a machine which has not done this yet
        Assert.assertEquals(store.passwordAt(legacySettingsKey()), "old");
        Assert.assertTrue(account.usesLegacyPasswordKey);
    }

    @Test
    public void testTheOlderOfTheTwoKeysIsUsedWhenTheNewerHasNothing() {
        FakeCredentialStore store = new FakeCredentialStore();
        GerritAccounts accounts = seeded(store);
        GerritAccount account = accounts.getDefaultAccount();
        store.put(legacyClassKey(), "ancient");

        Assert.assertEquals(accounts.getPassword(account), "ancient");
    }

    /**
     * Clearing the password field stores an empty password rather than removing the entry. Treating that as "no
     * password" sent the read to the key of an earlier version, which still had the old one, and put it back.
     */
    @Test
    public void testClearingThePasswordDoesNotBringTheOldOneBack() {
        FakeCredentialStore store = new FakeCredentialStore();
        GerritAccounts accounts = seeded(store);
        GerritAccount account = accounts.getDefaultAccount();
        store.put(legacySettingsKey(), "old");

        accounts.setPassword(account, "");

        Assert.assertEquals(accounts.getPassword(account), "");
    }

    /**
     * Entering a password settles where this account's password lives, so the key of an earlier version stops being
     * consulted - on every machine, since the accounts travel.
     */
    @Test
    public void testEnteringAPasswordStopsTheOlderKeyBeingConsulted() {
        FakeCredentialStore store = new FakeCredentialStore();
        GerritAccounts accounts = seeded(store);
        GerritAccount account = accounts.getDefaultAccount();
        store.put(legacySettingsKey(), "old");

        accounts.setPassword(account, "new");

        Assert.assertFalse(account.usesLegacyPasswordKey);
        Assert.assertEquals(accounts.getPassword(account), "new");
    }

    @Test
    public void testForgettingAPasswordClearsTheOlderKeysToo() {
        FakeCredentialStore store = new FakeCredentialStore();
        GerritAccounts accounts = seeded(store);
        GerritAccount account = accounts.getDefaultAccount();
        store.put(GerritAccounts.attributesFor(account), "current");
        store.put(legacySettingsKey(), "old");
        store.put(legacyClassKey(), "ancient");

        accounts.forgetPassword(account);

        Assert.assertEquals(accounts.getPassword(account), "");
        Assert.assertNull(store.passwordAt(legacySettingsKey()));
        Assert.assertNull(store.passwordAt(legacyClassKey()));
    }

    /**
     * Forgetting a password used to put the account back, so removing one returned it to the list.
     */
    @Test
    public void testRemovingAnAccountClearsItsPasswordAndDoesNotPutItBack() {
        FakeCredentialStore store = new FakeCredentialStore();
        GerritAccounts accounts = seeded(store);
        GerritAccount account = accounts.getDefaultAccount();
        store.put(GerritAccounts.attributesFor(account), "current");

        accounts.remove(account);

        Assert.assertTrue(accounts.getAccounts().isEmpty());
        Assert.assertNull(store.passwordAt(GerritAccounts.attributesFor(account)));
    }

    private static GerritAccounts seeded(GerritAccounts.CredentialStore store) {
        GerritAccounts accounts = new GerritAccounts();
        accounts.setCredentialStore(store);
        accounts.seedFrom("https://gerrit.example.com", "jdoe", "");
        return accounts;
    }

    private static CredentialAttributes legacySettingsKey() throws RuntimeException {
        try {
            return constant("LEGACY_SETTINGS_ATTRIBUTES");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CredentialAttributes legacyClassKey() {
        try {
            return constant("LEGACY_CLASS_ATTRIBUTES");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class FakeCredentialStore implements GerritAccounts.CredentialStore {
        private final java.util.Map<String, Credentials> entries = new java.util.HashMap<>();

        void put(CredentialAttributes attributes, String password) {
            entries.put(key(attributes), new Credentials(null, password));
        }

        String passwordAt(CredentialAttributes attributes) {
            Credentials credentials = entries.get(key(attributes));
            return credentials != null ? credentials.getPasswordAsString() : null;
        }

        @Override
        public Credentials get(CredentialAttributes attributes) {
            return entries.get(key(attributes));
        }

        @Override
        public void set(CredentialAttributes attributes, Credentials credentials) {
            if (credentials == null) {
                entries.remove(key(attributes));
            } else {
                entries.put(key(attributes), credentials);
            }
        }

        private static String key(CredentialAttributes attributes) {
            return attributes.getServiceName() + "\u0000" + attributes.getUserName();
        }
    }

    private static GerritAccounts.AccountsState stateOf(boolean seeded, GerritAccount... accounts) {
        GerritAccounts.AccountsState state = new GerritAccounts.AccountsState();
        state.seeded = seeded;
        state.accounts = java.util.Arrays.asList(accounts);
        return state;
    }

    /**
     * The accounts are synced between machines but the credential store is not, so a machine which has not moved
     * the password yet still has to know that it may be under the key an earlier version used.
     */
    @Test
    public void testTheLegacyKeyMarkerSurvivesBeingCopied() {
        GerritAccount account = GerritAccount.create("https://gerrit.example.com", "jdoe", "");
        account.usesLegacyPasswordKey = true;

        Assert.assertTrue(account.copy().usesLegacyPasswordKey);
    }

    private static CredentialAttributes constant(String fieldName) throws Exception {
        Field field = GerritAccounts.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (CredentialAttributes) field.get(null);
    }
}
