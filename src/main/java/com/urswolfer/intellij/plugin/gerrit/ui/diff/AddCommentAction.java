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

import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.Comment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.ui.AnActionButton;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class AddCommentAction extends AnActionButton implements DumbAware {

    private final Editor editor;
    private final ReviewCommentSink reviewCommentSink;
    private final ChangeInfo changeInfo;
    @Nullable
    private final FilePath filePath;
    private final GerritGitUtil gerritGitUtil;
    private final CommentBalloonBuilder commentBalloonBuilder;
    private final Comment.Side commentSide;

    public AddCommentAction(ReviewCommentSink reviewCommentSink,
                            ChangeInfo changeInfo,
                            @Nullable final Editor editor,
                            @Nullable FilePath filePath,
                            GerritGitUtil gerritGitUtil,
                            CommentBalloonBuilder commentBalloonBuilder,
                            Comment.Side commentSide) {
        super("Add Comment", "Add a comment at current line", AllIcons.Toolwindows.ToolWindowMessages);
        this.reviewCommentSink = reviewCommentSink;
        this.changeInfo = changeInfo;
        this.filePath = filePath;
        this.editor = editor;
        this.gerritGitUtil = gerritGitUtil;
        this.commentBalloonBuilder = commentBalloonBuilder;
        this.commentSide = commentSide;
    }

    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) return;
        addVersionedComment(project);
    }

    private void addVersionedComment(@NotNull final Project project) {
        if (editor == null || filePath == null) return;

        final CommentForm commentForm = new CommentForm(
                project, filePath, reviewCommentSink, changeInfo, gerritGitUtil, commentSide);
        commentForm.setEditor(editor);
        final JBPopup balloon = commentBalloonBuilder.getNewCommentBalloon(commentForm, "Comment");
        balloon.addListener(new JBPopupAdapter() {
            @Override
            public void onClosed(LightweightWindowEvent event) {
                ReviewInput.CommentInput comment = commentForm.getComment();
                if (comment != null) {
                    RangeHighlighter rangeHighlighter = null;
                    if (comment.range != null) {
                        rangeHighlighter = CommentsDiffTool.highlightRangeComment(comment.range, editor, project);
                    }
                    MarkupModel markup = editor.getMarkupModel();
                    RangeHighlighter highlighter = markup.addLineHighlighter(comment.line - 1, HighlighterLayer.ERROR + 1, null);
                    CommentGutterIconRenderer iconRenderer = new CommentGutterIconRenderer(
                            comment, reviewCommentSink, changeInfo, highlighter, editor, rangeHighlighter);
                    highlighter.setGutterIconRenderer(iconRenderer);
                }
            }
        });
        commentForm.setBalloon(balloon);
        balloon.showInBestPositionFor(editor);
        commentForm.requestFocus();
    }
}
