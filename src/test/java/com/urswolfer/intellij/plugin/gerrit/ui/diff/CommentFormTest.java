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

import com.google.gerrit.extensions.common.CommentInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

public class CommentFormTest {

    @Test
    public void testNewCommentStartsUnresolved() {
        Assert.assertFalse(CommentForm.isInitiallyResolved(null, null));
    }

    @Test
    public void testReplyKeepsResolvedThreadResolved() {
        Assert.assertTrue(CommentForm.isInitiallyResolved(null, comment(false)));
    }

    @Test
    public void testReplyKeepsUnresolvedThreadUnresolved() {
        Assert.assertFalse(CommentForm.isInitiallyResolved(null, comment(true)));
    }

    @Test
    public void testEditedDraftKeepsItsOwnState() {
        Assert.assertTrue(CommentForm.isInitiallyResolved(comment(false), comment(true)));
        Assert.assertFalse(CommentForm.isInitiallyResolved(comment(true), comment(false)));
    }

    private static CommentInfo comment(boolean unresolved) {
        CommentInfo comment = new CommentInfo();
        comment.unresolved = unresolved;
        return comment;
    }
}
