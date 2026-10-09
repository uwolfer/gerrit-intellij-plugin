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
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The comments of a diff and the editors which show them, those of a diff viewer or an editor of the file itself. A
 * viewer may rebuild the text of an editor, as the unified one does on every rediff, which leaves the comments on the
 * wrong lines; so they are kept here and laid out again in all the editors whenever the comments or the text change.
 */
final class DiffComments {
    private static final Key<DiffComments> KEY = Key.create("gerrit.DiffComments");

    private static final TextAttributesKey COMMENT_RANGE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey(
        "GERRIT_COMMENT_RANGE", EditorColors.SEARCH_RESULT_ATTRIBUTES);

    private final Project project;
    private final ChangeInfo changeInfo;
    private final CommentSide left;
    private final CommentSide right;
    private final BooleanSupplier disposed;
    private final AddCommentActionBuilder addCommentActionBuilder = new AddCommentActionBuilder();
    private final Map<EditorEx, LineMapping> editors = new LinkedHashMap<>();
    private final Map<EditorEx, List<RangeHighlighter>> highlighters = new LinkedHashMap<>();
    // in the order the icons are added in, each to the left of those on its line already
    private final List<ShownComment> comments = new ArrayList<>();
    private boolean shown;

    /**
     * @param disposed whether what shows the comments is gone, such as the diff viewer
     */
    DiffComments(Project project, ChangeInfo changeInfo, CommentSide left, CommentSide right,
                 BooleanSupplier disposed) {
        this.project = project;
        this.changeInfo = changeInfo;
        this.left = left;
        this.right = right;
        this.disposed = disposed;
    }

    void addEditor(EditorEx editor, LineMapping mapping) {
        editors.put(editor, mapping);
        editor.putUserData(KEY, this);
    }

    void removeEditor(EditorEx editor) {
        clear(editor);
        editors.remove(editor);
        editor.putUserData(KEY, null);
    }

    Iterable<EditorEx> getEditors() {
        return editors.keySet();
    }

    LineMapping getMapping(Editor editor) {
        return editors.get(editor);
    }

    /** The lines of an editor which show a comment, as it is laid out now. */
    List<Integer> commentLines(Editor editor) {
        List<Integer> lines = new ArrayList<>();
        List<RangeHighlighter> placed = highlighters.get(editor);
        if (placed == null) return lines;
        for (RangeHighlighter highlighter : placed) {
            if (highlighter.isValid()) {
                lines.add(editor.getDocument().getLineNumber(highlighter.getStartOffset()));
            }
        }
        return lines;
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


    /**
     * Shows the comments again where one is no longer on the line the mapping gives it, or no longer the one shown, as
     * after comments loaded again meanwhile. Their highlighters move with the text, so after most edits of the file
     * they are where they belong already, and stay.
     */
    void relayout() {
        if (disposed.getAsBoolean()) return;
        if (!shown) {
            show();
            return;
        }
        for (Map.Entry<EditorEx, LineMapping> entry : editors.entrySet()) {
            if (!isLaidOut(entry.getKey(), entry.getValue())) {
                render();
                return;
            }
        }
    }

    private boolean isLaidOut(EditorEx editor, LineMapping mapping) {
        List<RangeHighlighter> placed = highlighters.get(editor);
        if (placed == null) return false;
        Document document = editor.getDocument();
        int index = 0;
        for (ShownComment shownComment : comments) {
            int line = editorLine(editor, mapping, shownComment);
            if (line < 0) continue;
            if (index >= placed.size()) return false;
            RangeHighlighter highlighter = placed.get(index++);
            if (!highlighter.isValid() || document.getLineNumber(highlighter.getStartOffset()) != line) return false;
            // the same object, not an equal one: a draft published meanwhile has the same id, line and text
            if (((CommentGutterIconRenderer) highlighter.getGutterIconRenderer()).getComment() != shownComment.comment) {
                return false;
            }
            if (!isRangeLaidOut(document, mapping, shownComment, highlighter)) return false;
        }
        return index == placed.size();
    }

    /** The highlight of a range goes where an end of it was edited, and comes back with an undo. */
    private static boolean isRangeLaidOut(Document document, LineMapping mapping, ShownComment shownComment,
                                          RangeHighlighter highlighter) {
        Comment.Range range = shownComment.comment.range;
        Comment.Range expected = range != null ? mapping.editorRangeOf(shownComment.side, range) : null;
        RangeHighlighter actual =
            ((CommentGutterIconRenderer) highlighter.getGutterIconRenderer()).getRangeHighlighter();
        if (expected == null || actual == null) return expected == null && actual == null;
        return actual.isValid()
            && document.getLineNumber(actual.getStartOffset()) == expected.startLine - 1
            && document.getLineNumber(actual.getEndOffset()) == expected.endLine - 1;
    }

    /**
     * What the comments of an editor are laid out by now, which replaced these: with the same patch set, as the lines
     * of another would not fit these comments.
     */
    @Nullable
    LineMapping currentMapping(Editor editor) {
        LineMapping mapping = getMapping(editor);
        if (mapping != null) return mapping;
        DiffComments current = editor.getUserData(KEY);
        return current != null && current.right.isSameAs(right) && current.left.isSameAs(left)
            ? current.getMapping(editor) : null;
    }

    private static int editorLine(EditorEx editor, LineMapping mapping, ShownComment shownComment) {
        return Math.min(mapping.editorLineOf(shownComment.side, shownComment.comment.line),
            editor.getDocument().getLineCount() - 1);
    }

    /** A comment saved just now, which replaces the one with its id, as an edited draft does. */
    private void add(Comment comment, CommentSide side) {
        comments.removeIf(entry -> entry.comment.id != null && entry.comment.id.equals(comment.id));
        comments.add(entry(comment, side));
        render();
    }

    /** The comments of a side as loaded again, in place of those there were. */
    void replaceAll(Iterable<? extends Comment> sideComments, CommentSide side) {
        comments.clear();
        for (Comment comment : sideComments) {
            comments.add(entry(comment, side));
        }
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

    private void remove(String commentId) {
        comments.removeIf(entry -> commentId.equals(entry.comment.id));
        render();
    }

    /**
     * A comment saved just now, in every diff and editor which shows its side, and with the changes on HEAD for an
     * editor opened later. The diff window builds a new viewer when it steps to another file of the change, so a
     * viewer which a comment was started in is gone if the user stepped away and back while the form was open.
     */
    void saved(CommentInfo comment, CommentSide side) {
        for (DiffComments comments : showing(side)) {
            comments.add(comment, side);
        }
        HeadChanges headChanges = project.getServiceIfCreated(HeadChanges.class);
        if (headChanges != null) {
            headChanges.saved(changeInfo._number, side, comment);
        }
    }

    void removed(String commentId, CommentSide side) {
        for (DiffComments comments : showing(side)) {
            comments.remove(commentId);
        }
        HeadChanges headChanges = project.getServiceIfCreated(HeadChanges.class);
        if (headChanges != null) {
            headChanges.removed(changeInfo._number, side, commentId);
        }
    }

    private Collection<DiffComments> showing(CommentSide side) {
        Set<DiffComments> showing = new LinkedHashSet<>();
        for (Editor candidate : EditorFactory.getInstance().getAllEditors()) {
            DiffComments comments = candidate.getUserData(KEY);
            if (comments != null
                && !comments.disposed.getAsBoolean()
                && comments.project == project
                && comments.changeInfo._number == changeInfo._number
                && (comments.left.isSameAs(side) || comments.right.isSameAs(side))) {
                showing.add(comments);
            }
        }
        return showing;
    }

    private void render() {
        if (!shown || disposed.getAsBoolean()) return;
        editors.forEach((editor, mapping) -> {
            if (!mapping.fitsText()) return; // what is shown moves with the text, and is laid out again once it fits
            clear(editor);
            List<RangeHighlighter> placed = new ArrayList<>();
            for (ShownComment shownComment : comments) {
                RangeHighlighter highlighter = place(editor, mapping, shownComment);
                if (highlighter != null) {
                    placed.add(highlighter);
                }
            }
            highlighters.put(editor, placed);
        });
    }

    /**
     * Takes the comments off an editor. Its own highlighters are kept track of, as the markup of an editor of the file
     * itself holds those of every inspection as well.
     */
    private void clear(EditorEx editor) {
        List<RangeHighlighter> placed = highlighters.remove(editor);
        if (placed == null) return;
        MarkupModel markup = editor.getMarkupModel();
        for (RangeHighlighter highlighter : placed) {
            RangeHighlighter rangeHighlighter =
                ((CommentGutterIconRenderer) highlighter.getGutterIconRenderer()).getRangeHighlighter();
            markup.removeHighlighter(highlighter);
            highlighter.dispose();
            if (rangeHighlighter != null) {
                markup.removeHighlighter(rangeHighlighter);
                rangeHighlighter.dispose();
            }
        }
    }

    @Nullable
    private RangeHighlighter place(EditorEx editor, LineMapping mapping, ShownComment shownComment) {
        Comment comment = shownComment.comment;
        int line = editorLine(editor, mapping, shownComment);
        if (line < 0) return null;

        RangeHighlighter rangeHighlighter = null;
        if (comment.range != null) {
            Comment.Range editorRange = mapping.editorRangeOf(shownComment.side, comment.range);
            if (editorRange != null) {
                rangeHighlighter = highlightRange(editor, editorRange);
            }
        }
        RangeHighlighter highlighter = editor.getMarkupModel().addLineHighlighter(line, HighlighterLayer.ERROR + 1, null);
        highlighter.setErrorStripeMarkColor(CommentGutterIconRenderer.getErrorStripeColor(comment));
        highlighter.setThinErrorStripeMark(true);
        highlighter.setErrorStripeTooltip(CommentGutterIconRenderer.getErrorStripeTooltip(comment));
        highlighter.setGutterIconRenderer(new CommentGutterIconRenderer(
            this, editor, addCommentActionBuilder, comment, getSide(shownComment.side), rangeHighlighter));
        return highlighter;
    }

    /**
     * On the markup rather than through the HighlightManager, whose highlights go with the next Escape: in an editor of
     * the file itself, that is all the time, and nothing would bring them back.
     */
    @Nullable
    private RangeHighlighter highlightRange(Editor editor, Comment.Range range) {
        Document document = editor.getDocument();
        if (document.getLineCount() == 0) return null;
        int end = offset(document, range.endLine, range.endCharacter);
        int start = Math.min(offset(document, range.startLine, range.startCharacter), end);
        RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(COMMENT_RANGE_ATTRIBUTES,
            start, end, HighlighterLayer.SELECTION - 1, HighlighterTargetArea.EXACT_RANGE);
        // which would take the mark of the search result colours: the comment has a mark of its own
        highlighter.setErrorStripeMarkColor(null);
        return highlighter;
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
