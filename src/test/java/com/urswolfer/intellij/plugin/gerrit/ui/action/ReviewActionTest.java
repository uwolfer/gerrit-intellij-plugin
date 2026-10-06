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

import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

public class ReviewActionTest {

    @Test
    public void testPublishedDraftKeepsItsState() {
        CommentInfo draft = draft();
        draft.unresolved = false;
        Assert.assertEquals(Boolean.FALSE, ReviewAction.createCommentInput(draft).unresolved);

        draft.unresolved = true;
        Assert.assertEquals(Boolean.TRUE, ReviewAction.createCommentInput(draft).unresolved);
    }

    @Test
    public void testPublishedDraftKeepsItsPlace() {
        ReviewInput.CommentInput input = ReviewAction.createCommentInput(draft());

        Assert.assertEquals("abc", input.id);
        Assert.assertEquals("root", input.inReplyTo);
        Assert.assertEquals("Will do", input.message);
        Assert.assertEquals("src/Main.java", input.path);
        Assert.assertEquals(Integer.valueOf(12), input.line);
        Assert.assertEquals(Side.REVISION, input.side);
    }

    private static CommentInfo draft() {
        CommentInfo draft = new CommentInfo();
        draft.id = "abc";
        draft.inReplyTo = "root";
        draft.message = "Will do";
        draft.path = "src/Main.java";
        draft.line = 12;
        draft.side = Side.REVISION;
        return draft;
    }
}
