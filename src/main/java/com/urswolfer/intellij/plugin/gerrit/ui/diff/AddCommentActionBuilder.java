package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.client.Comment;
import com.intellij.openapi.editor.Editor;

import javax.swing.*;

/**
 * @author Thomas Forrer
 */
public class AddCommentActionBuilder {
    private final CommentBalloonBuilder commentBalloonBuilder = new CommentBalloonBuilder();

    Builder create(DiffComments comments, Editor editor) {
        return new Builder().init(comments, editor);
    }

    public class Builder {
        private String text;
        private Icon icon;
        private DiffComments comments;
        private Editor editor;
        private CommentSide side;
        private Comment commentToEdit;
        private Comment replyToComment;

        private Builder init(DiffComments comments, Editor editor) {
            this.comments = comments;
            this.editor = editor;
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

        Builder update(Comment commentToEdit, CommentSide side) {
            this.commentToEdit = commentToEdit;
            this.side = side;
            return this;
        }

        Builder reply(Comment replyToComment, CommentSide side) {
            this.replyToComment = replyToComment;
            this.side = side;
            return this;
        }

        public AddCommentAction get() {
            return new AddCommentAction(text, icon, comments, editor, commentBalloonBuilder, side, commentToEdit,
                replyToComment);
        }
    }
}
