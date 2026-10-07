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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

public class GerritProjectAccountTest {

    private static final Supplier<Collection<String>> NO_REMOTES = Collections::emptyList;

    private final GerritAccount one = GerritAccount.create("https://one.example.com", "jdoe", "");
    private final GerritAccount two = GerritAccount.create("https://two.example.com", "jdoe", "");

    /**
     * Everyone who has ever used the plugin has exactly one account after the upgrade and has bound no project to
     * it, so that case has to resolve without anybody choosing anything.
     */
    @Test
    public void testTheOnlyAccountIsUsedWithoutBindingAProjectToIt() {
        Assert.assertSame(GerritProjectAccount.resolve("", Collections.singletonList(one), NO_REMOTES), one);
    }

    @Test
    public void testTheBoundAccountWins() {
        Assert.assertSame(GerritProjectAccount.resolve(two.id, Arrays.asList(one, two),
            remotes("https://one.example.com/app")), two);
    }

    /**
     * Guessing one of several would send a project's changes, and its credentials, to whichever instance happened to
     * be first.
     */
    @Test
    public void testNothingIsGuessedWhenSeveralAccountsAreUnbound() {
        Assert.assertNull(GerritProjectAccount.resolve("", Arrays.asList(one, two), NO_REMOTES));
    }

    @Test
    public void testAccountRemovedSinceBindingFallsBackToTheOnlyOneLeft() {
        Assert.assertSame(GerritProjectAccount.resolve("gone", Collections.singletonList(one), NO_REMOTES), one);
    }

    @Test
    public void testAccountRemovedSinceBindingResolvesToNothingWhileSeveralRemain() {
        Assert.assertNull(GerritProjectAccount.resolve("gone", Arrays.asList(one, two), NO_REMOTES));
    }

    @Test
    public void testNoAccountsResolveToNothing() {
        Assert.assertNull(GerritProjectAccount.resolve("", Collections.emptyList(), NO_REMOTES));
    }

    @Test
    public void testTheRemotesPickTheAccountOfTheirInstance() {
        Assert.assertSame(GerritProjectAccount.resolve("", Arrays.asList(one, two),
            remotes("https://github.com/jdoe/app.git", "ssh://jdoe@two.example.com:29418/app")), two);
    }

    @Test
    public void testTheCloneBaseUrlCountsAsTheInstance() {
        GerritAccount mirrored = GerritAccount.create("https://review.example.com", "jdoe", "https://git.example.com");

        Assert.assertSame(GerritProjectAccount.resolve("", Arrays.asList(one, mirrored),
            remotes("https://git.example.com/app")), mirrored);
    }

    /**
     * Two logins on one instance leave the remotes just as undecided as no remote on any of them.
     */
    @Test
    public void testNothingIsGuessedWhenTheRemotesPointAtSeveralAccounts() {
        GerritAccount otherLogin = GerritAccount.create("https://one.example.com", "bot", "");

        Assert.assertNull(GerritProjectAccount.resolve("", Arrays.asList(one, otherLogin, two),
            remotes("https://one.example.com/app")));
    }

    @Test
    public void testAProjectWithoutGerritRemotesResolvesToNothing() {
        Assert.assertNull(GerritProjectAccount.resolve("", Arrays.asList(one, two),
            remotes("https://github.com/jdoe/app.git", "/home/jdoe/repos/app [old]")));
    }

    @Test
    public void testTheRemotesAreNotLookedAtWhileThereIsOneAccount() {
        Assert.assertSame(GerritProjectAccount.resolve("", Collections.singletonList(one), () -> {
            throw new AssertionError("the remotes were looked at");
        }), one);
    }

    private static Supplier<Collection<String>> remotes(String... urls) {
        return () -> Arrays.asList(urls);
    }
}
