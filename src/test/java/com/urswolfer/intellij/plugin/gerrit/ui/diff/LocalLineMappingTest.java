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
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

public class LocalLineMappingTest {

    @Test
    public void testUnchangedTextMapsEveryLineToItself() {
        LocalLineMapping mapping = mapping(5);

        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 3), 3);
        Assert.assertEquals(mapping.toSideLine(Side.RIGHT, 3), 3);
    }

    @Test
    public void testLinesInsertedAboveMoveTheComment() {
        // two lines inserted before line 1 of the patch set
        LocalLineMapping mapping = mapping(5, new Range(1, 1, 1, 3));

        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 0), 0);
        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 1), 3);
        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 4), 6);
        Assert.assertEquals(mapping.editorLineOf(Side.RIGHT, 2), 3);
    }

    @Test
    public void testLinesDeletedAboveMoveTheComment() {
        LocalLineMapping mapping = mapping(5, new Range(0, 2, 0, 0));

        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 3), 1);
    }

    @Test
    public void testCommentOnARewrittenLineStaysInTheBlock() {
        // lines 1 to 3 of the patch set became one line
        LocalLineMapping mapping = mapping(6, new Range(1, 4, 1, 2));

        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 1), 1);
        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 3), 1);
        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 4), 2);
    }

    @Test
    public void testCommentOnADeletedLineGoesToTheLineWhichFollows() {
        LocalLineMapping mapping = mapping(6, new Range(2, 4, 2, 2));

        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 3), 2);
    }

    @Test
    public void testNoLineOfThePatchSetWhereTheUserChangedIt() {
        LocalLineMapping mapping = mapping(6, new Range(1, 2, 1, 3));

        Assert.assertEquals(mapping.toEditorLineStrict(Side.RIGHT, 1), -1);
        Assert.assertEquals(mapping.toSideLine(Side.RIGHT, 1), -1);
        Assert.assertEquals(mapping.toSideLine(Side.RIGHT, 2), -1);
        Assert.assertEquals(mapping.toSideLine(Side.RIGHT, 3), 2);
    }

    @Test
    public void testSeveralBlocks() {
        LocalLineMapping mapping = mapping(10, new Range(1, 2, 1, 4), new Range(5, 7, 7, 7));

        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 3), 5);
        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 8), 8);
        Assert.assertEquals(mapping.toSideLine(Side.RIGHT, 8), 8);
        Assert.assertEquals(mapping.toSideLine(Side.RIGHT, 6), 4);
    }

    @Test
    public void testNoLineBeforeTheTextIsCompared() {
        LocalLineMapping mapping = new LocalLineMapping();

        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 0), -1);
        Assert.assertEquals(mapping.toSideLine(Side.RIGHT, 0), -1);
        Assert.assertNull(CommentPosition.onLine(mapping, 0));
    }

    @Test
    public void testNoNewCommentUntilAnEditIsCompared() {
        LocalLineMapping mapping = mapping(5);

        mapping.markStale();

        Assert.assertEquals(mapping.toEditorLine(Side.RIGHT, 2), 2, "what is shown stays");
        Assert.assertNull(CommentPosition.onLine(mapping, 2));
        mapping.set(Collections.emptyList(), 5);
        Assert.assertNotNull(CommentPosition.onLine(mapping, 2));
    }

    @Test
    public void testNoLeftSide() {
        LocalLineMapping mapping = mapping(5);

        Assert.assertEquals(mapping.editorLineOf(Side.LEFT, 2), -1);
        Assert.assertEquals(mapping.toSideLine(Side.LEFT, 2), -1);
        Assert.assertEquals(mapping.preferredSide(), Side.RIGHT);
    }

    @Test
    public void testNewCommentOnAnUnchangedLineGoesToItsLineInThePatchSet() {
        LocalLineMapping mapping = mapping(5, new Range(0, 0, 0, 2));

        CommentPosition position = CommentPosition.onLine(mapping, 3);

        Assert.assertNotNull(position);
        Assert.assertEquals(position.side, Side.RIGHT);
        Assert.assertEquals(position.line, 2);
    }

    @Test
    public void testNoNewCommentOnALineTheUserAdded() {
        LocalLineMapping mapping = mapping(5, new Range(0, 0, 0, 2));

        Assert.assertNull(CommentPosition.onLine(mapping, 1));
    }

    @Test
    public void testRangeMovesWithItsLines() {
        LocalLineMapping mapping = mapping(5, new Range(0, 0, 0, 1));
        Comment.Range range = new Comment.Range();
        range.startLine = 2;
        range.startCharacter = 1;
        range.endLine = 3;
        range.endCharacter = 4;

        Comment.Range editorRange = mapping.editorRangeOf(Side.RIGHT, range);

        Assert.assertNotNull(editorRange);
        Assert.assertEquals(editorRange.startLine, 3);
        Assert.assertEquals(editorRange.endLine, 4);
    }

    private static LocalLineMapping mapping(int patchSetLineCount, Range... ranges) {
        LocalLineMapping mapping = new LocalLineMapping();
        mapping.set(ranges.length == 0 ? Collections.emptyList() : Arrays.asList(ranges), patchSetLineCount);
        return mapping;
    }
}
