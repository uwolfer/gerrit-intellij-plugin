/*
 * Copyright 2013-2014 Urs Wolfer
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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A reply which resolves the thread, as Gerrit's web UI writes it for Ack and Done.
 *
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class CommentResolveAction extends AnAction implements DumbAware, UpdateInBackground {
    /** In the order Gerrit's web UI offers them. */
    enum Reply {
        ACK("Acknowledged", null),
        DONE("Done", AllIcons.Actions.Checked);

        // as Gerrit's web UI writes it, which other reviewers read: not translated
        final String message;
        @Nullable
        private final Icon icon;

        Reply(String message, @Nullable Icon icon) {
            this.message = message;
            this.icon = icon;
        }

        String text() {
            return this == ACK ? GerritBundle.message("diff.ack") : GerritBundle.message("diff.done");
        }
    }

    /** As Gerrit's web UI has it: while the thread is unresolved, and no draft replies to it yet. */
    static boolean isOffered(CommentThreads.Thread<?> thread) {
        return thread.isUnresolved() && !thread.hasDraft();
    }

    private final DiffComments comments;
    private final Comment fileComment;
    private final CommentSide side;
    private final Reply reply;

    CommentResolveAction(DiffComments comments, Comment fileComment, CommentSide side, Reply reply) {
        super(reply.text(), null, reply.icon);

        this.comments = comments;
        this.fileComment = fileComment;
        this.side = side;
        this.reply = reply;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        final DraftInput comment = createReply(fileComment, reply);
        final Project project = e.getProject();
        GerritUtil.getInstance().saveDraftComment(comments.getChangeInfo()._number, side.revisionId, comment, project,
            (CommentInfo commentInfo) -> comments.saved(commentInfo, side));
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(AddCommentAction.canComment(e.getProject()));
    }

    static DraftInput createReply(Comment fileComment, Reply reply) {
        DraftInput comment = new DraftInput();
        comment.inReplyTo = fileComment.id;
        comment.message = reply.message;
        comment.line = fileComment.line;
        comment.path = fileComment.path;
        comment.side = fileComment.side;
        comment.parent = fileComment.parent;
        comment.range = fileComment.range;
        // left out, Gerrit copies the state of the comment replied to, which keeps the thread unresolved
        comment.unresolved = false;
        return comment;
    }
}
