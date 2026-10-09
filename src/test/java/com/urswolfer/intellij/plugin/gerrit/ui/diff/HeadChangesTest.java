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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.openapi.util.Pair;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class HeadChangesTest {
    private static final String ID_A = "I" + "a".repeat(40);
    private static final String ID_B = "I" + "b".repeat(40);
    private static final Predicate<ChangeInfo> ANY = change -> true;

    @Test
    public void testChangeIdIsTheLastFooter() {
        String message = "Subject\n\nChange-Id: " + ID_A + " in the text\n\n"
            + "Change-Id: " + ID_A + "\nChange-Id: " + ID_B + "\n";

        Assert.assertEquals(HeadChanges.changeIdOf(message), ID_B);
    }

    @Test
    public void testChangeIdOutsideTheFooterIsNone() {
        Assert.assertNull(
            HeadChanges.changeIdOf("Subject\n\nSquashed:\nChange-Id: " + ID_A + "\n\nSigned-off-by: me\n"));
        Assert.assertNull(HeadChanges.changeIdOf("Change-Id: " + ID_A + "\n"));
    }

    @Test
    public void testChangeIdRightAfterTheSubject() {
        Assert.assertEquals(HeadChanges.changeIdOf("Subject\nChange-Id: " + ID_A + "\n"), ID_A);
    }

    @Test
    public void testCommitOfTwoProjectsMatchesTheChangeOfThisRepository() {
        ChangeInfo other = change(1, ID_A, "master", "a1");
        ChangeInfo mine = change(2, ID_A, "master", "a1");

        List<Pair<ChangeInfo, String>> matches = HeadChanges.match(Collections.singletonList(commit("a1", ID_A)),
            Arrays.asList(other, mine), "master", change -> change == mine);

        Assert.assertEquals(matches.size(), 1);
        Assert.assertSame(matches.get(0).first, mine);
    }

    @Test
    public void testNoChangeId() {
        Assert.assertNull(HeadChanges.changeIdOf("Subject\n\nChange-Id: I123\n"));
    }

    @Test
    public void testCommitWhichIsAPatchSetMatchesThatPatchSet() {
        ChangeInfo change = change(1, ID_A, "master", "old", "current");

        List<Pair<ChangeInfo, String>> matches =
            HeadChanges.match(Collections.singletonList(commit("old", ID_A)),
                Collections.singletonList(change), "master", ANY);

        Assert.assertEquals(matches.size(), 1);
        Assert.assertSame(matches.get(0).first, change);
        Assert.assertEquals(matches.get(0).second, "old");
    }

    @Test
    public void testAmendedCommitMatchesTheCurrentPatchSet() {
        ChangeInfo change = change(1, ID_A, "master", "old", "current");

        List<Pair<ChangeInfo, String>> matches =
            HeadChanges.match(Collections.singletonList(commit("local", ID_A)),
                Collections.singletonList(change), "master", ANY);

        Assert.assertEquals(matches.size(), 1);
        Assert.assertEquals(matches.get(0).second, "current");
    }

    @Test
    public void testChangeIdMatchesOnTheBranchOfTheUpstreamOnly() {
        ChangeInfo cherryPick = change(1, ID_A, "stable", "other");
        ChangeInfo change = change(2, ID_A, "master", "current");

        List<Pair<ChangeInfo, String>> matches =
            HeadChanges.match(Collections.singletonList(commit("local", ID_A)),
                Arrays.asList(cherryPick, change), "master", ANY);

        Assert.assertEquals(matches.size(), 1);
        Assert.assertSame(matches.get(0).first, change);
    }

    @Test
    public void testChangeIdMatchesInTheRepositoryOnly() {
        ChangeInfo change = change(1, ID_A, "master", "current");

        Assert.assertTrue(HeadChanges.match(Collections.singletonList(commit("local", ID_A)),
            Collections.singletonList(change), "master", other -> false).isEmpty());
    }

    @Test
    public void testCommitMatchesWhateverItsRepository() {
        ChangeInfo change = change(1, ID_A, "master", "a1");

        Assert.assertEquals(HeadChanges.match(Collections.singletonList(commit("a1", ID_A)),
            Collections.singletonList(change), "master", other -> false).size(), 1);
    }

    @Test
    public void testChangeIdOnSeveralBranchesMatchesNothingWithoutTheBranch() {
        ChangeInfo cherryPick = change(1, ID_A, "stable", "other");
        ChangeInfo change = change(2, ID_A, "master", "current");

        Assert.assertTrue(HeadChanges.match(Collections.singletonList(commit("local", ID_A)),
            Arrays.asList(cherryPick, change), null, ANY).isEmpty());
        Assert.assertEquals(HeadChanges.match(Collections.singletonList(commit("local", ID_A)),
            Collections.singletonList(change), null, ANY).size(), 1);
    }

    @Test
    public void testChangeIdOnALineOfItsOwnOnly() {
        Assert.assertNull(HeadChanges.changeIdOf("Subject\n\nChange-Id:\n" + ID_A + "\n"));
    }

    @Test
    public void testStackIsMatchedFromHeadDown() {
        ChangeInfo lower = change(1, ID_A, "master", "a1");
        ChangeInfo upper = change(2, ID_B, "master", "b1");

        List<Pair<ChangeInfo, String>> matches = HeadChanges.match(
            Arrays.asList(commit("b1", ID_B), commit("a1", ID_A)), Arrays.asList(lower, upper), "master", ANY);

        Assert.assertEquals(matches.size(), 2);
        Assert.assertSame(matches.get(0).first, upper);
        Assert.assertSame(matches.get(1).first, lower);
    }

    @Test
    public void testChangeIsTakenOnceByTheCommitNearestToHead() {
        ChangeInfo change = change(1, ID_A, "master", "a1", "a2");

        List<Pair<ChangeInfo, String>> matches = HeadChanges.match(
            Arrays.asList(commit("a2", ID_A), commit("a1", ID_A)), Collections.singletonList(change), "master", ANY);

        Assert.assertEquals(matches.size(), 1);
        Assert.assertEquals(matches.get(0).second, "a2");
    }

    @Test
    public void testCommitWithoutChangeIdOrPatchSetMatchesNothing() {
        ChangeInfo change = change(1, ID_A, "master", "a1");

        Assert.assertTrue(HeadChanges.match(Collections.singletonList(commit("local", null)),
            Collections.singletonList(change), "master", ANY).isEmpty());
    }

    @Test
    public void testFilesLeftInPlaceByThePatchSet() {
        Map<String, FileInfo> files = new HashMap<>();
        files.put("/COMMIT_MSG", new FileInfo());
        files.put("kept.txt", new FileInfo());
        FileInfo deleted = new FileInfo();
        deleted.status = 'D';
        files.put("deleted.txt", deleted);
        FileInfo renamed = new FileInfo();
        renamed.status = 'R';
        files.put("renamed.txt", renamed);

        Assert.assertEquals(HeadChanges.filesOf(files), new HashSet<>(Arrays.asList("kept.txt", "renamed.txt")));
    }

    private static Pair<String, String> commit(String hash, String changeId) {
        return Pair.create(hash, "Subject\n" + (changeId != null ? "\nChange-Id: " + changeId + "\n" : ""));
    }

    /** The last revision is the current one. */
    private static ChangeInfo change(int number, String changeId, String branch, String... revisions) {
        ChangeInfo change = new ChangeInfo();
        change._number = number;
        change.changeId = changeId;
        change.branch = branch;
        change.revisions = new HashMap<>();
        for (int i = 0; i < revisions.length; i++) {
            RevisionInfo revision = new RevisionInfo();
            revision._number = i + 1;
            change.revisions.put(revisions[i], revision);
        }
        change.currentRevision = revisions[revisions.length - 1];
        return change;
    }
}
