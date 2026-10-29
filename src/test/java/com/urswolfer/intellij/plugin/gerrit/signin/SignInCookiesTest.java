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

package com.urswolfer.intellij.plugin.gerrit.signin;

import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.signin.SignInCookies.StoredCookie;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class SignInCookiesTest {

    private static class FakeStore implements SignInCookies.CredentialStore {
        final Map<String, String> entries = new HashMap<>();
        final List<String> reads = new ArrayList<>();

        @Override
        public String read(String origin) {
            reads.add(origin);
            return entries.get(origin);
        }

        @Override
        public void write(String origin, String cookies) {
            if (cookies == null) {
                entries.remove(origin);
            } else {
                entries.put(origin, cookies);
            }
        }
    }

    @Test
    public void testOriginIgnoresPathCaseAndDefaultPort() {
        Assert.assertEquals(SignInCookies.origin("https://Review.Example.org/gerrit/"), "https://review.example.org");
        Assert.assertEquals(SignInCookies.origin("https://review.example.org:443"), "https://review.example.org");
        Assert.assertEquals(SignInCookies.origin("http://review.example.org:80/"), "http://review.example.org");
        Assert.assertEquals(SignInCookies.origin("http://localhost:8090"), "http://localhost:8090");
        Assert.assertEquals(SignInCookies.origin("https://gerrit_srv.corp.local:8443/r"), "https://gerrit_srv.corp.local:8443");
        Assert.assertEquals(SignInCookies.hostName("https://gerrit_srv.corp.local/r"), "gerrit_srv.corp.local");
        Assert.assertNull(SignInCookies.origin("review.example.org"));
        Assert.assertNull(SignInCookies.origin(""));
        Assert.assertNull(SignInCookies.origin(null));
    }

    @Test
    public void testCookiesKeptAsTheyWereStored() {
        List<StoredCookie> cookies = Arrays.asList(
            new StoredCookie("AzureAppProxyAccessCookie_abc_1.3", "v=1/2+3", 1767225600000L),
            new StoredCookie("session", "x", 0));

        List<StoredCookie> parsed = SignInCookies.parse(SignInCookies.format(cookies));

        Assert.assertEquals(parsed.size(), 2);
        Assert.assertEquals(parsed.get(0).name, "AzureAppProxyAccessCookie_abc_1.3");
        Assert.assertEquals(parsed.get(0).value, "v=1/2+3");
        Assert.assertEquals(parsed.get(0).expires, 1767225600000L);
        Assert.assertEquals(parsed.get(1).value, "x");
        Assert.assertEquals(parsed.get(1).expires, 0);
    }

    @Test
    public void testDamagedLinesSkipped() {
        Assert.assertEquals(SignInCookies.parse("broken\n\tno-name\t0\nok\tv\tsoon\nfine\tv\t0\n").size(), 1);
        Assert.assertTrue(SignInCookies.parse(null).isEmpty());
    }

    /**
     * Every host is one without cookies until someone signs in, which is every host of an IDE without the sign-in.
     */
    @Test
    public void testHostWithoutCookiesNeverReadsTheCredentialStore() {
        SignInCookies signInCookies = new SignInCookies();
        FakeStore store = new FakeStore();
        signInCookies.setCredentialStore(store);

        Assert.assertTrue(signInCookies.get("https://review.example.org").isEmpty());
        Assert.assertTrue(signInCookies.get("not a url").isEmpty());
        Assert.assertEquals(store.reads, Collections.emptyList());
    }

    /**
     * A credential store which does not save, or is not unlocked, would be asked several times per request.
     */
    @Test
    public void testHostWhoseCookiesAreNotReadAskedForAgainOnlyLater() {
        FakeStore store = new FakeStore();
        SignInCookies before = new SignInCookies();
        before.setCredentialStore(new FakeStore());
        before.set("https://review.example.org", Collections.singletonList(new StoredCookie("proxy", "1", 0)));
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(store);
        signInCookies.loadState(before.getState());

        Assert.assertTrue(signInCookies.get("https://review.example.org").isEmpty());
        Assert.assertTrue(signInCookies.get("https://review.example.org").isEmpty());
        Assert.assertEquals(store.reads.size(), 1);

        signInCookies.set("https://review.example.org", Collections.singletonList(new StoredCookie("proxy", "2", 0)));
        Assert.assertEquals(signInCookies.get("https://review.example.org").get(0).value, "2");
    }

    /**
     * The requests go without the cookies rather than fail.
     */
    @Test
    public void testCredentialStoreWhichFailsToReadGivesNoCookies() {
        SignInCookies before = new SignInCookies();
        before.setCredentialStore(new FakeStore());
        before.set("https://review.example.org", Collections.singletonList(new StoredCookie("proxy", "1", 0)));
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore() {
            @Override
            public String read(String origin) {
                throw new IllegalStateException("broken");
            }
        });
        signInCookies.loadState(before.getState());

        Assert.assertTrue(signInCookies.get("https://review.example.org").isEmpty());
        Assert.assertTrue(signInCookies.isSignedIn("https://review.example.org/accounts/1/avatar"));
    }

    /**
     * A credential store which cannot write keeps the user signed in until the IDE closes.
     */
    @Test
    public void testCookiesKeptWhereTheCredentialStoreFails() {
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore() {
            @Override
            public void write(String origin, String cookies) {
                throw new IllegalStateException("no keychain");
            }
        });

        signInCookies.set("https://review.example.org", Collections.singletonList(new StoredCookie("proxy", "1", 0)));

        Assert.assertEquals(signInCookies.get("https://review.example.org").get(0).value, "1");
        Assert.assertTrue(signInCookies.isSignedIn("https://review.example.org"));
        // nor does a restart look for them
        Assert.assertTrue(signInCookies.getState().hosts.isEmpty());
    }

    @Test
    public void testCookiesGoToEveryUrlOfTheHost() {
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore());
        signInCookies.set("https://review.example.org/gerrit", Collections.singletonList(
            new StoredCookie("proxy", "1", 0)));

        Assert.assertEquals(signInCookies.get("https://REVIEW.example.org:443/").get(0).name, "proxy");
        Assert.assertTrue(signInCookies.get("https://other.example.org").isEmpty());
        Assert.assertTrue(signInCookies.get("http://review.example.org").isEmpty());
    }

    @Test
    public void testCookiesReadFromTheCredentialStoreAfterARestart() {
        FakeStore store = new FakeStore();
        SignInCookies before = new SignInCookies();
        before.setCredentialStore(store);
        before.set("https://review.example.org", Collections.singletonList(new StoredCookie("proxy", "1", 0)));

        SignInCookies after = new SignInCookies();
        after.setCredentialStore(store);
        after.loadState(before.getState());

        Assert.assertEquals(after.get("https://review.example.org").get(0).value, "1");
        Assert.assertEquals(store.reads, Collections.singletonList("https://review.example.org"));
        after.get("https://review.example.org");
        Assert.assertEquals(store.reads.size(), 1, "read once, then from memory");
    }

    @Test
    public void testAuthDataGetsTheCookiesOfItsHostWhichHaveNotExpired() {
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore());
        signInCookies.set("https://review.example.org", Arrays.asList(new StoredCookie("proxy", "1", 0),
            new StoredCookie("gone", "x", 1000), new StoredCookie("later", "y", Long.MAX_VALUE)));

        GerritAuthData authData = signInCookies.withCookies(
            new GerritAuthData.Basic("https://review.example.org/gerrit", "me", "secret"));

        Assert.assertEquals(authData.getCookies().keySet(), new HashSet<>(Arrays.asList("proxy", "later")));
        Assert.assertEquals(authData.getLogin(), "me");
        Assert.assertEquals(authData.getPassword(), "secret");
        Assert.assertTrue(authData.isLoginAndPasswordAvailable());
        Assert.assertTrue(signInCookies.withCookies(new GerritAuthData.Basic("https://other.example.org"))
            .getCookies().isEmpty());
    }

    /**
     * The sign-in window tries the cookies it found before storing them, which must not be helped by stored ones.
     */
    @Test
    public void testCookiesOfTheAuthDataRatherThanTheStoredOnes() {
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore());
        signInCookies.set("https://review.example.org", Arrays.asList(new StoredCookie("proxy", "old", 0),
            new StoredCookie("other", "still valid", 0)));

        GerritAuthData authData = signInCookies.withCookies(new GerritAuthData.Basic("https://review.example.org") {
            @Override
            public Map<String, String> getCookies() {
                return Collections.singletonMap("proxy", "new");
            }
        });

        Assert.assertEquals(authData.getCookies(), Collections.singletonMap("proxy", "new"));
    }

    @Test
    public void testCookieHeaderOfTheHost() {
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore());
        signInCookies.set("https://review.example.org", Arrays.asList(new StoredCookie("a", "1", 0),
            new StoredCookie("gone", "x", 1000), new StoredCookie("b", "2", 0)));

        Assert.assertEquals(signInCookies.cookieHeader("https://review.example.org/accounts/1/avatar?s=32"), "a=1; b=2");
        Assert.assertEquals(signInCookies.cookieHeader("https://avatars.example.org/1.png"), "");
        Assert.assertEquals(signInCookies.cookieHeader("http://review.example.org/accounts/1/avatar"), "");
    }

    /**
     * The REST client keeps the auth data, and starts a new login when what it says changes, such as after a sign-in.
     */
    @Test
    public void testAuthDataReadsTheCookiesPerCall() {
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore());
        GerritAuthData authData = signInCookies.withCookies(new GerritAuthData.Basic("https://review.example.org"));
        Assert.assertTrue(authData.getCookies().isEmpty());

        signInCookies.set("https://review.example.org", Collections.singletonList(new StoredCookie("proxy", "1", 0)));

        Assert.assertEquals(authData.getCookies(), Collections.singletonMap("proxy", "1"));
    }

    @Test
    public void testCookiesOfAHostForgottenWithItsLastAccount() {
        FakeStore store = new FakeStore();
        SignInCookies before = new SignInCookies();
        before.setCredentialStore(store);
        before.set("https://kept.example.org", Collections.singletonList(new StoredCookie("a", "1", 0)));
        before.set("https://gone.example.org", Collections.singletonList(new StoredCookie("b", "2", 0)));
        SignInCookies signInCookies = new SignInCookies(); // after a restart
        signInCookies.setCredentialStore(store);
        signInCookies.loadState(before.getState());

        signInCookies.forgetUnused(Collections.singletonList(GerritAccount.create("https://kept.example.org/r", "me", "")));

        Assert.assertEquals(signInCookies.get("https://kept.example.org").size(), 1);
        Assert.assertTrue(signInCookies.get("https://gone.example.org").isEmpty());
        Assert.assertEquals(store.entries.keySet(), Collections.singleton("https://kept.example.org"));
        Assert.assertEquals(signInCookies.getState().hosts, Collections.singletonList("https://kept.example.org"));
    }

    @Test
    public void testCookiesWhichCannotBeRemovedKeptForTheNextTry() {
        FakeStore store = new FakeStore();
        SignInCookies before = new SignInCookies();
        before.setCredentialStore(store);
        before.set("https://locked.example.org", Collections.singletonList(new StoredCookie("a", "1", 0)));
        before.set("https://gone.example.org", Collections.singletonList(new StoredCookie("b", "2", 0)));
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore() {
            @Override
            public void write(String origin, String cookies) {
                if (origin.contains("locked")) {
                    throw new IllegalStateException("locked");
                }
                store.write(origin, cookies);
            }
        });
        signInCookies.loadState(before.getState());

        signInCookies.forgetUnused(Collections.emptyList());

        Assert.assertEquals(signInCookies.getState().hosts, Collections.singletonList("https://locked.example.org"));
        Assert.assertEquals(store.entries.keySet(), Collections.singleton("https://locked.example.org"));
    }

    /**
     * Its account may still be waiting on the settings page to be applied.
     */
    @Test
    public void testCookiesOfAHostSignedInToSinceTheStartKept() {
        SignInCookies signInCookies = new SignInCookies();
        signInCookies.setCredentialStore(new FakeStore());
        signInCookies.set("https://new.example.org", Collections.singletonList(new StoredCookie("a", "1", 0)));

        signInCookies.forgetUnused(Collections.emptyList());

        Assert.assertEquals(signInCookies.get("https://new.example.org").size(), 1);
    }
}
