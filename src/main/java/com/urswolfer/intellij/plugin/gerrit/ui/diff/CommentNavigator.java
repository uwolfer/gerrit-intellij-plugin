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

import com.intellij.diff.tools.simple.SimpleDiffChange;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Moves the caret of a diff viewer to its next or previous comment. The comments are looked up as the editors show
 * them at the time, so that those saved, removed or loaded again since the diff opened count as well.
 */
final class CommentNavigator {
    static final Key<CommentNavigator> KEY = Key.create("gerrit.CommentNavigator");

    private final DiffComments comments;
    @Nullable
    private final TwosideTextDiffViewer twosideViewer;
    @Nullable
    private CommentNavigation.Position last;
    // the carets a step put next to the comment it went to, until they move otherwise
    private final Set<Editor> placedCarets = new HashSet<>();
    private boolean placing;

    /**
     * @param twosideViewer the viewer of a side-by-side diff, whose editors are one per side; the editor of any other
     *                      viewer shows what it shows in one
     */
    CommentNavigator(DiffComments comments, @Nullable TwosideTextDiffViewer twosideViewer) {
        this.comments = comments;
        this.twosideViewer = twosideViewer;
    }

    /**
     * @param viewer what the editor is shown in, which the navigator goes with
     */
    void addEditor(Editor editor, Disposable viewer) {
        editor.putUserData(KEY, this);
        editor.getCaretModel().addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                if (!placing) placedCarets.remove(editor);
            }
        }, viewer);
    }

    boolean canGo(Editor editor, boolean forward) {
        return target(editor, forward, changes()) != null;
    }

    /**
     * @return whether there was a comment to go to
     */
    boolean go(Editor editor, boolean forward) {
        List<Range> changes = changes();
        CommentNavigation.Position target = target(editor, forward, changes);
        if (target == null) return false;
        Editor targetEditor = twosideViewer != null ? twosideViewer.getEditor(target.side) : editor;
        expandFoldAt(targetEditor, target.line);
        last = target;
        placedCarets.clear();
        placing = true;
        try {
            DiffUtil.scrollEditor(targetEditor, target.line, false);
            placedCarets.add(targetEditor);
            if (twosideViewer != null) {
                Editor other = twosideViewer.getEditor(target.side.other());
                int otherLine = Math.max(0, Math.min(CommentNavigation.facingLine(target.side, target.line, changes),
                    other.getDocument().getLineCount() - 1));
                DiffUtil.moveCaret(other, otherLine);
                placedCarets.add(other);
            }
        } finally {
            placing = false;
        }
        if (twosideViewer != null) {
            // the side-by-side viewer takes the side which has the focus as its current one
            DiffUtil.requestFocus(targetEditor.getProject(), targetEditor.getContentComponent());
        }
        return true;
    }

    /**
     * An unchanged fragment the diff collapsed is expanded by a caret moved into it, but not by one put where it
     * starts, the start of a line, nor by one where it ends, as on an empty last line: a comment there would stay
     * hidden.
     */
    private static void expandFoldAt(Editor editor, int line) {
        int offset = editor.getDocument().getLineStartOffset(line);
        FoldingModel folding = editor.getFoldingModel();
        List<FoldRegion> collapsed = new ArrayList<>();
        for (FoldRegion region : folding.getAllFoldRegions()) {
            if (!region.isExpanded() && region.getStartOffset() <= offset && offset <= region.getEndOffset()) {
                collapsed.add(region);
            }
        }
        if (collapsed.isEmpty()) return;
        folding.runBatchFoldingOperation(() -> collapsed.forEach(region -> region.setExpanded(true)));
    }

    @Nullable
    private CommentNavigation.Position target(Editor editor, boolean forward, List<Range> changes) {
        Side side = side(editor);
        if (side == null) return null;
        List<CommentNavigation.Position> positions = new ArrayList<>();
        for (EditorEx commented : comments.getEditors()) {
            Side commentedSide = side(commented);
            if (commentedSide == null) continue;
            for (int line : comments.commentLines(commented)) {
                positions.add(position(commentedSide, line, changes));
            }
        }
        CommentNavigation.Position from = from(editor, side, changes);
        return forward ? CommentNavigation.next(positions, from) : CommentNavigation.previous(positions, from);
    }

    /**
     * The comment gone to last while the caret is still where that put it: the caret of the other side, where the
     * focus did not follow, is next to the comment rather than at it, and would not tell which comment is next.
     */
    private CommentNavigation.Position from(Editor editor, Side side, List<Range> changes) {
        // aligned anew, as a rediff since may have changed which lines face each other
        return last != null && placedCarets.contains(editor)
            ? position(last.side, last.line, changes)
            : position(side, editor.getCaretModel().getLogicalPosition().line, changes);
    }

    private static CommentNavigation.Position position(Side side, int line, List<Range> changes) {
        return new CommentNavigation.Position(side, line,
            side == Side.LEFT ? CommentNavigation.facingLine(Side.LEFT, line, changes) : line);
    }

    /**
     * The changed blocks of the side-by-side viewer as compared last. Not through the line transfer of its sync
     * scrolling, which takes a line to where its block begins on the other side, and the first line after lines only
     * one side has to the first of them.
     */
    private List<Range> changes() {
        if (!(twosideViewer instanceof SimpleDiffViewer)) return Collections.emptyList();
        List<Range> changes = new ArrayList<>();
        for (SimpleDiffChange change : ((SimpleDiffViewer) twosideViewer).getDiffChanges()) {
            changes.add(new Range(change.getStartLine(Side.LEFT), change.getEndLine(Side.LEFT),
                change.getStartLine(Side.RIGHT), change.getEndLine(Side.RIGHT)));
        }
        return changes;
    }

    @Nullable
    private Side side(Editor editor) {
        if (comments.getMapping(editor) == null) return null;
        if (twosideViewer == null) return Side.RIGHT;
        return twosideViewer.getEditor(Side.LEFT) == editor ? Side.LEFT : Side.RIGHT;
    }
}
