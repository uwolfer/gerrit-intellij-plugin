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

package com.urswolfer.intellij.plugin.gerrit.extension;

import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GerritHttpAuthDataProviderTest {

    private static final String URL = "https://review.example.com";

    private final GerritAccount jdoe = GerritAccount.create("https://review.example.com", "jdoe", "");
    private final GerritAccount bot = GerritAccount.create("https://review.example.com", "bot", "");
    private final GerritAccount other = GerritAccount.create("https://other.example.com", "jdoe", "");
    private final List<GerritAccount> all = Arrays.asList(jdoe, bot, other);

    @Test
    public void testTheAccountOfTheProjectGoesFirst() {
        Assert.assertSame(accountFor(bot, all, URL, null), bot);
    }

    @Test
    public void testTwoLoginsOnTheInstanceLeaveNoAnswerWithoutTheProject() {
        Assert.assertNull(accountFor(null, all, URL, null));
    }

    @Test
    public void testTheLoginInTheUrlPicksTheAccount() {
        Assert.assertSame(accountFor(null, all, URL, "bot"), bot);
    }

    /**
     * Git sends the password with the login of the url, so it must never get the password of another login.
     */
    @Test
    public void testTheAccountOfTheProjectIsPassedOverForAnotherLoginInTheUrl() {
        Assert.assertSame(accountFor(jdoe, all, URL, "bot"), bot);
        Assert.assertNull(accountFor(jdoe, all, URL, "stranger"));
    }

    @Test
    public void testTheLoginInTheUrlMatchesInAnyCase() {
        Assert.assertSame(accountFor(null, all, URL, "Bot"), bot);
    }

    /**
     * Git hands over the login of "https://jdoe%40example.com@review.example.com" as it is in the url.
     */
    @Test
    public void testALoginWhichIsAnEmailAddressIsDecoded() {
        GerritAccount email = GerritAccount.create("https://review.example.com", "jdoe+git@example.com", "");
        Assert.assertSame(accountFor(null, Arrays.asList(email, jdoe), URL, "jdoe+git%40example.com"), email);
        Assert.assertNull(accountFor(null, Arrays.asList(jdoe), URL, "jdoe%"));
    }

    /**
     * A git which decodes the login first cuts it at its first '@', handing over "jdoe" and
     * "https://example.com@review.example.com".
     */
    @Test
    public void testALoginWhichIsAnEmailAddressIsPutBackTogether() {
        GerritAccount email = GerritAccount.create("https://review.example.com", "jdoe@example.com", "");
        GerritAccount elsewhere = GerritAccount.create("https://review.example.com", "jdoe@example.org", "");
        List<GerritAccount> accounts = Arrays.asList(elsewhere, email, jdoe);
        Assert.assertSame(accountFor(elsewhere, accounts, "https://example.com@review.example.com", "jdoe"), email);
        Assert.assertSame(accountFor(email, accounts, URL, "jdoe"), jdoe);
        Assert.assertNull(accountFor(null, Arrays.asList(email), URL, "jdoe"));
    }

    @Test
    public void testAnAccountOnAnotherInstanceIsNotUsed() {
        Assert.assertSame(accountFor(other, all, URL, "bot"), bot);
        Assert.assertNull(accountFor(null, Arrays.asList(other), URL, "jdoe"));
    }

    @Test
    public void testThePasswordIsNotHandedToAnotherSchemeOrPort() {
        Assert.assertNull(accountFor(jdoe, all, "http://review.example.com", null));
        Assert.assertNull(accountFor(jdoe, all, "https://review.example.com:8443", null));
    }

    /**
     * Up to 2024.1 git hands over every url as "http", an https remote included, and the remotes tell the scheme.
     */
    @Test
    public void testTheRemotesTellTheSchemeWhereGitDoesNot() {
        GerritAccount onPort = GerritAccount.create("https://review.example.com:8443", "jdoe", "");
        List<String> remotes = Arrays.asList("https://jdoe@review.example.com/a/app",
            "https://review.example.com:8443/b", "git@github.com:org/lib.git");
        Assert.assertSame(withoutScheme(jdoe, all, "http://review.example.com", remotes), jdoe);
        Assert.assertSame(withoutScheme(onPort, all, "http://review.example.com:8443", remotes), onPort);
        Assert.assertNull(withoutScheme(onPort, Arrays.asList(onPort), "http://review.example.com", remotes));
        Assert.assertNull(withoutScheme(jdoe, Arrays.asList(jdoe), "http://review.example.com:8080", remotes));
    }

    /**
     * Where git does not tell the scheme, a remote over plain http may be the one it talks to, and a clone has no
     * remote yet to tell.
     */
    @Test
    public void testAnHttpsPasswordNeedsAnHttpsRemoteAndNoPlainOneWhereGitDoesNotTellTheScheme() {
        Assert.assertNull(withoutScheme(jdoe, all, "http://review.example.com",
            Arrays.asList("https://review.example.com/app", "http://Review.example.com/lib")));
        Assert.assertNull(withoutScheme(jdoe, all, "http://review.example.com", Collections.emptyList()));
        Assert.assertSame(withoutScheme(jdoe, all, "http://review.example.com",
            Arrays.asList("https://review.example.com/app", "http://review.example.com:8080/lib")), jdoe);

        GerritAccount plain = GerritAccount.create("http://review.example.com", "jdoe", "");
        Assert.assertSame(withoutScheme(plain, Arrays.asList(plain), "http://review.example.com",
            Collections.emptyList()), plain);
    }

    @Test
    public void testRejectedCredentialsAreNotHandedOutAgain() {
        GerritGitAuthFailures failures = new GerritGitAuthFailures();
        failures.reject(jdoe.id, URL, "jdoe", 0);
        Assert.assertTrue(failures.isRejected(jdoe.id, URL, "jdoe", 0));
    }

    @Test
    public void testARejectionConcernsOnlyItsAccountAndUrl() {
        GerritGitAuthFailures failures = new GerritGitAuthFailures();
        failures.reject(jdoe.id, URL, "jdoe", 0);
        Assert.assertFalse(failures.isRejected(bot.id, URL, "jdoe", 0));
        Assert.assertFalse(failures.isRejected(jdoe.id, "https://git.example.com", "jdoe", 0));
    }

    /**
     * A rejection can be passing, and entering the password again is how someone says it should work now.
     */
    @Test
    public void testSavingThePasswordOrChangingTheLoginEndsTheRejection() {
        GerritGitAuthFailures failures = new GerritGitAuthFailures();
        failures.reject(jdoe.id, URL, "jdoe", 3);
        Assert.assertFalse(failures.isRejected(jdoe.id, URL, "jdoe", 4));
        Assert.assertFalse(failures.isRejected(jdoe.id, URL, "john", 3));
    }

    private static GerritAccount accountFor(GerritAccount own, List<GerritAccount> accounts, String url, String login) {
        return GerritHttpAuthDataProvider.accountFor(own, accounts, url, login, true, Collections.emptyList());
    }

    private static GerritAccount withoutScheme(GerritAccount own, List<GerritAccount> accounts, String url,
                                               List<String> remoteUrls) {
        return GerritHttpAuthDataProvider.accountFor(own, accounts, url, null, false, remoteUrls);
    }
}
