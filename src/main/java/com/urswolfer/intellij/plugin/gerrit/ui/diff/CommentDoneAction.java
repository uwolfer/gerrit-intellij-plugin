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
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class CommentDoneAction extends AnAction implements DumbAware, UpdateInBackground {
    private final DiffComments comments;
    private final Comment fileComment;
    private final CommentSide side;

    CommentDoneAction(DiffComments comments, Comment fileComment, CommentSide side) {
        super("Done", null, AllIcons.Actions.Checked);

        this.comments = comments;
        this.fileComment = fileComment;
        this.side = side;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        final DraftInput comment = createDoneReply(fileComment);
        final Project project = e.getProject();
        GerritUtil.getInstance().saveDraftComment(comments.getChangeInfo()._number, side.revisionId, comment, project,
            (CommentInfo commentInfo) -> {
                DiffComments current = comments.current(side);
                if (current != null) {
                    current.add(commentInfo, side);
                }
            });
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(e.getProject() != null
            && GerritProjectAccount.getInstance(e.getProject()).isLoginAndPasswordAvailable());
    }

    static DraftInput createDoneReply(Comment fileComment) {
        DraftInput comment = new DraftInput();
        comment.inReplyTo = fileComment.id;
        comment.message = "Done";
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
