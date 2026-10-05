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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

public class GerritUpdatesNotificationComponentTest {

    @Test
    public void testListItemEscapesHtml() {
        ChangeInfo change = change("tools/<app>", "Use Optional<String> & co", "Jane <Doe>");

        Assert.assertEquals(
            "<li><strong>NEW: </strong>tools/&lt;app&gt;: Use Optional&lt;String&gt; &amp; co (Owner: Jane &lt;Doe&gt;)</li>",
            GerritUpdatesNotificationComponent.listItem(change, true));
    }

    @Test
    public void testListItemNamesOwnerWithoutFullName() {
        ChangeInfo change = change("demo", "Add hello", null);
        change.owner.username = "ci-bot";

        Assert.assertEquals("<li>demo: Add hello (Owner: ci-bot)</li>",
            GerritUpdatesNotificationComponent.listItem(change, false));
    }

    @Test
    public void testListItemNamesOwnerWithBlankName() {
        ChangeInfo change = change("demo", "Add hello", " ");
        change.owner.email = "bot@example.com";

        Assert.assertEquals("<li>demo: Add hello (Owner: bot@example.com)</li>",
            GerritUpdatesNotificationComponent.listItem(change, false));
    }

    private static ChangeInfo change(String project, String subject, String ownerName) {
        ChangeInfo change = new ChangeInfo();
        change.project = project;
        change.subject = subject;
        change.owner = new AccountInfo(ownerName, null);
        change.owner._accountId = 1000000;
        return change;
    }
}
