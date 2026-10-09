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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AccountPopupActionTest {

    @Test
    public void testLabelLeavesOutTheScheme() {
        Assert.assertEquals(AccountPopupAction.label(
            GerritAccount.create("https://review.example.org/", "jdoe", "")), "jdoe@review.example.org");
        Assert.assertEquals(AccountPopupAction.label(
            GerritAccount.create("http://localhost:8080/gerrit", "jdoe", "")), "jdoe@localhost:8080/gerrit");
    }

    @Test
    public void testLabelOfAnAnonymousAccountIsItsHost() {
        Assert.assertEquals(AccountPopupAction.label(
            GerritAccount.create("https://review.example.org", "", "")), "review.example.org");
    }

    @Test
    public void testLabelWithoutAccount() {
        Assert.assertEquals(AccountPopupAction.label(null), "None");
    }

    @Test
    public void testLabelOfAnAccountWithoutHost() {
        Assert.assertEquals(AccountPopupAction.label(GerritAccount.create("", "jdoe", "")), "jdoe");
    }
}
