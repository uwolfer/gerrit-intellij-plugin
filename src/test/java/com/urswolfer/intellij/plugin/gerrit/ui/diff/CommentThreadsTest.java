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
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.swing.plaf.basic.BasicHTML;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommentThreadsTest {

    @Test
    public void testRepliesJoinTheThreadOfTheCommentTheyReplyToInTheOrderTheyWereWritten() {
        CommentInfo root = published("a", null, 1, true);
        CommentInfo reply = published("b", "a", 2, true);
        CommentInfo replyToReply = published("c", "b", 3, false);
        CommentInfo other = published("d", null, 4, true);

        List<CommentThreads.Thread<Comment>> threads = group(replyToReply, other, reply, root);

        Assert.assertEquals(ids(threads), Arrays.asList("a b c", "d"));
        Assert.assertSame(threads.get(0).root, root);
    }

    @Test
    public void testADraftGoesLastAsTheReplyBeingWritten() {
        CommentInfo root = published("a", null, 1, true);
        CommentInfo draft = draft("b", "a", 2);
        CommentInfo later = published("c", "a", 3, true);

        List<CommentThreads.Thread<Comment>> threads = group(draft, root, later);

        Assert.assertEquals(ids(threads), Arrays.asList("a c b"));
        Assert.assertTrue(threads.get(0).hasDraft());
        Assert.assertSame(threads.get(0).last(), draft);
    }

    @Test
    public void testADraftSavedAgainKeepsItsPlaceAndCommentsPublishedAtOnceGoByIdAsInGerrit() {
        CommentInfo root = published("a", null, 1, true);
        CommentInfo earlierDraft = draft("c", "a", 5);
        CommentInfo laterDraft = draft("d", "a", 3); // the earlier one edited since
        CommentInfo second = published("x", "a", 2, true);
        CommentInfo first = published("b", "a", 2, false);

        Assert.assertEquals(ids(group(laterDraft, earlierDraft, second, root, first)), Arrays.asList("a b x c d"));
    }

    @Test
    public void testAReplyWrittenInTheSameSecondComesAfterItsComment() {
        CommentInfo root = published("z", null, 1, true);
        CommentInfo reply = published("a", "z", 1, true);
        CommentInfo other = published("b", "z", 1, false);

        Assert.assertEquals(ids(group(reply, other, root)), Arrays.asList("z a b"));
    }

    @Test
    public void testAReplyToACommentNotThereStartsAThreadOfItsOwn() {
        CommentInfo orphan = published("b", "elsewhere", 2, true);

        List<CommentThreads.Thread<Comment>> threads = group(orphan);

        Assert.assertEquals(ids(threads), Arrays.asList("b"));
        Assert.assertSame(threads.get(0).root, orphan);
    }

    @Test
    public void testACycleEndsAtTheCommentItStartedFrom() {
        List<CommentThreads.Thread<Comment>> threads =
            group(published("a", "b", 1, true), published("b", "a", 2, true));

        Assert.assertEquals(threads.stream().mapToInt(thread -> thread.items.size()).sum(), 2);
    }

    @Test
    public void testTheLastCommentSaysWhetherTheThreadIsResolved() {
        CommentInfo root = published("a", null, 1, true);
        CommentInfo done = published("b", "a", 2, false);

        Assert.assertTrue(group(root).get(0).isUnresolved());
        Assert.assertFalse(group(root, done).get(0).isUnresolved());
        Assert.assertTrue(group(root, done, draft("c", "b", 3)).get(0).isUnresolved(), "a draft reopens it");
    }

    @Test
    public void testACommentWhichDoesNotSayIsResolvedAsInGerrit() {
        Assert.assertFalse(group(published("a", null, 1, null)).get(0).isUnresolved());
    }

    @Test
    public void testOnlyThePublishedCommentsOfAResolvedThreadAreFoldedAtFirst() {
        CommentInfo root = published("a", null, 1, true);
        CommentInfo done = published("b", "a", 2, false);
        CommentThreads.Thread<Comment> resolved = group(root, done).get(0);
        CommentThreads.Thread<Comment> unresolved = group(root).get(0);

        Assert.assertTrue(resolved.isFoldedAtFirst(root));
        Assert.assertTrue(resolved.isFoldedAtFirst(done));
        Assert.assertFalse(unresolved.isFoldedAtFirst(root));
        CommentInfo draft = draft("c", "b", 3);
        draft.unresolved = false;
        Assert.assertFalse(group(root, done, draft).get(0).isFoldedAtFirst(draft));
    }

    @Test
    public void testAnExcerptIsOneLine() {
        Assert.assertEquals(CommentThreads.excerpt(" first\n\n  second\tthird "), "first second third");
        Assert.assertEquals(CommentThreads.excerpt(null), "");
    }

    /** A fold of lines 2 to 4 of "a\nb\nc\nd\ne", from the start of line 2 to the end of line 4. */
    @Test
    public void testAFoldHidesTheThreadsOfItsLinesFromTheFirstToTheLast() {
        int start = 2, end = 7;
        Assert.assertFalse(DiffComments.hides(start, end, 1), "the line before");
        Assert.assertTrue(DiffComments.hides(start, end, 3), "the first line");
        Assert.assertTrue(DiffComments.hides(start, end, 5), "a line in between");
        Assert.assertTrue(DiffComments.hides(start, end, 7), "the last line, whose thread shows under the placeholder");
        Assert.assertTrue(DiffComments.hides(start, start, start), "an empty first line");
        Assert.assertFalse(DiffComments.hides(start, end, 9), "the line after");
    }

    @Test
    public void testCodeWrapsInLinesWhichKeepTheirIndent() {
        Assert.assertEquals(CommentThreadPanel.wrapping(
                "<p>Look:</p><pre>if (a  &amp;&amp; b) {<br />  call();<br /></pre><p>end</p>"),
            "<p>Look:</p><div><tt>if (a &nbsp;&amp;&amp; b) {</tt></div><div><tt>&nbsp;&nbsp;call();</tt></div><p>end</p>");
        Assert.assertEquals(CommentThreadPanel.wrapping("<pre>\tif (err) {<br /><br />\t\treturn</pre>"),
            "<div><tt>&nbsp;&nbsp;&nbsp;&nbsp;if (err) {</tt></div><div><tt>&nbsp;</tt></div>"
                + "<div><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;return</tt></div>");
    }

    @Test
    public void testCodeCopiedHasPlainSpaces() {
        Assert.assertEquals(CommentThreadPanel.plain("\u00A0\u00A0call(a,\u00A0 b);"), "  call(a,  b);");
    }

    @Test
    public void testTextFromGerritIsNoMarkupInALabel() {
        Assert.assertNull(CommentThreadPanel.plainLabel("<html><img src='http://example.com/x'>")
            .getClientProperty(BasicHTML.propertyKey));
    }

    private static List<CommentThreads.Thread<Comment>> group(Comment... comments) {
        List<CommentThreads.Thread<Comment>> threads =
            CommentThreads.group(new ArrayList<>(Arrays.asList(comments)), Function.identity());
        threads.sort(java.util.Comparator.comparing(thread -> thread.root, CommentThreads.order()));
        return threads;
    }

    private static List<String> ids(List<CommentThreads.Thread<Comment>> threads) {
        return threads.stream()
            .map(thread -> thread.comments().stream().map(comment -> comment.id).collect(Collectors.joining(" ")))
            .collect(Collectors.toList());
    }

    private static CommentInfo published(String id, String inReplyTo, long time, Boolean unresolved) {
        CommentInfo comment = draft(id, inReplyTo, time);
        comment.author = new AccountInfo(1);
        comment.unresolved = unresolved;
        return comment;
    }

    private static CommentInfo draft(String id, String inReplyTo, long time) {
        CommentInfo comment = new CommentInfo();
        comment.id = id;
        comment.inReplyTo = inReplyTo;
        comment.updated = new Timestamp(time * 1000);
        comment.unresolved = true;
        return comment;
    }
}
