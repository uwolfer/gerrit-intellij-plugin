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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

public class AccountLookupTest {

    @Test
    public void testIdentifierWithNameAndEmail() {
        AccountInfo account = account(7, "Jane Doe", "jane@example.com", "jdoe");
        Assert.assertEquals(AccountLookup.identifier(account), "Jane Doe <jane@example.com>");
    }

    @Test
    public void testIdentifierWithEmailOnly() {
        AccountInfo account = account(7, null, "jane@example.com", null);
        Assert.assertEquals(AccountLookup.identifier(account), "jane@example.com");
    }

    @Test
    public void testIdentifierWithNameOnly() {
        AccountInfo account = account(7, "Jane Doe", null, "jdoe");
        Assert.assertEquals(AccountLookup.identifier(account), "Jane Doe (7)");
    }

    @Test
    public void testIdentifierFallsBackToUsernameAndId() {
        Assert.assertEquals(AccountLookup.identifier(account(7, null, null, "jdoe")), "jdoe");
        Assert.assertEquals(AccountLookup.identifier(account(7, null, null, null)), "7");
    }

    @Test
    public void testInsertedIdentifierLeavesOutNameWithSeparator() {
        Assert.assertEquals(AccountLookup.insertedIdentifier(account(7, "Doe, John", "jdoe@example.com", "jdoe"), ","),
            "7");
        Assert.assertEquals(AccountLookup.insertedIdentifier(account(7, "Doe, John", null, null), ","), "7");
    }

    @Test
    public void testInsertedIdentifierKeepsNameWithoutSeparator() {
        AccountInfo account = account(7, "Jane Doe", "jane@example.com", "jdoe");
        Assert.assertEquals(AccountLookup.insertedIdentifier(account, ","), "Jane Doe <jane@example.com>");
        Assert.assertEquals(AccountLookup.insertedIdentifier(account(7, "Doe, John", "jdoe@example.com", null), ""),
            "Doe, John <jdoe@example.com>");
    }

    @Test
    public void testAlternativeLookupStringsContainEveryNamePart() {
        AccountInfo account = account(7, "Jane\u00A0van Doe", "jane@example.com", "jdoe");
        Assert.assertEquals(AccountLookup.alternativeLookupStrings(account),
            Arrays.asList("Jane", "van", "Doe", "jane@example.com", "jdoe"));
    }

    @Test
    public void testAlternativeLookupStringsWithoutDetails() {
        Assert.assertEquals(AccountLookup.alternativeLookupStrings(account(7, null, null, null)),
            Collections.emptyList());
    }

    @Test
    public void testCurrentAssignee() {
        ChangeInfo changeInfo = new ChangeInfo();
        Assert.assertEquals(SetAssigneeAction.currentAssignee(changeInfo), "");

        changeInfo.assignee = account(7, "Jane Doe", "jane@example.com", "jdoe");
        Assert.assertEquals(SetAssigneeAction.currentAssignee(changeInfo), "Jane Doe <jane@example.com>");
    }

    private static AccountInfo account(int id, String name, String email, String username) {
        AccountInfo account = new AccountInfo(id);
        account.name = name;
        account.email = email;
        account.username = username;
        return account;
    }
}
