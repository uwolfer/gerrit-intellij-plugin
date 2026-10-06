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
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GerritChangeDetailsPanelTest {

    private long time;

    @Test
    public void testVoterOnAnyLabelHasAVote() {
        Map<String, LabelInfo> labels = new LinkedHashMap<>();
        labels.put("Code-Review", label(approval(1, "Rita", 1), approval(2, "bot", 0), approval(3, "Carl", 0)));
        labels.put("Verified", label(approval(1, "Rita", 0), approval(2, "bot", 1), approval(3, "Carl", null)));

        Assert.assertEquals(List.of("Carl"), names(GerritChangeDetailsPanel.getReviewersWithoutVote(
            List.of(account(1, "Rita"), account(2, "bot"), account(3, "Carl")), labels)));
    }

    @Test
    public void testReviewerMissingFromLabels() {
        Map<String, LabelInfo> labels = new LinkedHashMap<>();
        labels.put("Code-Review", label(approval(1, "Rita", 1)));
        labels.put("Verified", new LabelInfo());

        Assert.assertEquals(List.of("Carl"), names(GerritChangeDetailsPanel.getReviewersWithoutVote(
            List.of(account(1, "Rita"), account(3, "Carl")), labels)));
    }

    @Test
    public void testReviewersFromLabelsWithoutReviewerList() {
        Map<String, LabelInfo> labels = new LinkedHashMap<>();
        labels.put("Code-Review", label(approval(1, "Rita", 1), approval(2, "bot", 0), approval(3, "Carl", 0)));
        labels.put("Verified", label(approval(1, "Rita", 0), approval(2, "bot", 1), approval(3, "Carl", 0)));

        Assert.assertEquals(List.of("Carl"), names(GerritChangeDetailsPanel.getReviewersWithoutVote(null, labels)));
    }

    @Test
    public void testAccountNameIsEscaped() {
        AccountInfo account = new AccountInfo("R&D <bot>", null);

        Assert.assertEquals("R&amp;D &lt;bot&gt;", GerritChangeDetailsPanel.accountName(account));
    }

    @Test
    public void testAccountNameWithoutFullName() {
        AccountInfo account = new AccountInfo(" ", null);
        account._accountId = 1000005;
        account.username = "ci-bot";

        Assert.assertEquals("ci-bot", GerritChangeDetailsPanel.accountName(account));
    }

    private static LabelInfo label(ApprovalInfo... approvals) {
        LabelInfo label = new LabelInfo();
        label.all = List.of(approvals);
        return label;
    }

    private static AccountInfo account(int accountId, String name) {
        AccountInfo account = new AccountInfo(accountId);
        account.name = name;
        return account;
    }

    private ApprovalInfo approval(int accountId, String name, Integer value) {
        ApprovalInfo approval = new ApprovalInfo(accountId);
        approval.name = name;
        approval.value = value;
        // differs per label, as it does in Gerrit's answer
        approval.date = new Timestamp(++time);
        return approval;
    }

    private static List<String> names(List<AccountInfo> accounts) {
        return accounts.stream().map(account -> account.name).collect(Collectors.toList());
    }
}
