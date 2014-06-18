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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;

import javax.swing.*;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class AddCommentAction extends AnAction implements DumbAware {

    private final Editor editor;
    private final CommentsDiffTool commentsDiffTool;
    private final ReviewCommentSink reviewCommentSink;
    private final SelectedRevisions selectedRevisions;
    private final ChangeInfo changeInfo;
    private final String revisionId;
    private final String filePath;
    private final CommentBalloonBuilder commentBalloonBuilder;
    private final Comment.Side commentSide;
    private final ReviewInput.CommentInput commentToEdit;
    private final RangeHighlighter lineHighlighter;
    private final RangeHighlighter rangeHighlighter;
    private final Comment replyToComment;

    public AddCommentAction(String label,
                            Icon icon,
                            CommentsDiffTool commentsDiffTool,
                            ReviewCommentSink reviewCommentSink,
                            Editor editor,
                            SelectedRevisions selectedRevisions,
                            CommentBalloonBuilder commentBalloonBuilder,
                            ChangeInfo changeInfo,
                            String revisionId,
                            String filePath,
                            Comment.Side commentSide,
                            ReviewInput.CommentInput commentToEdit,
                            RangeHighlighter lineHighlighter,
                            RangeHighlighter rangeHighlighter,
                            Comment replyToComment) {
        super(label, null, icon);

        this.commentsDiffTool = commentsDiffTool;
        this.reviewCommentSink = reviewCommentSink;
        this.selectedRevisions = selectedRevisions;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
        this.filePath = filePath;
        this.editor = editor;
        this.commentBalloonBuilder = commentBalloonBuilder;
        this.commentSide = commentSide;
        this.commentToEdit = commentToEdit;
        this.lineHighlighter = lineHighlighter;
        this.rangeHighlighter = rangeHighlighter;
        this.replyToComment = replyToComment;
    }

    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) return;
        addVersionedComment(project);
    }

    private void addVersionedComment(final Project project) {
        if (editor == null || filePath == null) return;

        final CommentForm commentForm = new CommentForm(project, editor, filePath, commentSide, commentToEdit);
        final JBPopup balloon = commentBalloonBuilder.getNewCommentBalloon(commentForm, "Comment");
        balloon.addListener(new JBPopupAdapter() {
            @Override
            public void onClosed(LightweightWindowEvent event) {
                ReviewInput.CommentInput comment = commentForm.getComment();
                if (comment != null) {
                    handleComment(comment, project);
                }
            }
        });
        commentForm.setBalloon(balloon);
        balloon.showInBestPositionFor(editor);
        commentForm.requestFocus();
    }

    private void handleComment(ReviewInput.CommentInput comment, Project project) {
        if (commentToEdit != null) {
            reviewCommentSink.removeCommentForChange(changeInfo.id, selectedRevisions.get(changeInfo), commentToEdit);
            commentsDiffTool.removeComment(editor, lineHighlighter, rangeHighlighter);
        }

        if (replyToComment != null) {
            comment.inReplyTo = replyToComment.id;
        }

        reviewCommentSink.addComment(changeInfo.id, revisionId, comment);
        commentsDiffTool.addComment(editor, changeInfo, revisionId, project, comment);
    }
}
