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
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.diff.tools.util.base.DiffViewerBase;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The comments of a diff and the editors of its viewer which show them. A viewer may rebuild the text of an editor,
 * as the unified one does on every rediff, which leaves the comments on the wrong lines; so they are kept here and
 * laid out again in all the editors whenever the comments or the text change.
 */
final class DiffComments {
    private static final Key<DiffComments> KEY = Key.create("gerrit.DiffComments");

    private static final TextAttributesKey COMMENT_RANGE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey(
        "GERRIT_COMMENT_RANGE", EditorColors.SEARCH_RESULT_ATTRIBUTES);

    private final Project project;
    private final ChangeInfo changeInfo;
    private final CommentSide left;
    private final CommentSide right;
    private final DiffViewerBase viewer;
    private final AddCommentActionBuilder addCommentActionBuilder = new AddCommentActionBuilder();
    private final Map<EditorEx, LineMapping> editors = new LinkedHashMap<>();
    // in the order the icons are added in, each to the left of those on its line already
    private final List<ShownComment> comments = new ArrayList<>();
    private boolean shown;

    DiffComments(Project project, ChangeInfo changeInfo, CommentSide left, CommentSide right, DiffViewerBase viewer) {
        this.project = project;
        this.changeInfo = changeInfo;
        this.left = left;
        this.right = right;
        this.viewer = viewer;
    }

    void addEditor(EditorEx editor, LineMapping mapping) {
        editors.put(editor, mapping);
        editor.putUserData(KEY, this);
    }

    Iterable<EditorEx> getEditors() {
        return editors.keySet();
    }

    LineMapping getMapping(Editor editor) {
        return editors.get(editor);
    }

    AddCommentActionBuilder getAddCommentActionBuilder() {
        return addCommentActionBuilder;
    }

    ChangeInfo getChangeInfo() {
        return changeInfo;
    }

    CommentSide getSide(Side side) {
        return side.select(left, right);
    }

    /**
     * Shows the comments from now on, and again on the text the editors have now. Until the first call, an editor may
     * not have the text yet which the mapping is about.
     */
    void show() {
        shown = true;
        render();
    }

    /** A comment saved just now, which replaces the one with its id, as an edited draft does. */
    void add(Comment comment, CommentSide side) {
        comments.removeIf(entry -> entry.comment.id != null && entry.comment.id.equals(comment.id));
        comments.add(entry(comment, side));
        render();
    }

    /** The comments loaded for a side; one shown already, as a draft saved while they loaded, is the newer one. */
    void addAll(Iterable<? extends Comment> sideComments, CommentSide side) {
        for (Comment comment : sideComments) {
            if (comments.stream().noneMatch(entry -> entry.comment.id != null && entry.comment.id.equals(comment.id))) {
                comments.add(entry(comment, side));
            }
        }
        render();
    }

    void remove(String commentId) {
        comments.removeIf(entry -> commentId.equals(entry.comment.id));
        render();
    }

    /**
     * The comments of the same side in the diff the user looks at now. The diff window builds a new viewer when it
     * steps to another file of the change, so a viewer which a comment was started in is gone if the user stepped
     * away and back while the form was open.
     */
    @Nullable
    DiffComments current(CommentSide side) {
        if (!viewer.isDisposed()) {
            return this;
        }
        for (Editor candidate : EditorFactory.getInstance().getAllEditors()) {
            DiffComments comments = candidate.getUserData(KEY);
            if (comments != null
                && !comments.viewer.isDisposed()
                && comments.project == project
                && comments.changeInfo._number == changeInfo._number
                && (comments.left.isSameAs(side) || comments.right.isSameAs(side))) {
                return comments;
            }
        }
        return null;
    }

    private void render() {
        if (!shown || viewer.isDisposed()) return;
        editors.forEach((editor, mapping) -> {
            clear(editor);
            for (ShownComment shownComment : comments) {
                place(editor, mapping, shownComment);
            }
        });
    }

    private void clear(EditorEx editor) {
        MarkupModel markup = editor.getMarkupModel();
        for (RangeHighlighter highlighter : markup.getAllHighlighters()) {
            if (highlighter.getGutterIconRenderer() instanceof CommentGutterIconRenderer) {
                RangeHighlighter rangeHighlighter =
                    ((CommentGutterIconRenderer) highlighter.getGutterIconRenderer()).getRangeHighlighter();
                markup.removeHighlighter(highlighter);
                highlighter.dispose();
                if (rangeHighlighter != null) {
                    HighlightManager.getInstance(project).removeSegmentHighlighter(editor, rangeHighlighter);
                }
            }
        }
    }

    private void place(EditorEx editor, LineMapping mapping, ShownComment shownComment) {
        Comment comment = shownComment.comment;
        int line = Math.min(mapping.editorLineOf(shownComment.side, comment.line),
            editor.getDocument().getLineCount() - 1);
        if (line < 0) return;

        RangeHighlighter rangeHighlighter = null;
        if (comment.range != null) {
            Comment.Range editorRange = mapping.editorRangeOf(shownComment.side, comment.range);
            if (editorRange != null) {
                rangeHighlighter = highlightRange(editor, editorRange);
            }
        }
        RangeHighlighter highlighter = editor.getMarkupModel().addLineHighlighter(line, HighlighterLayer.ERROR + 1, null);
        highlighter.setGutterIconRenderer(new CommentGutterIconRenderer(
            this, editor, addCommentActionBuilder, comment, getSide(shownComment.side), rangeHighlighter));
    }

    @Nullable
    private RangeHighlighter highlightRange(Editor editor, Comment.Range range) {
        Document document = editor.getDocument();
        if (document.getLineCount() == 0) return null;
        int end = offset(document, range.endLine, range.endCharacter);
        int start = Math.min(offset(document, range.startLine, range.startCharacter), end);

        List<RangeHighlighter> highlighters = new ArrayList<>();
        HighlightManager.getInstance(project).addRangeHighlight(
            editor, start, end, COMMENT_RANGE_ATTRIBUTES, false, highlighters);
        return highlighters.isEmpty() ? null : highlighters.get(0);
    }

    /**
     * Kept within its line: a range from elsewhere may not fit the text which the editor shows, such as the unified
     * one, which shows a line once when the sides differ in whitespace only, as the text of one of them.
     */
    private static int offset(Document document, int line, int character) {
        int lineIndex = Math.min(Math.max(line - 1, 0), document.getLineCount() - 1);
        return Math.min(document.getLineStartOffset(lineIndex) + Math.max(character, 0),
            document.getLineEndOffset(lineIndex));
    }

    private ShownComment entry(Comment comment, CommentSide side) {
        comment.path = side.filePath;
        return new ShownComment(comment, side.isSameAs(right) ? Side.RIGHT : Side.LEFT);
    }

    private static final class ShownComment {
        final Comment comment;
        final Side side;

        ShownComment(Comment comment, Side side) {
            this.comment = comment;
            this.side = side;
        }
    }
}
