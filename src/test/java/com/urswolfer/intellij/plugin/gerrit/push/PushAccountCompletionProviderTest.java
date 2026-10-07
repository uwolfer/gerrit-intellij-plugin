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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.google.gerrit.extensions.common.AccountInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PushAccountCompletionProviderTest {

    @Test
    public void testPushIdentifierPrefersUsername() {
        Assert.assertEquals(PushAccountCompletionProvider.pushIdentifier(
            account(7, "Jane Doe", "jane@example.com", "jdoe")), "jdoe");
    }

    @Test
    public void testPushIdentifierFallsBackToEmail() {
        Assert.assertEquals(PushAccountCompletionProvider.pushIdentifier(
            account(7, "Jane Doe", "jane@example.com", null)), "jane@example.com");
    }

    @Test
    public void testPushIdentifierSkipsNumericUsername() {
        Assert.assertEquals(PushAccountCompletionProvider.pushIdentifier(
            account(7, "Jane Doe", "jane@example.com", "1234")), "jane@example.com");
    }

    @Test
    public void testPushIdentifierSkipsWhatThePushReferenceCannotCarry() {
        Assert.assertEquals(PushAccountCompletionProvider.pushIdentifier(
            account(7, "Jane Doe", "doe,jane@example.com", "jane doe")), "7");
        Assert.assertEquals(PushAccountCompletionProvider.pushIdentifier(
            account(7, "Jane Doe", "jane~1@example.com", "jane:doe")), "7");
    }

    @Test
    public void testPushIdentifierFallsBackToAccountId() {
        Assert.assertEquals(PushAccountCompletionProvider.pushIdentifier(account(7, "Jane Doe", null, null)), "7");
        Assert.assertEquals(PushAccountCompletionProvider.pushIdentifier(account(7, null, null, "")), "7");
    }

    private static AccountInfo account(int id, String name, String email, String username) {
        AccountInfo account = new AccountInfo(id);
        account.name = name;
        account.email = email;
        account.username = username;
        return account;
    }
}
