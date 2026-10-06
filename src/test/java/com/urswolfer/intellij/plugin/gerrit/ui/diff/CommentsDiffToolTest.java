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

import java.util.function.Predicate;

public class CommentsDiffToolTest {

    @Test
    public void testBaseOfSingleParentCommitShowsParentComments() {
        Predicate<Comment> onBase = CommentsDiffTool.onBase(null);

        Assert.assertTrue(onBase.test(comment(Side.PARENT, null)));
        Assert.assertFalse(onBase.test(comment(Side.REVISION, null)));
        Assert.assertFalse(onBase.test(comment(null, null)));
    }

    @Test
    public void testBaseOfMergeShowsCommentsOnItsParentOnly() {
        Predicate<Comment> onBase = CommentsDiffTool.onBase(1);

        Assert.assertTrue(onBase.test(comment(Side.PARENT, 1)));
        Assert.assertFalse(onBase.test(comment(Side.PARENT, 2)));
        Assert.assertFalse(onBase.test(comment(Side.PARENT, null))); // on the auto-merge
        Assert.assertFalse(onBase.test(comment(null, null)));
    }

    private static Comment comment(Side side, Integer parent) {
        CommentInfo comment = new CommentInfo();
        comment.side = side;
        comment.parent = parent;
        return comment;
    }
}
