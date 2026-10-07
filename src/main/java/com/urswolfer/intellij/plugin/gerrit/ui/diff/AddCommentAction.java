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
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class AddCommentAction extends AnAction implements DumbAware, UpdateInBackground {

    private final Editor editor;
    private final CommentsDiffTool commentsDiffTool;
    private final GerritUtil gerritUtil;
    private final GerritSettings gerritSettings;
    private final ChangeInfo changeInfo;
    private final String revisionId;
    private final String filePath;
    private final CommentBalloonBuilder commentBalloonBuilder;
    private final Side commentSide;
    private final Integer parent;
    private final Comment commentToEdit;
    private final RangeHighlighter lineHighlighter;
    private final RangeHighlighter rangeHighlighter;
    private final Comment replyToComment;

    public AddCommentAction(String label,
                            Icon icon,
                            CommentsDiffTool commentsDiffTool,
                            GerritUtil gerritUtil,
                            GerritSettings gerritSettings,
                            Editor editor,
                            CommentBalloonBuilder commentBalloonBuilder,
                            ChangeInfo changeInfo,
                            String revisionId,
                            String filePath,
                            Side commentSide,
                            Integer parent,
                            Comment commentToEdit,
                            RangeHighlighter lineHighlighter,
                            RangeHighlighter rangeHighlighter,
                            Comment replyToComment) {
        super(label, null, icon);

        this.commentsDiffTool = commentsDiffTool;
        this.gerritUtil = gerritUtil;
        this.gerritSettings = gerritSettings;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
        this.filePath = filePath;
        this.editor = editor;
        this.commentBalloonBuilder = commentBalloonBuilder;
        this.commentSide = commentSide;
        this.parent = parent;
        this.commentToEdit = commentToEdit;
        this.lineHighlighter = lineHighlighter;
        this.rangeHighlighter = rangeHighlighter;
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
        if (project == null || editor == null || filePath == null) return;

        final CommentForm commentForm = new CommentForm(project, editor, filePath, commentSide, commentToEdit, replyToComment);
        final JBPopup balloon = commentBalloonBuilder.getNewCommentBalloon(commentForm, "Comment");
        balloon.addListener(new JBPopupListener() {
            @Override
            public void beforeShown(LightweightWindowEvent lightweightWindowEvent) {}

            @Override
            public void onClosed(LightweightWindowEvent event) {
                DraftInput comment = commentForm.getComment();
                if (comment != null) {
                    handleComment(comment, project);
                }
            }
        });
        commentForm.setBalloon(balloon);
        balloon.showInBestPositionFor(editor);
    }

    private void handleComment(final DraftInput comment, final Project project) {
        comment.parent = parent;
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

        gerritUtil.saveDraftComment(changeInfo._number, revisionId, comment, project,
                new Consumer<CommentInfo>() {
                    @Override
                    public void consume(CommentInfo commentInfo) {
                        Editor currentEditor = currentEditor();
                        if (currentEditor == null) {
                            return; // the diff was closed, it shows the comment when it is opened again
                        }
                        if (commentToEdit != null) {
                            if (currentEditor == editor) {
                                commentsDiffTool.removeComment(project, editor, lineHighlighter, rangeHighlighter);
                            } else {
                                commentsDiffTool.removeComment(project, currentEditor, commentToEdit.id);
                            }
                        }
                        commentsDiffTool.addComment(currentEditor, changeInfo, revisionId, project, commentInfo);
                    }
                });
    }

    /**
     * The diff window builds new editors when it steps to another file of the change, so the editor the comment was
     * started in is released if the user stepped away and back while the form was open.
     */
    @Nullable
    private Editor currentEditor() {
        if (!editor.isDisposed()) {
            return editor;
        }
        for (Editor candidate : EditorFactory.getInstance().getAllEditors()) {
            AddCommentAction action = candidate.getUserData(CommentsDiffTool.ADD_COMMENT_ACTION);
            if (action != null
                && candidate.getProject() == editor.getProject()
                && action.changeInfo._number == changeInfo._number
                && Objects.equals(action.revisionId, revisionId)
                && Objects.equals(action.filePath, filePath)
                && CommentsDiffTool.sideOf(action.commentSide) == CommentsDiffTool.sideOf(commentSide)) {
                return candidate;
            }
        }
        return null;
    }
}
