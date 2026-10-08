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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import org.jetbrains.annotations.Nullable;

/**
 * Where on a side of a diff a new comment goes: the line, counted from 1 as Gerrit does, and the range on the side if
 * it is about a selection.
 */
final class CommentPosition {
    final Side side;
    final int line;
    @Nullable
    final Comment.Range range;

    private CommentPosition(Side side, int line, @Nullable Comment.Range range) {
        this.side = side;
        this.line = line;
        this.range = range;
    }

    /** The position of the selection or else of the caret, or null where a comment cannot go. */
    @Nullable
    static CommentPosition read(Editor editor, LineMapping mapping) {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection()) {
            CharSequence text = editor.getDocument().getCharsSequence();
            return onRange(mapping, RangeUtils.textOffsetToRange(text,
                selectionModel.getBlockSelectionStarts()[0], selectionModel.getBlockSelectionEnds()[0]));
        }
        return onLine(mapping, editor.getDocument().getLineNumber(editor.getCaretModel().getOffset()));
    }

    @Nullable
    static CommentPosition onLine(LineMapping mapping, int editorLine) {
        Side side = sideOf(mapping, editorLine, editorLine);
        return side != null ? new CommentPosition(side, mapping.toSideLine(side, editorLine) + 1, null) : null;
    }

    /**
     * @param editorRange in the lines of the editor
     * @return null if no side has both ends of the range, as a selection from a removed to an added line in a unified
     * diff
     */
    @Nullable
    static CommentPosition onRange(LineMapping mapping, Comment.Range editorRange) {
        Side side = sideOf(mapping, editorRange.startLine - 1, editorRange.endLine - 1);
        if (side == null) return null;
        Comment.Range range = new Comment.Range();
        range.startLine = mapping.toSideLine(side, editorRange.startLine - 1) + 1;
        range.startCharacter = editorRange.startCharacter;
        range.endLine = mapping.toSideLine(side, editorRange.endLine - 1) + 1;
        range.endCharacter = editorRange.endCharacter;
        return new CommentPosition(side, range.endLine, range); // the end line, as Gerrit specifies
    }

    @Nullable
    private static Side sideOf(LineMapping mapping, int startLine, int endLine) {
        Side preferred = mapping.preferredSide();
        for (Side side : new Side[]{preferred, preferred.other()}) {
            if (mapping.toSideLine(side, startLine) >= 0 && mapping.toSideLine(side, endLine) >= 0) {
                return side;
            }
        }
        return null;
    }
}
