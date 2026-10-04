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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ReviewBranchNameTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String OTHER_COMMIT = "fedcba9876543210fedcba9876543210fedcba98";

    @Test
    public void testEveryPatchSetIsAPathSegment() {
        Assert.assertEquals(ReviewBranchName.build(change("John Doe", null, 123), 1), "review/john_doe/123/1");
        Assert.assertEquals(ReviewBranchName.build(change("John Doe", null, 123), 2), "review/john_doe/123/2");
    }

    @Test
    public void testChangesSharingATopicGetDistinctNames() {
        Assert.assertEquals(ReviewBranchName.build(change("jdoe", "PROJ-42", 123), 3), "review/jdoe/PROJ-42-123/3");
        Assert.assertEquals(ReviewBranchName.build(change("jdoe", "PROJ-42", 124), 3), "review/jdoe/PROJ-42-124/3");
        Assert.assertEquals(ReviewBranchName.build(change("jdoe", "  ", 123), 3), "review/jdoe/123/3");
    }

    @Test
    public void testSegmentsAreValidRefComponents() {
        Assert.assertEquals(ReviewBranchName.build(change("jdoe", "feature/a b:c~d^e?f*g[h\\i", 7), 1),
                "review/jdoe/feature_a_b_c_d_e_f_g_h_i-7/1");
        Assert.assertEquals(ReviewBranchName.build(change("jdoe", ".hidden..x@{y}", 7), 1),
                "review/jdoe/_hidden_x@_y}-7/1");
        Assert.assertEquals(ReviewBranchName.build(change("j.doe.lock", null, 7), 1), "review/j.doe_lock/7/1");
        Assert.assertEquals(ReviewBranchName.build(change("j.", null, 7), 1), "review/j_/7/1");
    }

    @Test
    public void testOwnerWithoutFullName() {
        ChangeInfo change = change(null, null, 7);
        change.owner.username = "JDoe";
        Assert.assertEquals(ReviewBranchName.build(change, 1), "review/jdoe/7/1");

        change.owner.username = null;
        Assert.assertEquals(ReviewBranchName.build(change, 1), "review/1000/7/1");

        change.owner = null;
        Assert.assertEquals(ReviewBranchName.build(change, 1), "review/unknown/7/1");
    }

    @Test
    public void testNamesResolveBackToTheChange() {
        Assert.assertEquals(FeatureMergeBranchResolver.reviewChangeNumber(
                ReviewBranchName.build(change("jdoe", null, 123), 2)).intValue(), 123);
        Assert.assertEquals(FeatureMergeBranchResolver.reviewChangeNumber(
                ReviewBranchName.build(change("jdoe", "abc-12", 345), 2) + "_1").intValue(), 345);
    }

    @Test
    public void testNewBranchIsCreated() {
        ReviewBranchName.Target target = ReviewBranchName.resolve("review/jdoe/1/2", COMMIT,
                heads(), name -> true);

        Assert.assertEquals(target.name, "review/jdoe/1/2");
        Assert.assertFalse(target.exists);
    }

    @Test
    public void testBranchAtTheSameCommitIsReused() {
        ReviewBranchName.Target target = ReviewBranchName.resolve("review/jdoe/1/2", COMMIT,
                heads("review/jdoe/1/2", COMMIT), name -> false);

        Assert.assertEquals(target.name, "review/jdoe/1/2");
        Assert.assertTrue(target.exists);
    }

    @Test
    public void testBranchAtAnotherCommitIsKept() {
        ReviewBranchName.Target target = ReviewBranchName.resolve("review/jdoe/1/2", COMMIT,
                heads("review/jdoe/1/2", OTHER_COMMIT), name -> !name.equals("review/jdoe/1/2"));

        Assert.assertEquals(target.name, "review/jdoe/1/2_1");
        Assert.assertFalse(target.exists);
    }

    @Test
    public void testSuffixedBranchAtTheSameCommitIsReused() {
        ReviewBranchName.Target target = ReviewBranchName.resolve("review/jdoe/1/2", COMMIT,
                heads("review/jdoe/1/2", OTHER_COMMIT, "review/jdoe/1/2_1", COMMIT), name -> true);

        Assert.assertEquals(target.name, "review/jdoe/1/2_1");
        Assert.assertTrue(target.exists);
    }

    @Test
    public void testNoUsableName() {
        Assert.assertNull(ReviewBranchName.resolve("review/jdoe/1/2", COMMIT, heads(), name -> false));
    }

    @Test
    public void testLegacyBranchBlocksPatchSetDirectory() {
        Set<String> branches = new HashSet<>(Arrays.asList("main", "review/jdoe/1"));

        Assert.assertEquals(ReviewBranchName.blockingBranch("review/jdoe/1/2", branches::contains), "review/jdoe/1");
        Assert.assertNull(ReviewBranchName.blockingBranch("review/jdoe/3/2", branches::contains));
    }

    private static ChangeInfo change(String ownerName, String topic, int number) {
        ChangeInfo change = new ChangeInfo();
        change.owner = new AccountInfo(1000);
        change.owner.name = ownerName;
        change.topic = topic;
        change._number = number;
        return change;
    }

    private static Function<String, String> heads(String... branchAndHash) {
        Map<String, String> heads = new HashMap<>();
        for (int i = 0; i < branchAndHash.length; i += 2) {
            heads.put(branchAndHash[i], branchAndHash[i + 1]);
        }
        return heads::get;
    }
}
