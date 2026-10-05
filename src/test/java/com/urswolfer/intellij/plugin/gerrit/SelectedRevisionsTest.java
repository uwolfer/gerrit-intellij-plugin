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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;

public class SelectedRevisionsTest {

    @Test
    public void testNewestRevisionIsTheOneWithTheHighestPatchSetNumber() throws Exception {
        ChangeInfo changeInfo = new ChangeInfo();
        changeInfo.revisions = new LinkedHashMap<String, RevisionInfo>();
        // the revisions map has no defined order; the last entry is not necessarily the newest revision
        changeInfo.revisions.put("aaa", revision(2));
        changeInfo.revisions.put("bbb", revision(3));
        changeInfo.revisions.put("ccc", revision(1));

        Assert.assertEquals(SelectedRevisions.getNewestRevision(changeInfo), "bbb");
    }

    @Test
    public void testNoRevisions() throws Exception {
        ChangeInfo withoutRevisions = new ChangeInfo();
        Assert.assertNull(SelectedRevisions.getNewestRevision(withoutRevisions));

        ChangeInfo withEmptyRevisions = new ChangeInfo();
        withEmptyRevisions.revisions = new LinkedHashMap<String, RevisionInfo>();
        Assert.assertNull(SelectedRevisions.getNewestRevision(withEmptyRevisions));
    }

    @Test
    public void testRetainKeepsTheSelectionsWhichStillApply() throws Exception {
        SelectedRevisions selectedRevisions = new SelectedRevisions();
        selectedRevisions.put("listed", "aaa");
        selectedRevisions.put("revisionGone", "bbb");
        selectedRevisions.put("notListed", "ccc");
        selectedRevisions.put("newPatchSet", "fff");

        selectedRevisions.retain(
            Arrays.asList(change("listed", "aaa", "ddd"), change("revisionGone", "bbb", "eee"),
                change("notListed", "ccc"), change("newPatchSet", "fff", "ggg")),
            Arrays.asList(change("listed", "aaa", "ddd"), change("revisionGone", "eee"),
                change("newPatchSet", "fff", "ggg", "hhh")));

        Assert.assertEquals(selectedRevisions.get("listed"), Optional.of("aaa"));
        Assert.assertEquals(selectedRevisions.get("revisionGone"), Optional.empty());
        Assert.assertEquals(selectedRevisions.get("notListed"), Optional.empty());
        Assert.assertEquals(selectedRevisions.get("newPatchSet"), Optional.empty());
    }

    private static ChangeInfo change(String id, String... revisions) {
        ChangeInfo changeInfo = new ChangeInfo();
        changeInfo.id = id;
        changeInfo.revisions = new LinkedHashMap<String, RevisionInfo>();
        for (int i = 0; i < revisions.length; i++) {
            changeInfo.revisions.put(revisions[i], revision(i + 1));
        }
        return changeInfo;
    }

    private static RevisionInfo revision(int number) {
        RevisionInfo revisionInfo = new RevisionInfo();
        revisionInfo._number = number;
        return revisionInfo;
    }
}
