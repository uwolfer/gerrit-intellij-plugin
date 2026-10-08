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

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class AddCommentAction extends AnAction implements DumbAware, UpdateInBackground {

    private final Editor editor;
    private final DiffComments comments;
    private final CommentBalloonBuilder commentBalloonBuilder;
    @Nullable
    private final CommentSide side;
    private final Comment commentToEdit;
    private final Comment replyToComment;

    /**
     * @param side of the comment edited or replied to; null for a new comment, which goes on the side the caret or
     *             the selection is on
     */
    AddCommentAction(String label,
                     Icon icon,
                     DiffComments comments,
                     Editor editor,
                     CommentBalloonBuilder commentBalloonBuilder,
                     @Nullable CommentSide side,
                     Comment commentToEdit,
                     Comment replyToComment) {
        super(label, null, icon);

        this.comments = comments;
        this.editor = editor;
        this.commentBalloonBuilder = commentBalloonBuilder;
        this.side = side;
        this.commentToEdit = commentToEdit;
        this.replyToComment = replyToComment;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        addVersionedComment(e.getProject());
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(canComment(e.getProject()));
    }

    boolean canComment(@Nullable Project project) {
        return project != null && GerritProjectAccount.getInstance(project).isLoginAndPasswordAvailable();
    }

    void addVersionedComment(@Nullable Project project) {
        if (project == null || editor == null) return;

        LineMapping mapping = comments.getMapping(editor);
        CommentPosition position = CommentPosition.read(editor, mapping);
        if (side == null && position == null) {
            // without a selection, the caret is on no line of a side, such as on the empty line a unified diff ends
            // with, where a click below the text puts it
            HintManager.getInstance().showErrorHint(editor, editor.getSelectionModel().hasSelection()
                ? "A comment is on one side of the diff: start and end the selection on lines of the same side"
                : "There is no line of the diff here to comment on");
            return;
        }

        final CommentForm commentForm =
            new CommentForm(project, editor, mapping, position, commentToEdit, replyToComment);
        final JBPopup balloon = commentBalloonBuilder.getNewCommentBalloon(commentForm, "Comment");
        balloon.addListener(new JBPopupListener() {
            @Override
            public void beforeShown(LightweightWindowEvent lightweightWindowEvent) {}

            @Override
            public void onClosed(LightweightWindowEvent event) {
                DraftInput comment = commentForm.getComment();
                if (comment != null) {
                    handleComment(comment, side != null ? side : comments.getSide(commentForm.getPosition().side),
                        project);
                }
            }
        });
        commentForm.setBalloon(balloon);
        balloon.showInBestPositionFor(editor);
    }

    private void handleComment(final DraftInput comment, final CommentSide commentSide, final Project project) {
        comment.path = commentSide.filePath;
        comment.side = commentSide.side;
        comment.parent = commentSide.parent;
        if (commentToEdit != null) {
            comment.id = commentToEdit.id;
            comment.parent = commentToEdit.parent;
        }

        if (replyToComment != null) {
            comment.inReplyTo = replyToComment.id;
            comment.side = replyToComment.side;
            comment.parent = replyToComment.parent;
            comment.line = replyToComment.line;
            comment.range = replyToComment.range;
        }

        GerritUtil.getInstance().saveDraftComment(comments.getChangeInfo()._number, commentSide.revisionId, comment,
            project, (CommentInfo commentInfo) -> {
                DiffComments current = comments.current(commentSide);
                if (current == null) {
                    return; // the diff was closed, it shows the comment when it is opened again
                }
                current.add(commentInfo, commentSide);
            });
    }
}
