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

package com.urswolfer.intellij.plugin.gerrit.util;

import com.google.gerrit.extensions.common.ChangeInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

public class CommitChangesTest {
    private static final String HASH = "1111111111111111111111111111111111111111";

    @Test
    public void testIsCommitHash() {
        Assert.assertTrue(CommitChanges.isCommitHash(HASH));
        Assert.assertFalse(CommitChanges.isCommitHash(null));
        Assert.assertFalse(CommitChanges.isCommitHash(""));
        Assert.assertFalse(CommitChanges.isCommitHash("1111111"));
        Assert.assertFalse(CommitChanges.isCommitHash("HEAD"));
        Assert.assertFalse(CommitChanges.isCommitHash(HASH + "1"));
    }

    @Test
    public void testPickChangeOfNone() {
        Assert.assertNull(CommitChanges.pickChange(Collections.emptyList(), "project"));
    }

    @Test
    public void testPickChangePrefersProject() {
        ChangeInfo other = change(1, "other");
        ChangeInfo own = change(2, "project");
        Assert.assertSame(CommitChanges.pickChange(Arrays.asList(other, own), "project"), own);
    }

    @Test
    public void testPickChangeFallsBackToFirst() {
        ChangeInfo first = change(1, "other");
        Assert.assertSame(CommitChanges.pickChange(Arrays.asList(first, change(2, "another")), "project"), first);
    }

    private static ChangeInfo change(int number, String project) {
        ChangeInfo change = new ChangeInfo();
        change._number = number;
        change.project = project;
        return change;
    }
}
