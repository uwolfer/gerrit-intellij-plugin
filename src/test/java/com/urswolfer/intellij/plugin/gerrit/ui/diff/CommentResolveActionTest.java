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

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class CommentResolveActionTest {

    @Test
    public void testDoneReplyResolvesTheThread() {
        DraftInput reply = CommentResolveAction.createReply(unresolved(), CommentResolveAction.Reply.DONE);

        assertResolvingReply(reply);
        Assert.assertEquals("Done", reply.message);
    }

    @Test
    public void testAckReplyResolvesTheThreadAsGerritWritesIt() {
        DraftInput reply = CommentResolveAction.createReply(unresolved(), CommentResolveAction.Reply.ACK);

        assertResolvingReply(reply);
        Assert.assertEquals("Acknowledged", reply.message);
    }

    @Test
    public void testOfferedWhileTheThreadIsUnresolvedAndHasNoDraft() {
        CommentInfo root = unresolved();
        CommentInfo done = published("def", "abc", false);
        CommentInfo draft = new CommentInfo();
        draft.id = "ghi";
        draft.inReplyTo = "abc";
        draft.unresolved = true;

        Assert.assertTrue(CommentResolveAction.isOffered(thread(root)));
        Assert.assertFalse(CommentResolveAction.isOffered(thread(root, done)));
        Assert.assertFalse(CommentResolveAction.isOffered(thread(root, draft)));
    }

    private static CommentThreads.Thread<Comment> thread(Comment... comments) {
        List<CommentThreads.Thread<Comment>> threads =
            CommentThreads.group(new ArrayList<>(Arrays.asList(comments)), Function.identity());
        Assert.assertEquals(1, threads.size());
        return threads.get(0);
    }

    private static CommentInfo published(String id, String inReplyTo, boolean unresolved) {
        CommentInfo comment = new CommentInfo();
        comment.id = id;
        comment.inReplyTo = inReplyTo;
        comment.author = new AccountInfo(1);
        comment.unresolved = unresolved;
        return comment;
    }

    private static CommentInfo unresolved() {
        CommentInfo unresolved = new CommentInfo();
        unresolved.id = "abc";
        unresolved.path = "src/Main.java";
        unresolved.line = 12;
        unresolved.side = Side.PARENT;
        unresolved.parent = 1;
        unresolved.range = new Comment.Range();
        unresolved.range.startLine = 11;
        unresolved.range.endLine = 12;
        unresolved.unresolved = true;
        unresolved.author = new AccountInfo(2);
        return unresolved;
    }

    private static void assertResolvingReply(DraftInput reply) {
        Assert.assertEquals(Boolean.FALSE, reply.unresolved);
        Assert.assertEquals("abc", reply.inReplyTo);
        Assert.assertEquals("src/Main.java", reply.path);
        Assert.assertEquals(Integer.valueOf(12), reply.line);
        Assert.assertEquals(Side.PARENT, reply.side);
        Assert.assertEquals(Integer.valueOf(1), reply.parent);
        Assert.assertEquals(11, reply.range.startLine);
        Assert.assertEquals(12, reply.range.endLine);
    }
}
