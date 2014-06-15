/*
 * Copyright 2013 Urs Wolfer
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

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.Comment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.inject.Inject;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class CommentsDiffTool extends CustomizableFrameDiffTool {
    private static final Predicate<Comment> REVISION_COMMENT = new Predicate<Comment>() {
        @Override
        public boolean apply(Comment comment) {
            return comment.side == null || comment.side.equals(Comment.Side.REVISION);
        }
    };

    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private DataManager dataManager;
    @Inject
    private AddCommentActionBuilder addCommentActionBuilder;
    @Inject
    private ReviewCommentSink reviewCommentSink;
    @Inject
    private PathUtils pathUtils;

    private ChangeInfo changeInfo;
    private Optional<Pair<String, RevisionInfo>> baseRevision;
    private Project project;

    @Override
    public boolean canShow(DiffRequest request) {
        final boolean superCanShow = super.canShow(request);

        final AsyncResult<DataContext> dataContextFromFocus = dataManager.getDataContextFromFocus();
        final DataContext context = dataContextFromFocus.getResult();
        if (context == null) return false;

        changeInfo = GerritDataKeys.CHANGE.getData(context);
        baseRevision = GerritDataKeys.BASE_REVISION.getData(context);
        project = PlatformDataKeys.PROJECT.getData(context);

        return superCanShow && changeInfo != null;
    }

    @Override
    public void diffRequestChange(DiffRequest diffRequest, DiffPanelImpl diffPanel) {
        handleComments(diffPanel, diffRequest.getWindowTitle());
    }

    private void handleComments(final DiffPanelImpl diffPanel, final String filePathString) {
        final FilePath filePath = new FilePathImpl(new File(filePathString), false);
        final String relativeFilePath = getRelativePath(project, filePath.getPath());

        addCommentAction(diffPanel, relativeFilePath, changeInfo);

        addDraftComments(diffPanel, relativeFilePath);

        gerritUtil.getChangeDetails(changeInfo._number, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeDetails) {
                gerritUtil.getComments(changeDetails.id, changeDetails.currentRevision, project,
                    new Consumer<Map<String, List<CommentInfo>>>() {
                        @Override
                        public void consume(Map<String, List<CommentInfo>> comments) {
                            List<CommentInfo> fileComments = comments.get(relativeFilePath);
                            if (fileComments != null) {
                                addCommentsGutter(
                                        diffPanel.getEditor2(),
                                        relativeFilePath,
                                        changeInfo.currentRevision,
                                        Iterables.filter(fileComments, REVISION_COMMENT)
                                );
                                if (!baseRevision.isPresent()) {
                                    addCommentsGutter(
                                            diffPanel.getEditor1(),
                                            relativeFilePath,
                                            changeInfo.currentRevision,
                                            Iterables.filter(fileComments, Predicates.not(REVISION_COMMENT))
                                    );
                                }
                            }
                        }
                    });

                if (baseRevision.isPresent()) {
                    gerritUtil.getComments(changeDetails.id, baseRevision.get().getFirst(), project, new Consumer<Map<String, List<CommentInfo>>>() {
                        @Override
                        public void consume(Map<String, List<CommentInfo>> comments) {
                            List<CommentInfo> fileComments = comments.get(relativeFilePath);
                            if (fileComments != null) {
                                addCommentsGutter(
                                        diffPanel.getEditor1(),
                                        relativeFilePath,
                                        baseRevision.get().getFirst(),
                                        Iterables.filter(fileComments, REVISION_COMMENT)
                                );
                            }
                        }
                    });
                }

                gerritUtil.setReviewed(changeDetails.id, changeDetails.currentRevision,
                        relativeFilePath, project);
            }
        });
    }

    private void addDraftComments(DiffPanelImpl diffPanel, String relativeFilePath) {
        List<Comment> draftComments = Lists.newArrayList();
        Iterable<ReviewInput.CommentInput> commentInputsFromSink = reviewCommentSink.getCommentsForChange(changeInfo.id, changeInfo.currentRevision);
        for (ReviewInput.CommentInput commentInput : commentInputsFromSink) {
            if (commentInput.path.equals(relativeFilePath)) {
                draftComments.add(commentInput);
            }
        }
        addCommentsGutter(diffPanel.getEditor2(), relativeFilePath, changeInfo.currentRevision, Iterables.filter(draftComments, REVISION_COMMENT));
        if (!baseRevision.isPresent()) {
            addCommentsGutter(diffPanel.getEditor1(), relativeFilePath, changeInfo.currentRevision, Iterables.filter(draftComments, Predicates.not(REVISION_COMMENT)));
        } else {
            Iterable<ReviewInput.CommentInput> baseRevisionDrafts = reviewCommentSink.getCommentsForChange(changeInfo.id, baseRevision.get().getFirst());
            addCommentsGutter(diffPanel.getEditor1(), relativeFilePath, baseRevision.get().getFirst(), Iterables.filter(baseRevisionDrafts, REVISION_COMMENT));
        }
    }

    private void addCommentAction(DiffPanelImpl diffPanel, String filePath, ChangeInfo changeInfo) {
        if (baseRevision.isPresent()) {
            addCommentActionToEditor(diffPanel.getEditor1(), filePath, changeInfo, baseRevision.get().getFirst(), Comment.Side.REVISION);
        } else {
            addCommentActionToEditor(diffPanel.getEditor1(), filePath, changeInfo, changeInfo.currentRevision, Comment.Side.PARENT);
        }
        addCommentActionToEditor(diffPanel.getEditor2(), filePath, changeInfo, changeInfo.currentRevision, Comment.Side.REVISION);
    }

    private void addCommentActionToEditor(Editor editor, String filePath, ChangeInfo changeInfo, String revisionId, Comment.Side commentSide) {
        if (editor == null) return;

        DefaultActionGroup group = new DefaultActionGroup();
        final AddCommentAction addCommentAction = addCommentActionBuilder
                .create(this, changeInfo, revisionId, editor, filePath, commentSide)
                .withText("Add Comment")
                .withIcon(AllIcons.Toolwindows.ToolWindowMessages)
                .get();
        addCommentAction.registerCustomShortcutSet(CustomShortcutSet.fromString("C"), editor.getComponent());
        group.add(addCommentAction);
        PopupHandler.installUnknownPopupHandler(editor.getContentComponent(), group, ActionManager.getInstance());
    }

    private void addCommentsGutter(Editor editor,
                                   String filePath,
                                   String revisionId,
                                   Iterable<? extends Comment> fileComments) {

        for (Comment fileComment : fileComments) {
            fileComment.path = filePath;
            addComment(editor, changeInfo, revisionId, project, fileComment);
        }
    }

    public void addComment(Editor editor, ChangeInfo changeInfo, String revisionId, Project project, Comment comment) {
        if (editor == null) return;
        MarkupModel markup = editor.getMarkupModel();

        RangeHighlighter rangeHighlighter = null;
        if (comment.range != null) {
            rangeHighlighter = highlightRangeComment(comment.range, editor, project);
        }

        int lineCount = markup.getDocument().getLineCount();

        int line = comment.line - 1;
        if (line < 0) {
            line = 0;
        }
        if (line > lineCount - 1) {
            line = lineCount - 1;
        }
        if (line >= 0) {
            final RangeHighlighter highlighter = markup.addLineHighlighter(line, HighlighterLayer.ERROR + 1, null);
            CommentGutterIconRenderer iconRenderer = new CommentGutterIconRenderer(
                    this, editor, reviewCommentSink, addCommentActionBuilder,
                    comment, changeInfo, revisionId, highlighter, rangeHighlighter);
            highlighter.setGutterIconRenderer(iconRenderer);
        }
    }

    public void removeComment(Editor editor, RangeHighlighter lineHighlighter, RangeHighlighter rangeHighlighter) {
        editor.getMarkupModel().removeHighlighter(lineHighlighter);
        lineHighlighter.dispose();

        if (rangeHighlighter != null) {
            HighlightManager highlightManager = HighlightManager.getInstance(project);
            highlightManager.removeSegmentHighlighter(editor, rangeHighlighter);
        }
    }

    private String getRelativePath(Project project, String absoluteFilePath) {
        return pathUtils.getRelativePath(project, absoluteFilePath, changeInfo.project);
    }

    public static RangeHighlighter highlightRangeComment(Comment.Range range, Editor editor, Project project) {
        CharSequence charsSequence = editor.getMarkupModel().getDocument().getCharsSequence();

        RangeUtils.Offset offset = RangeUtils.rangeToTextOffset(charsSequence, range);

        TextAttributes attributes = new TextAttributes();
        attributes.setBackgroundColor(JBColor.YELLOW);
        ArrayList<RangeHighlighter> highlighters = Lists.newArrayList();
        HighlightManager highlightManager = HighlightManager.getInstance(project);
        highlightManager.addRangeHighlight(editor, offset.start, offset.end, attributes, false, highlighters);
        return highlighters.get(0);
    }
}
