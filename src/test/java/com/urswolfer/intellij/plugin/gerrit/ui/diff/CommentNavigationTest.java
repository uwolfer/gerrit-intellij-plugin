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

import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.CommentNavigation.Position;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommentNavigationTest {

    @Test
    public void testOneEditorGoesByLine() {
        List<Position> comments = Arrays.asList(right(9), right(2), right(5));

        Assert.assertEquals(right(2), CommentNavigation.next(comments, right(0)));
        Assert.assertEquals(right(5), CommentNavigation.next(comments, right(2)));
        Assert.assertEquals(right(5), CommentNavigation.next(comments, right(3)));
        Assert.assertEquals(right(5), CommentNavigation.previous(comments, right(9)));
        Assert.assertEquals(right(2), CommentNavigation.previous(comments, right(4)));
    }

    @Test
    public void testStopsAtTheEnds() {
        List<Position> comments = Arrays.asList(right(2), right(5));

        Assert.assertNull(CommentNavigation.next(comments, right(5)));
        Assert.assertNull(CommentNavigation.next(comments, right(7)));
        Assert.assertNull(CommentNavigation.previous(comments, right(2)));
        Assert.assertNull(CommentNavigation.previous(comments, right(0)));
        Assert.assertNull(CommentNavigation.next(Collections.emptyList(), right(0)));
    }

    @Test
    public void testCommentsOnOneLineAreOneStop() {
        List<Position> comments = Arrays.asList(right(2), right(4), right(4), right(6));

        Assert.assertEquals(right(4), CommentNavigation.next(comments, right(2)));
        Assert.assertEquals(right(6), CommentNavigation.next(comments, right(4)));
        Assert.assertEquals(right(2), CommentNavigation.previous(comments, right(4)));
    }

    @Test
    public void testSidesInterleaveByAlignedLine() {
        // the left has two lines more above, so its line 7 sits next to line 5 of the right
        Position left7 = new Position(Side.LEFT, 7, 5);
        Position left1 = new Position(Side.LEFT, 1, 1);
        List<Position> comments = Arrays.asList(right(8), left7, right(3), left1);

        List<Position> forward = walk(comments, new Position(Side.LEFT, 0, 0), true);
        Assert.assertEquals(Arrays.asList(left1, right(3), left7, right(8)), forward);
        List<Position> backward = walk(comments, right(20), false);
        Collections.reverse(backward);
        Assert.assertEquals(forward, backward);
    }

    @Test
    public void testOnAlignedLinesTheLeftComesFirst() {
        Position left4 = new Position(Side.LEFT, 4, 4);
        List<Position> comments = Arrays.asList(right(4), left4);

        Assert.assertEquals(right(4), CommentNavigation.next(comments, left4));
        Assert.assertEquals(left4, CommentNavigation.previous(comments, right(4)));
        Assert.assertEquals(left4, CommentNavigation.next(comments, right(3)));
    }

    @Test
    public void testLinesOnlyOneSideHasGoInTheirOrder() {
        // lines 10 to 12 of the left were removed, and all sit next to line 9 of the right
        Position left10 = new Position(Side.LEFT, 10, 9);
        Position left12 = new Position(Side.LEFT, 12, 9);
        List<Position> comments = Arrays.asList(left12, right(9), left10);

        Assert.assertEquals(Arrays.asList(left10, left12, right(9)),
            walk(comments, new Position(Side.LEFT, 0, 0), true));
        Assert.assertEquals(left12, CommentNavigation.next(comments, new Position(Side.LEFT, 11, 9)));
    }

    @Test
    public void testFacesUnchangedLinesOneToOne() {
        List<Range> changes = Collections.singletonList(new Range(3, 4, 3, 4));

        for (int line = 0; line < 10; line++) {
            Assert.assertEquals(line, CommentNavigation.facingLine(Side.LEFT, line, changes));
            Assert.assertEquals(line, CommentNavigation.facingLine(Side.RIGHT, line, changes));
        }
        Assert.assertEquals(7, CommentNavigation.facingLine(Side.LEFT, 7, Collections.emptyList()));
    }

    @Test
    public void testFacesTheLineAfterLinesOnlyTheRightHasWithItsOwn() {
        // right lines 5 to 7 inserted: left line 5 is shown next to right line 8, not next to the first inserted one
        List<Range> changes = Collections.singletonList(new Range(5, 5, 5, 8));

        Assert.assertEquals(4, CommentNavigation.facingLine(Side.LEFT, 4, changes));
        Assert.assertEquals(8, CommentNavigation.facingLine(Side.LEFT, 5, changes));
        Assert.assertEquals(12, CommentNavigation.facingLine(Side.LEFT, 9, changes));
        Assert.assertEquals(5, CommentNavigation.facingLine(Side.RIGHT, 6, changes));
        Assert.assertEquals(5, CommentNavigation.facingLine(Side.RIGHT, 8, changes));
    }

    @Test
    public void testFacesLinesOnlyOneSideHasWithTheLineAfterThem() {
        // left lines 10 to 12 removed: they, and left line 13 after them, are next to right line 10
        List<Range> changes = Collections.singletonList(new Range(10, 13, 10, 10));

        Assert.assertEquals(9, CommentNavigation.facingLine(Side.LEFT, 9, changes));
        Assert.assertEquals(10, CommentNavigation.facingLine(Side.LEFT, 10, changes));
        Assert.assertEquals(10, CommentNavigation.facingLine(Side.LEFT, 12, changes));
        Assert.assertEquals(10, CommentNavigation.facingLine(Side.LEFT, 13, changes));
        Assert.assertEquals(16, CommentNavigation.facingLine(Side.LEFT, 19, changes));
        Assert.assertEquals(13, CommentNavigation.facingLine(Side.RIGHT, 10, changes));
    }

    @Test
    public void testFacesAModifiedBlockLineByLine() {
        // left lines 3 and 4 modified into right lines 3 to 7
        List<Range> longerRight = Collections.singletonList(new Range(3, 5, 3, 8));
        Assert.assertEquals(4, CommentNavigation.facingLine(Side.LEFT, 4, longerRight));
        Assert.assertEquals(8, CommentNavigation.facingLine(Side.LEFT, 5, longerRight));
        Assert.assertEquals(5, CommentNavigation.facingLine(Side.RIGHT, 6, longerRight));

        // left lines 3 to 7 modified into right lines 3 and 4: those past the right ones are next to right line 5
        List<Range> longerLeft = Collections.singletonList(new Range(3, 8, 3, 5));
        Assert.assertEquals(4, CommentNavigation.facingLine(Side.LEFT, 4, longerLeft));
        Assert.assertEquals(5, CommentNavigation.facingLine(Side.LEFT, 5, longerLeft));
        Assert.assertEquals(5, CommentNavigation.facingLine(Side.LEFT, 7, longerLeft));
        Assert.assertEquals(5, CommentNavigation.facingLine(Side.LEFT, 8, longerLeft));
    }

    @Test
    public void testFacesLinesAtTheEdgesOfTheFile() {
        // the first three lines of the left removed, and right lines 7 to 9 added at the end
        List<Range> changes = Arrays.asList(new Range(0, 3, 0, 0), new Range(10, 10, 7, 10));

        Assert.assertEquals(0, CommentNavigation.facingLine(Side.LEFT, 0, changes));
        Assert.assertEquals(0, CommentNavigation.facingLine(Side.LEFT, 3, changes));
        Assert.assertEquals(6, CommentNavigation.facingLine(Side.LEFT, 9, changes));
        Assert.assertEquals(3, CommentNavigation.facingLine(Side.RIGHT, 0, changes));
        Assert.assertEquals(10, CommentNavigation.facingLine(Side.RIGHT, 8, changes));
    }

    @Test
    public void testFacesLinesAcrossSeveralBlocks() {
        // a line removed at 2, then right lines 6 and 7 inserted before left line 7
        List<Range> changes = Arrays.asList(new Range(2, 3, 2, 2), new Range(7, 7, 6, 8));

        Assert.assertEquals(4, CommentNavigation.facingLine(Side.LEFT, 5, changes));
        Assert.assertEquals(8, CommentNavigation.facingLine(Side.LEFT, 7, changes));
        Assert.assertEquals(9, CommentNavigation.facingLine(Side.LEFT, 8, changes));
        Assert.assertEquals(7, CommentNavigation.facingLine(Side.RIGHT, 8, changes));
    }

    private static List<Position> walk(List<Position> comments, Position from, boolean forward) {
        List<Position> visited = new ArrayList<>();
        Position position = from;
        while ((position = forward
            ? CommentNavigation.next(comments, position)
            : CommentNavigation.previous(comments, position)) != null) {
            visited.add(position);
        }
        return visited;
    }

    private static Position right(int line) {
        return new Position(Side.RIGHT, line, line);
    }
}
