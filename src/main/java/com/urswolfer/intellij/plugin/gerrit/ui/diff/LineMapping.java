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
import org.jetbrains.annotations.Nullable;

/**
 * Between the lines of an editor in a diff and the lines of the sides of the diff which it shows, all counted from 0.
 * Gerrit counts from 1, and so do the comments.
 */
interface LineMapping {
    /**
     * The editor line which shows a line of a side or, where none does, the nearest one; -1 if the editor does not
     * show the side.
     */
    int toEditorLine(Side side, int line);

    /** The editor line which shows a line of a side, or -1. */
    int toEditorLineStrict(Side side, int line);

    /** The line of a side which an editor line shows, or -1. */
    int toSideLine(Side side, int editorLine);

    /** The side which an editor line counts to when it shows a line of each side, as an unchanged line does. */
    Side preferredSide();

    int lineCount(Side side);

    /**
     * Whether the lines it gives are those of the text now. An editor of the file is edited before the text is compared
     * again, and in between its comments stay where the edits moved them.
     */
    default boolean fitsText() {
        return true;
    }

    /** Why the caret or the selection is on no line a comment can go on. */
    default String noPositionHint(boolean selection) {
        return selection
            ? "A comment is on one side of the diff: start and end the selection on lines of the same side"
            : "There is no line of the diff here to comment on";
    }

    /**
     * The editor line of a comment: its own line when the editor shows it, else the nearest one, so that a comment
     * never disappears. A comment beyond the end of the file is shown on its last line, one on the file on its first.
     */
    default int editorLineOf(Side side, @Nullable Integer commentLine) {
        int line = Math.min(Math.max((commentLine != null ? commentLine : 0) - 1, 0), lineCount(side) - 1);
        return line >= 0 ? toEditorLine(side, line) : -1;
    }

    /** The range of a comment in the lines of the editor, or null if the editor does not show both ends of it. */
    @Nullable
    default Comment.Range editorRangeOf(Side side, Comment.Range range) {
        int startLine = toEditorLineStrict(side, range.startLine - 1);
        int endLine = toEditorLineStrict(side, range.endLine - 1);
        if (startLine < 0 || endLine < startLine) return null;
        Comment.Range editorRange = new Comment.Range();
        editorRange.startLine = startLine + 1;
        editorRange.startCharacter = range.startCharacter;
        editorRange.endLine = endLine + 1;
        editorRange.endCharacter = range.endCharacter;
        return editorRange;
    }
}
