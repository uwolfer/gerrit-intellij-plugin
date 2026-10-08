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

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.openapi.progress.ProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The lines of a patch set in an editor of the file itself, which the user may have changed since: what the diff of
 * the patch set and the text tells, computed again as the text changes. The patch set is the right side, as in a diff
 * of the change; the editor shows no left one.
 *
 * TODO once the minimum IDE has com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil as public API
 * (2020.3 has not, 2026.2 has it experimental, from 2023.3 on): keep the ranges with its trackDocumentDiffSync, which
 * follows every edit, and its transferLineToAfter/transferLineFromAfter, and drop transfer(), the stale state and the
 * delayed comparison in EditorComments. Its editorComponentInlaysUtil can show the comments under their lines. Both
 * are Kotlin suspend functions, which need Kotlin and coroutines in the plugin.
 */
final class LocalLineMapping implements LineMapping {
    private List<Range> ranges = Collections.emptyList();
    private int patchSetLineCount;
    // until the text was first compared, no line is known
    private boolean ready;
    // edited since it was last compared: what is shown moves with the text, but a new comment would go astray
    private boolean stale;

    /**
     * @param ranges which differ, the patch set on side 1 and the text on side 2, in order
     */
    void set(List<Range> ranges, int patchSetLineCount) {
        this.ranges = ranges;
        this.patchSetLineCount = patchSetLineCount;
        this.ready = true;
        this.stale = false;
    }

    void markStale() {
        stale = true;
    }

    boolean isStale() {
        return stale;
    }

    /** May take long on a large file, so not on the event dispatch thread. */
    static List<Range> compare(CharSequence patchSet, CharSequence text, ProgressIndicator indicator) {
        List<Range> ranges = new ArrayList<>();
        for (LineFragment fragment : ComparisonManager.getInstance()
            .compareLines(patchSet, text, ComparisonPolicy.DEFAULT, indicator)) {
            ranges.add(new Range(fragment.getStartLine1(), fragment.getEndLine1(),
                fragment.getStartLine2(), fragment.getEndLine2()));
        }
        return ranges;
    }

    @Override
    public int toEditorLine(Side side, int line) {
        return ready && side == Side.RIGHT ? transfer(ranges, line, true, true) : -1;
    }

    @Override
    public int toEditorLineStrict(Side side, int line) {
        return ready && side == Side.RIGHT ? transfer(ranges, line, false, true) : -1;
    }

    /** -1 for a line the user added or changed: the patch set has nothing there to comment on. */
    @Override
    public int toSideLine(Side side, int editorLine) {
        return ready && !stale && side == Side.RIGHT ? transfer(ranges, editorLine, false, false) : -1;
    }

    @Override
    public String noPositionHint(boolean selection) {
        if (!ready || stale) {
            return "The patch set is still being compared with the file";
        }
        return selection
            ? "The selection reaches lines which are not in the patch set: comment on lines it has"
            : "This line is not in the patch set, it changed since: comment on a line the patch set has";
    }

    @Override
    public Side preferredSide() {
        return Side.RIGHT;
    }

    @Override
    public int lineCount(Side side) {
        return side == Side.RIGHT ? patchSetLineCount : 0;
    }

    /**
     * Moves a line across the diff. A line within a changed block keeps its place in the block, as far as the block on
     * the other side reaches, when {@code approximate}; a comment on a line the user rewrote stays near it, and one on
     * a line the user deleted goes to the line which follows now.
     *
     * @param forward from side 1 to side 2
     * @return -1 for a line within a changed block unless {@code approximate}
     */
    static int transfer(List<Range> ranges, int line, boolean approximate, boolean forward) {
        if (line < 0) return -1;
        int otherEnd = 0;
        int end = 0;
        for (Range range : ranges) {
            int start = forward ? range.start1 : range.start2;
            int otherStart = forward ? range.start2 : range.start1;
            end = forward ? range.end1 : range.end2;
            otherEnd = forward ? range.end2 : range.end1;
            if (line < start) {
                return line - start + otherStart;
            }
            if (line < end) {
                if (!approximate) return -1;
                return otherEnd > otherStart ? Math.min(otherStart + line - start, otherEnd - 1) : otherStart;
            }
        }
        return line - end + otherEnd;
    }
}
