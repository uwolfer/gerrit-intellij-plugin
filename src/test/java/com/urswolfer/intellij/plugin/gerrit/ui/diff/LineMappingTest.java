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
import com.intellij.diff.util.Side;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LineMappingTest {
    private final LineMapping right = TableLineMapping.ofSide(Side.RIGHT, 3);

    @Test
    public void testCommentOnItsLine() {
        Assert.assertEquals(right.editorLineOf(Side.RIGHT, 2), 1);
    }

    @Test
    public void testCommentOnTheFileOnFirstLine() {
        Assert.assertEquals(right.editorLineOf(Side.RIGHT, null), 0);
        Assert.assertEquals(right.editorLineOf(Side.RIGHT, 0), 0);
    }

    @Test
    public void testCommentBeyondTheEndOnLastLine() {
        Assert.assertEquals(right.editorLineOf(Side.RIGHT, 7), 2);
    }

    @Test
    public void testCommentOnASideTheEditorDoesNotShow() {
        Assert.assertEquals(right.editorLineOf(Side.LEFT, 1), -1);
    }

    @Test
    public void testRangeKeepsItsCharacters() {
        Comment.Range range = right.editorRangeOf(Side.RIGHT, range(2, 1, 3, 4));
        Assert.assertNotNull(range);
        Assert.assertEquals(range.startLine, 2);
        Assert.assertEquals(range.startCharacter, 1);
        Assert.assertEquals(range.endLine, 3);
        Assert.assertEquals(range.endCharacter, 4);
    }

    @Test
    public void testNoRangeWhereAnEndIsNotShown() {
        Assert.assertNull(right.editorRangeOf(Side.RIGHT, range(2, 0, 9, 0)));
        Assert.assertNull(right.editorRangeOf(Side.LEFT, range(1, 0, 1, 2)));
    }

    @Test
    public void testCommentsOfBothSidesInAUnifiedDiff() {
        LineMapping unified = new TableLineMapping(Side.RIGHT, new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 1});
        Assert.assertEquals(unified.editorLineOf(Side.LEFT, 1), 0);
        Assert.assertEquals(unified.editorLineOf(Side.RIGHT, 1), 1);
        Assert.assertEquals(unified.editorLineOf(Side.LEFT, 2), 2);
        Assert.assertEquals(unified.editorLineOf(Side.RIGHT, 2), 2);
    }

    @Test
    public void testNoRangeOverALineTheUnifiedDiffDoesNotShow() {
        // ignoring whitespace, the left line 1 differs in whitespace only and only the right one is shown
        LineMapping unified = new TableLineMapping(Side.RIGHT, new int[]{0, 0}, new int[]{-1, 1}, new int[]{2, 2});
        Assert.assertNull(unified.editorRangeOf(Side.LEFT, range(1, 0, 2, 1)));
        Comment.Range range = unified.editorRangeOf(Side.LEFT, range(1, 0, 3, 1));
        Assert.assertNotNull(range);
        Assert.assertEquals(range.startLine, 1);
        Assert.assertEquals(range.endLine, 3);
    }

    static Comment.Range range(int startLine, int startCharacter, int endLine, int endCharacter) {
        Comment.Range range = new Comment.Range();
        range.startLine = startLine;
        range.startCharacter = startCharacter;
        range.endLine = endLine;
        range.endCharacter = endCharacter;
        return range;
    }
}
