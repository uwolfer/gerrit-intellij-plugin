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

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CommentSideTest {

    @Test
    public void testRevisionShowsCommentsOnTheRevision() {
        CommentSide revision = CommentSide.onRevision("a.txt", "abc");

        Assert.assertTrue(revision.shows(comment(Side.REVISION, null)));
        Assert.assertTrue(revision.shows(comment(null, null))); // Gerrit leaves the side out
        Assert.assertFalse(revision.shows(comment(Side.PARENT, null)));
    }

    @Test
    public void testBaseOfSingleParentCommitShowsParentComments() {
        CommentSide base = CommentSide.onParent("a.txt", "abc", null);

        Assert.assertTrue(base.shows(comment(Side.PARENT, null)));
        Assert.assertFalse(base.shows(comment(Side.REVISION, null)));
        Assert.assertFalse(base.shows(comment(null, null)));
    }

    @Test
    public void testBaseOfMergeShowsCommentsOnItsParentOnly() {
        CommentSide base = CommentSide.onParent("a.txt", "abc", 1);

        Assert.assertTrue(base.shows(comment(Side.PARENT, 1)));
        Assert.assertFalse(base.shows(comment(Side.PARENT, 2)));
        Assert.assertFalse(base.shows(comment(Side.PARENT, null))); // on the auto-merge
        Assert.assertFalse(base.shows(comment(null, null)));
    }

    @Test
    public void testSameSideInAnotherDiff() {
        Assert.assertTrue(CommentSide.onParent("a.txt", "abc", 1).isSameAs(CommentSide.onParent("a.txt", "abc", 1)));
        Assert.assertFalse(CommentSide.onParent("a.txt", "abc", 1).isSameAs(CommentSide.onParent("a.txt", "abc", 2)));
        Assert.assertFalse(CommentSide.onParent("a.txt", "abc", null).isSameAs(CommentSide.onRevision("a.txt", "abc")));
        Assert.assertFalse(CommentSide.onRevision("a.txt", "abc").isSameAs(CommentSide.onRevision("b.txt", "abc")));
        Assert.assertFalse(CommentSide.onRevision("a.txt", "abc").isSameAs(CommentSide.onRevision("a.txt", "def")));
    }

    private static Comment comment(Side side, Integer parent) {
        CommentInfo comment = new CommentInfo();
        comment.side = side;
        comment.parent = parent;
        return comment;
    }
}
