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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

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
    // under their lines, where the setting has them
    private final Map<EditorEx, List<ShownThread>> threads = new LinkedHashMap<>();
    // by id, as the state of their thread had them when first shown, or the user folded or unfolded them since
    private final Map<String, Boolean> folded = new HashMap<>();
    // weakly, as a rediff replaces them
    private final Set<FoldRegion> unfolded = Collections.newSetFromMap(new WeakHashMap<>());
    // laid out as the settings and the account say now; one whose text did not fit its comments then is not
    private final Set<EditorEx> upToDate = new HashSet<>();
    private int localChanges;
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

    boolean isDisposed() {
        return disposed.getAsBoolean();
    }

    /** Counts the comments saved and removed here, which comments loaded before do not have yet. */
    int localChanges() {
        return localChanges;
    }

    void addEditor(EditorEx editor, LineMapping mapping) {
        editors.put(editor, mapping);
        editor.putUserData(KEY, this);
    }

    void removeEditor(EditorEx editor) {
        keepingScrollPosition(editor, () -> clear(editor));
        CommentInlays.release(editor);
        upToDate.remove(editor);
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
        return index == placed.size() && areThreadsLaidOut(editor, mapping);
    }

    /** A thread goes where the end of its line was edited, as when the line was split in two. */
    private boolean areThreadsLaidOut(EditorEx editor, LineMapping mapping) {
        if (!upToDate.contains(editor)) return false;
        List<ShownThread> shown = threads.get(editor);
        if (shown == null) return !GerritSettings.getInstance().getShowCommentsInline();
        Document document = editor.getDocument();
        for (ShownThread thread : shown) {
            if (!thread.inlay.isValid()
                || document.getLineNumber(thread.inlay.getOffset()) != threadLine(editor, mapping, thread.root)) {
                return false;
            }
        }
        return true;
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
        localChanges++;
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

    /** The comments of both sides as loaded again, in place of those there were, as after a review published drafts. */
    void replaceAll(Iterable<? extends Comment> leftComments, Iterable<? extends Comment> rightComments) {
        comments.clear();
        for (Comment comment : leftComments) {
            comments.add(entry(comment, left));
        }
        for (Comment comment : rightComments) {
            comments.add(entry(comment, right));
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
        localChanges++;
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
        Set<DiffComments> showing = live();
        showing.removeIf(comments -> comments.project != project
            || comments.changeInfo._number != changeInfo._number
            || !comments.left.isSameAs(side) && !comments.right.isSameAs(side));
        return showing;
    }

    /** Those of the diffs and editors open, of every project. */
    private static Set<DiffComments> live() {
        Set<DiffComments> live = new LinkedHashSet<>();
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            DiffComments comments = editor.getUserData(KEY);
            if (comments != null && !comments.disposed.getAsBoolean()) {
                live.add(comments);
            }
        }
        return live;
    }

    private void render() {
        if (!shown || disposed.getAsBoolean()) return;
        editors.forEach((editor, mapping) -> {
            if (!mapping.fitsText()) return; // what is shown moves with the text, and is laid out again once it fits
            Runnable layout = () -> {
                clear(editor);
                List<RangeHighlighter> placed = new ArrayList<>();
                for (ShownComment shownComment : comments) {
                    RangeHighlighter highlighter = place(editor, mapping, shownComment);
                    if (highlighter != null) {
                        placed.add(highlighter);
                    }
                }
                highlighters.put(editor, placed);
                if (GerritSettings.getInstance().getShowCommentsInline()) {
                    List<ShownThread> shownThreads = placeThreads(editor, mapping);
                    threads.put(editor, shownThreads);
                    if (shownThreads.isEmpty()) {
                        CommentInlays.release(editor);
                    } else if (!EditorComments.isFileEditor(editor)) { // the folds of the file are the user's own
                        unfold(editor, shownThreads);
                    }
                } else {
                    CommentInlays.release(editor);
                }
            };
            keepingScrollPosition(editor, layout);
            upToDate.add(editor);
        });
    }

    /**
     * Further down, the threads above the lines in view are taken off and put back, which would move those lines; at
     * the top, the threads on the file come above the first line, and stay in view. By the line at the top rather than
     * the caret, as the platform's keeper would where the caret is in view: a thread which grew or shrank by the
     * reply just clicked moves the lines below it, as folding one of its comments does, and stays where it is.
     */
    private void keepingScrollPosition(EditorEx editor, Runnable layout) {
        boolean threadsMove = threads.containsKey(editor) || GerritSettings.getInstance().getShowCommentsInline();
        ScrollingModel scrolling = editor.getScrollingModel();
        if (!threadsMove || scrolling.getVerticalScrollOffset() <= 0) {
            layout.run();
            return;
        }
        Rectangle visible = scrolling.getVisibleArea();
        int top = editor.logicalPositionToOffset(editor.xyToLogicalPosition(visible.getLocation()));
        RangeMarker marker = editor.getDocument().createRangeMarker(top, top);
        int shift = editor.offsetToXY(top).y - visible.y;
        try {
            layout.run();
            if (marker.isValid()) {
                scrolling.disableAnimation();
                scrolling.scrollVertically(editor.offsetToXY(marker.getStartOffset()).y - shift);
                scrolling.enableAnimation();
            }
        } finally {
            marker.dispose();
        }
    }

    /** After a setting which the threads under their lines go by changed, in every diff and editor. */
    static void settingsChanged() {
        live().forEach(DiffComments::refresh);
    }

    /** Where the threads are shown, which offer to reply only with an account to reply with. */
    void refreshThreads() {
        if (GerritSettings.getInstance().getShowCommentsInline()) {
            refresh();
        }
    }

    /** Lays out every editor anew, and one whose text does not fit its comments now once it does. */
    void refresh() {
        upToDate.clear();
        render();
    }

    /**
     * Takes the comments off an editor. Its own highlighters are kept track of, as the markup of an editor of the file
     * itself holds those of every inspection as well.
     */
    private void clear(EditorEx editor) {
        List<ShownThread> shownThreads = threads.remove(editor);
        if (shownThreads != null) {
            shownThreads.forEach(thread -> Disposer.dispose(thread.inlay));
        }
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

    private List<ShownThread> placeThreads(EditorEx editor, LineMapping mapping) {
        List<CommentThreads.Thread<ShownComment>> all = new ArrayList<>();
        for (Side side : Side.values()) { // a thread is on one side, which a unified viewer shows along with the other
            all.addAll(threads(side));
        }
        all.sort(Comparator.comparing(thread -> thread.root.comment, CommentThreads.order()));
        List<ShownThread> shown = new ArrayList<>();
        int index = 0;
        for (CommentThreads.Thread<ShownComment> thread : all) {
            int line = threadLine(editor, mapping, thread.root);
            if (line < 0) continue; // the editor does not show the side
            CommentInlays inlays = CommentInlays.of(editor);
            CommentThreadPanel panel = new CommentThreadPanel(thread,
                actions(editor, thread, getSide(thread.root.side)), inlays::width, inlays.avatarIcons());
            // in the order the threads were started, from the top, wherever several are on one line; the platform has
            // the one with the larger priority closer to the text
            Inlay<?> inlay = isOnFile(thread.root)
                ? inlays.insertAtTop(panel, index)
                : inlays.insertAfter(line, panel, -index);
            index++;
            if (inlay != null) {
                shown.add(new ShownThread(thread.root, inlay));
            }
        }
        return shown;
    }

    /**
     * As Gerrit's web UI shows the lines around a comment: a diff folds the unchanged ones, and a thread under one of
     * them with it. Once: a fold the user collapsed again stays so, until a rediff folds the text anew. Also a fold
     * which is open already counts as seen, as the diff opens a fold of the other side along with one of this side.
     */
    private void unfold(EditorEx editor, List<ShownThread> shownThreads) {
        FoldingModelEx folding = editor.getFoldingModel();
        FoldRegion[] regions = folding.getAllFoldRegions();
        Set<FoldRegion> collapsed = new LinkedHashSet<>();
        for (ShownThread thread : shownThreads) {
            // one on the file comes above the first line, which no fold hides
            if (!thread.inlay.isRelatedToPrecedingText()) continue;
            int lineEnd = thread.inlay.getOffset();
            boolean seen = true;
            for (FoldRegion region : regions) {
                if (hides(region.getStartOffset(), region.getEndOffset(), lineEnd) && unfolded.add(region)) {
                    seen = false;
                }
            }
            if (seen) continue;
            FoldRegion region = folding.getCollapsedRegionAtOffset(lineEnd);
            if (region == null && lineEnd > 0) {
                region = folding.getCollapsedRegionAtOffset(lineEnd - 1);
            }
            if (region != null && hides(region.getStartOffset(), region.getEndOffset(), lineEnd)) {
                collapsed.add(region);
            }
        }
        if (!collapsed.isEmpty()) {
            folding.runBatchFoldingOperation(() -> collapsed.forEach(region -> region.setExpanded(true)));
        }
    }

    /**
     * Whether a fold hides a line, by an offset in it: the end, where its thread is, or the start. The platform hides
     * a thread from the start of the fold on, to before its end; the thread of the last line it shows under the
     * placeholder, without its line.
     */
    static boolean hides(int foldStart, int foldEnd, int offset) {
        return foldStart <= offset && offset <= foldEnd;
    }

    /** The thread of a comment shown here, by the comments shown now; null for one no longer shown. */
    @Nullable
    CommentThreads.Thread<?> threadOf(Comment comment) {
        // by id, as an editor whose text does not fit its comments keeps icons of comments loaded again since
        ShownComment shown = comments.stream()
            .filter(entry -> entry.comment == comment || comment.id != null && comment.id.equals(entry.comment.id))
            .findFirst().orElse(null);
        if (shown == null) return null;
        return threads(shown.side).stream()
            .filter(thread -> thread.items.contains(shown))
            .findFirst()
            .orElse(null);
    }

    private List<CommentThreads.Thread<ShownComment>> threads(Side side) {
        return CommentThreads.group(comments.stream().filter(entry -> entry.side == side).collect(Collectors.toList()),
            entry -> entry.comment);
    }

    private static boolean isOnFile(ShownComment shownComment) {
        return shownComment.comment.line == null || shownComment.comment.line == 0;
    }

    /** -1 where the editor does not show the side. */
    private int threadLine(EditorEx editor, LineMapping mapping, ShownComment root) {
        int line = editorLine(editor, mapping, root);
        return line >= 0 && isOnFile(root) ? 0 : line;
    }

    private CommentThreadPanel.Actions actions(EditorEx editor, CommentThreads.Thread<ShownComment> thread,
                                               CommentSide side) {
        return new CommentThreadPanel.Actions() {
            /** Also once the change left HEAD, in an editor of the file: a reply goes to the patch set of its comment. */
            @Override
            public boolean canComment() {
                return AddCommentAction.canComment(project);
            }

            @Override
            public AnAction reply(Comment comment, JComponent anchor) {
                return addCommentActionBuilder.create(DiffComments.this, editor).reply(comment, side).below(anchor)
                    .get();
            }

            @Override
            public AnAction resolve(Comment comment, CommentResolveAction.Reply reply) {
                return new CommentResolveAction(DiffComments.this, comment, side, reply);
            }

            @Override
            public AnAction edit(Comment draft, JComponent anchor) {
                return addCommentActionBuilder.create(DiffComments.this, editor).update(draft, side).below(anchor)
                    .get();
            }

            @Override
            public AnAction remove(Comment draft) {
                return new RemoveCommentAction(DiffComments.this, draft, side);
            }

            @Override
            public boolean isFolded(Comment comment) {
                return comment.id != null
                    ? folded.computeIfAbsent(comment.id, id -> thread.isFoldedAtFirst(comment))
                    : thread.isFoldedAtFirst(comment);
            }

            @Override
            public void setFolded(Comment comment, boolean isFolded) {
                if (comment.id != null) {
                    folded.put(comment.id, isFolded);
                }
            }
        };
    }

    private ShownComment entry(Comment comment, CommentSide side) {
        comment.path = side.filePath;
        return new ShownComment(comment, side.isSameAs(right) ? Side.RIGHT : Side.LEFT);
    }

    private static final class ShownThread {
        final ShownComment root;
        final Inlay<?> inlay;

        ShownThread(ShownComment root, Inlay<?> inlay) {
            this.root = root;
            this.inlay = inlay;
        }
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
