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
import java.util.Collections;

public class GerritProjectAccountTest {

    private final GerritAccount one = GerritAccount.create("https://one.example.com", "jdoe", "");
    private final GerritAccount two = GerritAccount.create("https://two.example.com", "jdoe", "");

    /**
     * Everyone who has ever used the plugin has exactly one account after the upgrade and has bound no project to
     * it, so that case has to resolve without anybody choosing anything.
     */
    @Test
    public void testTheOnlyAccountIsUsedWithoutBindingAProjectToIt() {
        Assert.assertSame(GerritProjectAccount.resolve("", Collections.singletonList(one)), one);
    }

    @Test
    public void testTheBoundAccountWins() {
        Assert.assertSame(GerritProjectAccount.resolve(two.id, Arrays.asList(one, two)), two);
    }

    /**
     * Guessing one of several would send a project's changes, and its credentials, to whichever instance happened to
     * be first.
     */
    @Test
    public void testNothingIsGuessedWhenSeveralAccountsAreUnbound() {
        Assert.assertNull(GerritProjectAccount.resolve("", Arrays.asList(one, two)));
    }

    @Test
    public void testAccountRemovedSinceBindingFallsBackToTheOnlyOneLeft() {
        Assert.assertSame(GerritProjectAccount.resolve("gone", Collections.singletonList(one)), one);
    }

    @Test
    public void testAccountRemovedSinceBindingResolvesToNothingWhileSeveralRemain() {
        Assert.assertNull(GerritProjectAccount.resolve("gone", Arrays.asList(one, two)));
    }

    @Test
    public void testNoAccountsResolveToNothing() {
        Assert.assertNull(GerritProjectAccount.resolve("", Collections.emptyList()));
    }
}
