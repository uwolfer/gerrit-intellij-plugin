package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.inject.Inject;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;

import javax.swing.*;

/**
 * @author Thomas Forrer
 */
public class AddCommentActionBuilder {
    @Inject
    private CommentBalloonBuilder commentBalloonBuilder;
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private GerritSettings gerritSettings;

    public Builder create(CommentsDiffTool commentsDiffTool,
                          ChangeInfo changeInfo,
                          String revisionId,
                          Editor editor,
                          String filePath,
                          Side commentSide) {
        return new Builder().init(commentsDiffTool, changeInfo, revisionId, editor, filePath, commentSide);
    }

    public class Builder {
        private String text;
        private Icon icon;
        private CommentsDiffTool commentsDiffTool;
        private ChangeInfo changeInfo;
        private String revisionId;
        private Editor editor;
        private String filePath;
        private Side commentSide;
        private Comment commentToEdit;
        private RangeHighlighter lineHighlighter;
        private RangeHighlighter rangeHighlighter;
        private Comment replyToComment;

        private Builder init(CommentsDiffTool commentsDiffTool,
                             ChangeInfo changeInfo,
                             String revisionId,
                             Editor editor,
                             String filePath,
                             Side commentSide) {
            this.commentsDiffTool = commentsDiffTool;
            this.changeInfo = changeInfo;
            this.revisionId = revisionId;
            this.editor = editor;
            this.filePath = filePath;
            this.commentSide = commentSide;
            return this;
        }

        public Builder withText(String text) {
            this.text = text;
            return this;
        }

        public Builder withIcon(Icon icon) {
            this.icon = icon;
            return this;
        }

        public Builder update(Comment commentToEdit,
                              RangeHighlighter lineHighlighter,
                              RangeHighlighter rangeHighlighter) {
            this.commentToEdit = commentToEdit;
            this.lineHighlighter = lineHighlighter;
            this.rangeHighlighter = rangeHighlighter;
            return this;
        }

        public Builder reply(Comment replyToComment) {
            this.replyToComment = replyToComment;
            return this;
        }

        public AddCommentAction get() {
            return new AddCommentAction(text, icon, commentsDiffTool, gerritUtil, gerritSettings, editor, commentBalloonBuilder,
                    changeInfo, revisionId, filePath, commentSide, commentToEdit, lineHighlighter, rangeHighlighter, replyToComment);
        }
    }
}
