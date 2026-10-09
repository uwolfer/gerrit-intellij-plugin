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
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Which comment of a diff comes next from a position, in the order the diff shows its lines: in a side-by-side
 * diff, those of both sides by the line on the right they are aligned with, so that stepping through goes down the
 * diff and changes sides where the comments do.
 */
final class CommentNavigation {

    private CommentNavigation() {}

    /**
     * The line on the other side which a line is shown next to, given the changed blocks of the diff in their order.
     * A line in a block which the other side has fewer lines of, past them, goes to the line after that block, as
     * do the lines only one side has; next to it, those of the left come first.
     */
    static int facingLine(Side side, int line, List<Range> changes) {
        int shift = 0;
        for (Range change : changes) {
            int start = side.select(change.start1, change.start2);
            int end = side.select(change.end1, change.end2);
            int otherStart = side.select(change.start2, change.start1);
            int otherEnd = side.select(change.end2, change.end1);
            if (line < start) break;
            if (line < end) return Math.min(otherStart + line - start, otherEnd);
            shift = otherEnd - end;
        }
        return line + shift;
    }

    @Nullable
    static Position next(Collection<Position> comments, Position from) {
        return comments.stream().filter(comment -> comment.compareTo(from) > 0).min(Position::compareTo).orElse(null);
    }

    @Nullable
    static Position previous(Collection<Position> comments, Position from) {
        return comments.stream().filter(comment -> comment.compareTo(from) < 0).max(Position::compareTo).orElse(null);
    }

    static final class Position implements Comparable<Position> {
        final Side side;
        final int line;
        // lines of a side which the other one lacks are aligned with the same line, the one next to them
        final int alignedLine;

        Position(Side side, int line, int alignedLine) {
            this.side = side;
            this.line = line;
            this.alignedLine = alignedLine;
        }

        @Override
        public int compareTo(Position other) {
            if (alignedLine != other.alignedLine) return Integer.compare(alignedLine, other.alignedLine);
            if (side != other.side) return Integer.compare(side.getIndex(), other.side.getIndex());
            return Integer.compare(line, other.line);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Position)) return false;
            Position other = (Position) o;
            return side == other.side && line == other.line && alignedLine == other.alignedLine;
        }

        @Override
        public int hashCode() {
            return Objects.hash(side, line, alignedLine);
        }

        @Override
        public String toString() {
            return side + ":" + line + "@" + alignedLine;
        }
    }
}
