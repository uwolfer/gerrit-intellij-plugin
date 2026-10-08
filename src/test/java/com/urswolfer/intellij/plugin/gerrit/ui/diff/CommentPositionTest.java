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

import com.intellij.diff.util.Side;
import org.testng.Assert;
import org.testng.annotations.Test;

import static com.urswolfer.intellij.plugin.gerrit.ui.diff.LineMappingTest.range;

public class CommentPositionTest {
    /**
     * A unified diff from "Demo keep old" to "Intro keep new": its lines are Demo (removed), Intro (added), keep,
     * old (removed) and new (added).
     */
    private static final int[][] UNIFIED = {{0, -1}, {-1, 0}, {1, 1}, {2, -1}, {-1, 2}};

    private final LineMapping unified = new TableLineMapping(Side.RIGHT, UNIFIED);

    @Test
    public void testLineOfTheSideOfTheEditor() {
        CommentPosition position = CommentPosition.onLine(TableLineMapping.ofSide(Side.LEFT, 3), 1);
        Assert.assertNotNull(position);
        Assert.assertEquals(position.side, Side.LEFT);
        Assert.assertEquals(position.line, 2);
        Assert.assertNull(position.range);
    }

    @Test
    public void testRangeEndsOnItsLastLine() {
        CommentPosition position = CommentPosition.onRange(TableLineMapping.ofSide(Side.RIGHT, 3), range(1, 2, 3, 1));
        Assert.assertNotNull(position);
        Assert.assertEquals(position.side, Side.RIGHT);
        Assert.assertEquals(position.line, 3);
        Assert.assertNotNull(position.range);
        Assert.assertEquals(position.range.startLine, 1);
        Assert.assertEquals(position.range.startCharacter, 2);
        Assert.assertEquals(position.range.endLine, 3);
        Assert.assertEquals(position.range.endCharacter, 1);
    }

    @Test
    public void testRemovedLineIsOnTheLeft() {
        assertPosition(CommentPosition.onLine(unified, 3), Side.LEFT, 3);
    }

    @Test
    public void testAddedLineIsOnTheRight() {
        assertPosition(CommentPosition.onLine(unified, 1), Side.RIGHT, 1);
    }

    @Test
    public void testUnchangedLineIsOnThePreferredSide() {
        assertPosition(CommentPosition.onLine(unified, 2), Side.RIGHT, 2);
        assertPosition(CommentPosition.onLine(new TableLineMapping(Side.LEFT, UNIFIED), 2), Side.LEFT, 2);
    }

    @Test
    public void testRangeIsOnTheSideOfBothEnds() {
        CommentPosition removedToUnchanged = CommentPosition.onRange(unified, range(1, 1, 3, 2));
        assertPosition(removedToUnchanged, Side.LEFT, 2);
        Assert.assertEquals(removedToUnchanged.range.startLine, 1);
        Assert.assertEquals(removedToUnchanged.range.endLine, 2);

        CommentPosition unchangedToAdded = CommentPosition.onRange(unified, range(3, 0, 5, 3));
        assertPosition(unchangedToAdded, Side.RIGHT, 3);
        Assert.assertEquals(unchangedToAdded.range.startLine, 2);
        Assert.assertEquals(unchangedToAdded.range.endLine, 3);
    }

    @Test
    public void testRangeOverUnchangedLinesIsOnThePreferredSide() {
        assertPosition(CommentPosition.onRange(unified, range(3, 0, 3, 4)), Side.RIGHT, 2);
    }

    @Test
    public void testNoRangeFromARemovedToAnAddedLine() {
        Assert.assertNull(CommentPosition.onRange(unified, range(1, 0, 2, 3)));
        Assert.assertNull(CommentPosition.onRange(unified, range(2, 0, 4, 1)));
    }

    private static void assertPosition(CommentPosition position, Side side, int line) {
        Assert.assertNotNull(position);
        Assert.assertEquals(position.side, side);
        Assert.assertEquals(position.line, line);
    }
}
