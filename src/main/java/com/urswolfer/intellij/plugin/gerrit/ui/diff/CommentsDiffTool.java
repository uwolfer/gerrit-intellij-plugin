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
import com.google.common.collect.Ordering;
import com.google.common.primitives.Longs;
import com.google.gerrit.extensions.common.*;
import com.google.inject.Inject;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.external.FrameDiffTool;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentable;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class CommentsDiffTool extends FrameDiffTool {
    private static final Predicate<Comment> REVISION_COMMENT = new Predicate<Comment>() {
        @Override
        public boolean apply(Comment comment) {
            return comment.side == null || comment.side.equals(Side.REVISION);
        }
    };

    private static final Ordering<Comment> COMMENT_ORDERING = new Ordering<Comment>() {
        @Override
        public int compare(Comment left, Comment right) {
            // need to sort descending as icons are added to the left of existing icons
            return -Longs.compare(left.updated.getTime(), right.updated.getTime());
        }
    };

    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private DataManager dataManager;
    @Inject
    private AddCommentActionBuilder addCommentActionBuilder;
    @Inject
    private PathUtils pathUtils;
    @Inject
    private SelectedRevisions selectedRevisions;

    @Override
    public boolean canShow(DiffRequest request) {
        final boolean superCanShow = super.canShow(request);

        final AsyncResult<DataContext> dataContextFromFocus = dataManager.getDataContextFromFocus();
        final DataContext context = dataContextFromFocus.getResult();
        if (context == null) return false;

        ChangeInfo changeInfo = GerritDataKeys.CHANGE.getData(context);
        return superCanShow && changeInfo != null;
    }

    @Nullable
    @Override
    protected DiffPanelImpl createDiffPanelImpl(@NotNull DiffRequest request, @Nullable Window window, @NotNull Disposable parentDisposable) {
        DataContext context = dataManager.getDataContextFromFocus().getResult();
        ChangeInfo changeInfo = GerritDataKeys.CHANGE.getData(context);
        String selectedRevisionId;
        if (changeInfo != null) {
            selectedRevisionId = selectedRevisions.get(changeInfo);
        } else {
            selectedRevisionId = null;
        }
        Optional<Pair<String, RevisionInfo>> baseRevision = GerritDataKeys.BASE_REVISION.getData(context);

        DiffPanelImpl diffPanel = new CommentableDiffPanel(window, request, changeInfo, selectedRevisionId, baseRevision);
        diffPanel.setDiffRequest(request);
        Disposer.register(parentDisposable, diffPanel);
        return diffPanel;
    }

    private void handleComments(final DiffPanelImpl diffPanel,
                                final String filePathString,
                                final Project project,
                                final ChangeInfo changeInfo,
                                final String selectedRevisionId,
                                final Optional<Pair<String, RevisionInfo>> baseRevision) {
        final FilePath filePath = new FilePathImpl(new File(filePathString), false);
        final String relativeFilePath = PathUtils.ensureSlashSeparators(getRelativeOrAbsolutePath(project, filePath.getPath(), changeInfo));

        addCommentAction(diffPanel, relativeFilePath, changeInfo, selectedRevisionId, baseRevision);

        gerritUtil.getChangeDetails(changeInfo._number, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeDetails) {
                gerritUtil.getComments(changeDetails.id, selectedRevisionId, project, true, true,
                        new Consumer<Map<String, List<CommentInfo>>>() {
                            @Override
                            public void consume(Map<String, List<CommentInfo>> comments) {
                                List<CommentInfo> fileComments = comments.get(relativeFilePath);
                                if (fileComments != null) {
                                    addCommentsGutter(
                                            diffPanel.getEditor2(),
                                            relativeFilePath,
                                            selectedRevisionId,
                                            Iterables.filter(fileComments, REVISION_COMMENT),
                                            changeInfo,
                                            project
                                    );
                                    if (!baseRevision.isPresent()) {
                                        addCommentsGutter(
                                                diffPanel.getEditor1(),
                                                relativeFilePath,
                                                selectedRevisionId,
                                                Iterables.filter(fileComments, Predicates.not(REVISION_COMMENT)),
                                                changeInfo,
                                                project
                                        );
                                    }
                                }
                            }
                        }
                );

                if (baseRevision.isPresent()) {
                    gerritUtil.getComments(changeDetails.id, baseRevision.get().getFirst(), project, true, true,
                            new Consumer<Map<String, List<CommentInfo>>>() {
                        @Override
                        public void consume(Map<String, List<CommentInfo>> comments) {
                            List<CommentInfo> fileComments = comments.get(relativeFilePath);
                            if (fileComments != null) {
                                Collections.sort(fileComments, COMMENT_ORDERING);
                                addCommentsGutter(
                                        diffPanel.getEditor1(),
                                        relativeFilePath,
                                        baseRevision.get().getFirst(),
                                        Iterables.filter(fileComments, REVISION_COMMENT),
                                        changeInfo,
                                        project
                                );
                            }
                        }
                    });
                }

                gerritUtil.setReviewed(changeDetails.id, selectedRevisionId,
                        relativeFilePath, project);
            }
        });
    }

    private void addCommentAction(DiffPanelImpl diffPanel, String filePath, ChangeInfo changeInfo,
                                  String selectedRevisionId, Optional<Pair<String, RevisionInfo>> baseRevision) {
        if (baseRevision.isPresent()) {
            addCommentActionToEditor(diffPanel.getEditor1(), filePath, changeInfo, baseRevision.get().getFirst(), Side.REVISION);
        } else {
            addCommentActionToEditor(diffPanel.getEditor1(), filePath, changeInfo, selectedRevisionId, Side.PARENT);
        }
        addCommentActionToEditor(diffPanel.getEditor2(), filePath, changeInfo, selectedRevisionId, Side.REVISION);
    }

    private void addCommentActionToEditor(Editor editor,
                                          String filePath,
                                          ChangeInfo changeInfo,
                                          String revisionId,
                                          Side commentSide) {
        if (editor == null) return;

        DefaultActionGroup group = new DefaultActionGroup();
        final AddCommentAction addCommentAction = addCommentActionBuilder
                .create(this, changeInfo, revisionId, editor, filePath, commentSide)
                .withText("Add Comment")
                .withIcon(AllIcons.Toolwindows.ToolWindowMessages)
                .get();
        addCommentAction.registerCustomShortcutSet(CustomShortcutSet.fromString("C"), editor.getContentComponent());
        group.add(addCommentAction);
        PopupHandler.installUnknownPopupHandler(editor.getContentComponent(), group, ActionManager.getInstance());
    }

    private void addCommentsGutter(Editor editor,
                                   String filePath,
                                   String revisionId,
                                   Iterable<CommentInfo> fileComments,
                                   ChangeInfo changeInfo,
                                   Project project) {
        for (CommentInfo fileComment : fileComments) {
            fileComment.path = PathUtils.ensureSlashSeparators(filePath);
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
                    this, editor, gerritUtil, selectedRevisions, addCommentActionBuilder,
                    comment, changeInfo, revisionId, highlighter, rangeHighlighter);
            highlighter.setGutterIconRenderer(iconRenderer);
        }
    }

    public void removeComment(Project project, Editor editor, RangeHighlighter lineHighlighter, RangeHighlighter rangeHighlighter) {
        editor.getMarkupModel().removeHighlighter(lineHighlighter);
        lineHighlighter.dispose();

        if (rangeHighlighter != null) {
            HighlightManager highlightManager = HighlightManager.getInstance(project);
            highlightManager.removeSegmentHighlighter(editor, rangeHighlighter);
        }
    }

    private class CommentableDiffPanel extends DiffPanelImpl {
        private final ChangeInfo changeInfo;
        private final String selectedRevisionId;
        private final Optional<Pair<String, RevisionInfo>> baseRevision;

        public CommentableDiffPanel(Window window,
                                    DiffRequest request,
                                    ChangeInfo changeInfo,
                                    String selectedRevisionId,
                                    Optional<Pair<String, RevisionInfo>> baseRevision) {
            super(window, request.getProject(), true, true, DiffManagerImpl.FULL_DIFF_DIVIDER_POLYGONS_OFFSET, CommentsDiffTool.this);
            this.changeInfo = changeInfo;
            this.selectedRevisionId = selectedRevisionId;
            this.baseRevision = baseRevision;
        }

        @Override
        public void setDiffRequest(DiffRequest request) {
            super.setDiffRequest(request);

            Object chain = request.getGenericData().get(VcsDataKeys.DIFF_REQUEST_CHAIN.getName());
            if (chain instanceof ChangeRequestChain) {
                DiffRequestPresentable currentRequest = ((ChangeRequestChain) chain).getCurrentRequest();
                if (currentRequest != null) {
                    String path = currentRequest.getPathPresentation();
                    handleComments(this, path, request.getProject(), changeInfo, selectedRevisionId, baseRevision);
                }
            }
        }
    }

    private String getRelativeOrAbsolutePath(Project project, String absoluteFilePath, ChangeInfo changeInfo) {
        return pathUtils.getRelativeOrAbsolutePath(project, absoluteFilePath, changeInfo.project);
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
