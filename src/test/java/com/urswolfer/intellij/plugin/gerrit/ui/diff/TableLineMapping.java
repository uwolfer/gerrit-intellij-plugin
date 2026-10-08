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

/**
 * The lines of an editor as a table: for each editor line, the line of the left and of the right side which it shows,
 * or -1.
 */
class TableLineMapping implements LineMapping {
    private final int[][] rows;
    private final Side preferredSide;

    /** @param rows {left, right} per editor line */
    TableLineMapping(Side preferredSide, int[]... rows) {
        this.rows = rows;
        this.preferredSide = preferredSide;
    }

    /** An editor which shows the lines of one side as they are. */
    static TableLineMapping ofSide(Side side, int lineCount) {
        int[][] rows = new int[lineCount][];
        for (int line = 0; line < lineCount; line++) {
            rows[line] = side.select(new int[]{line, -1}, new int[]{-1, line});
        }
        return new TableLineMapping(side, rows);
    }

    @Override
    public int toEditorLine(Side side, int line) {
        // like the platform's approximate conversion: where the line is not shown, the one before it which is
        int nearest = -1;
        for (int editorLine = 0; editorLine < rows.length; editorLine++) {
            int sideLine = side.select(rows[editorLine]);
            if (sideLine == line) return editorLine;
            if (sideLine >= 0 && sideLine < line) nearest = editorLine;
        }
        return lineCount(side) > 0 ? Math.max(nearest, 0) : -1;
    }

    @Override
    public int toEditorLineStrict(Side side, int line) {
        for (int editorLine = 0; editorLine < rows.length; editorLine++) {
            if (side.select(rows[editorLine]) == line) return editorLine;
        }
        return -1;
    }

    @Override
    public int toSideLine(Side side, int editorLine) {
        return editorLine >= 0 && editorLine < rows.length ? side.select(rows[editorLine]) : -1;
    }

    @Override
    public Side preferredSide() {
        return preferredSide;
    }

    @Override
    public int lineCount(Side side) {
        int count = 0;
        for (int[] row : rows) {
            count = Math.max(count, side.select(row) + 1);
        }
        return count;
    }
}
