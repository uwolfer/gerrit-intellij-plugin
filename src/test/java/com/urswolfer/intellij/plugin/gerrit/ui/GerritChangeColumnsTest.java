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

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GerritChangeColumnsTest {

    @Test
    public void testStatusOfOpenChange() {
        Assert.assertEquals("", GerritChangeColumns.getStatus(change(ChangeStatus.NEW, null, true)));
    }

    @Test
    public void testStatusOfWorkInProgress() {
        Assert.assertEquals("WIP", GerritChangeColumns.getStatus(change(ChangeStatus.NEW, true, true)));
    }

    @Test
    public void testStatusOfWorkInProgressWithMergeConflict() {
        Assert.assertEquals("WIP, Merge Conflict", GerritChangeColumns.getStatus(change(ChangeStatus.NEW, true, false)));
    }

    @Test
    public void testStatusOfClosedChangeLeavesOutWorkInProgress() {
        Assert.assertEquals("Abandoned", GerritChangeColumns.getStatus(change(ChangeStatus.ABANDONED, true, null)));
    }

    @Test
    public void testNumbersSortAsNumbers() {
        Assert.assertEquals(Arrays.asList(999, 1000, 10000), sorted(GerritChangeColumns.BY_NUMBER,
            numbered(10000), numbered(999), numbered(1000)).stream().map(change -> change._number).collect(Collectors.toList()));
    }

    @Test
    public void testTextsSortNaturallyIgnoringCaseWithMissingOnesLast() {
        List<String> topics = Arrays.asList("feature-10", null, "Feature-9", "", "alpha");
        List<ChangeInfo> changes = new ArrayList<>();
        for (String topic : topics) {
            ChangeInfo change = new ChangeInfo();
            change.topic = topic;
            changes.add(change);
        }

        changes.sort(GerritChangeColumns.byText(change -> change.topic));

        Assert.assertEquals(Arrays.asList("alpha", "Feature-9", "feature-10"),
            changes.subList(0, 3).stream().map(change -> change.topic).collect(Collectors.toList()));
    }

    @Test
    public void testChangeIdsSortAsHex() {
        ChangeInfo nine = new ChangeInfo();
        nine.changeId = "I9abcdef0123";
        ChangeInfo ten = new ChangeInfo();
        ten.changeId = "I10abcdef012";

        Assert.assertEquals(Arrays.asList(ten, nine), sorted(GerritChangeColumns.BY_HASH, nine, ten));
    }

    @Test
    public void testOwnerWithoutNameSortsByWhatTheColumnShowsOfIt() {
        ChangeInfo bot = owned(null, "ci@example.com");
        ChangeInfo person = owned("Bob", "bob@example.com");
        ChangeInfo other = owned("Dora", "dora@example.com");

        Assert.assertEquals(Arrays.asList(person, bot, other), sorted(GerritChangeColumns.BY_OWNER, other, bot, person));
    }

    @Test
    public void testOwnerWithoutNameIsShownAsGerritShowsIt() {
        AccountInfo account = new AccountInfo(7);
        ChangeInfo change = new ChangeInfo();
        change.owner = account;
        Assert.assertEquals("7", GerritChangeColumns.getOwner(change));

        account.email = "ci@example.com";
        Assert.assertEquals("ci@example.com", GerritChangeColumns.getOwner(change));

        account.username = "ci";
        Assert.assertEquals("ci", GerritChangeColumns.getOwner(change));

        account.name = "";
        Assert.assertEquals("ci", GerritChangeColumns.getOwner(change));

        account.name = "CI Bot";
        Assert.assertEquals("CI Bot", GerritChangeColumns.getOwner(change));
    }

    @Test
    public void testUpdatedSortsByTimeNotByItsText() {
        ChangeInfo yesterday = updated(Timestamp.valueOf("2026-10-24 09:00:00"));
        ChangeInfo today = updated(Timestamp.valueOf("2026-10-25 08:00:00"));
        ChangeInfo lastYear = updated(Timestamp.valueOf("2025-12-31 23:00:00"));

        Assert.assertEquals(Arrays.asList(lastYear, yesterday, today),
            sorted(GerritChangeColumns.BY_UPDATED, today, lastYear, yesterday));
    }

    @Test
    public void testStarredSortFirst() {
        ChangeInfo starred = new ChangeInfo();
        starred.starred = true;
        ChangeInfo plain = new ChangeInfo();

        Assert.assertEquals(Arrays.asList(starred, plain), sorted(GerritChangeColumns.BY_STARRED, plain, starred));
    }

    @Test
    public void testLabelsSortByTheVoteTheirIconShows() {
        ChangeInfo rejected = labelled(vote(true, false, false, false, null));
        ChangeInfo dislikedByTwo = labelled(vote(false, false, true, false, (short) -2));
        ChangeInfo disliked = labelled(vote(false, false, true, false, null));
        ChangeInfo none = labelled(null);
        ChangeInfo recommended = labelled(vote(false, false, false, true, null));
        ChangeInfo recommendedByTwo = labelled(vote(false, false, false, true, (short) 2));
        ChangeInfo approved = labelled(vote(false, true, false, false, null));

        List<ChangeInfo> expected = Arrays.asList(
            rejected, dislikedByTwo, disliked, none, recommended, recommendedByTwo, approved);
        List<ChangeInfo> reversed = new ArrayList<>(expected);
        Collections.reverse(reversed);
        reversed.sort(GerritChangeColumns.byLabel("Priority"));
        Assert.assertEquals(expected, reversed);
    }

    @Test
    public void testLabelWithRejectionAndApprovalSortsAsTheRejectionItsIconShows() {
        Assert.assertEquals(GerritChangeColumns.getVote(vote(true, false, false, false, null)),
            GerritChangeColumns.getVote(vote(true, true, false, false, null)));
    }

    private static List<ChangeInfo> sorted(Comparator<ChangeInfo> comparator, ChangeInfo... changes) {
        List<ChangeInfo> result = new ArrayList<>(Arrays.asList(changes));
        result.sort(comparator);
        return result;
    }

    private static ChangeInfo numbered(int number) {
        ChangeInfo change = new ChangeInfo();
        change._number = number;
        return change;
    }

    private static ChangeInfo owned(String name, String email) {
        ChangeInfo change = new ChangeInfo();
        change.owner = new AccountInfo(name, email);
        return change;
    }

    private static ChangeInfo updated(Timestamp updated) {
        ChangeInfo change = new ChangeInfo();
        change.updated = updated;
        return change;
    }

    private static ChangeInfo labelled(LabelInfo label) {
        ChangeInfo change = new ChangeInfo();
        change.labels = label == null ? null : Collections.singletonMap("Priority", label);
        return change;
    }

    private static LabelInfo vote(boolean rejected, boolean approved, boolean disliked, boolean recommended, Short value) {
        AccountInfo voter = new AccountInfo(1);
        LabelInfo label = new LabelInfo();
        label.rejected = rejected ? voter : null;
        label.approved = approved ? voter : null;
        label.disliked = disliked ? voter : null;
        label.recommended = recommended ? voter : null;
        label.value = value;
        return label;
    }

    private static ChangeInfo change(ChangeStatus status, Boolean workInProgress, Boolean mergeable) {
        ChangeInfo change = new ChangeInfo();
        change.status = status;
        change.workInProgress = workInProgress;
        change.mergeable = mergeable;
        return change;
    }
}
