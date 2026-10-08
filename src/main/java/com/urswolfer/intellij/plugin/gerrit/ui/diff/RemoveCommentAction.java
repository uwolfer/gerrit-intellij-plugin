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

import com.google.gerrit.extensions.client.Comment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class RemoveCommentAction extends AnAction implements DumbAware, UpdateInBackground {

    private final DiffComments comments;
    private final Comment comment;
    private final CommentSide side;

    RemoveCommentAction(DiffComments comments, Comment comment, CommentSide side) {
        super("Remove", "Remove selected comment", AllIcons.Actions.Cancel);

        this.comments = comments;
        this.comment = comment;
        this.side = side;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        GerritUtil.getInstance().deleteDraftComment(comments.getChangeInfo()._number, side.revisionId, comment.id,
            e.getProject(), (Void aVoid) -> comments.removed(comment.id, side));
    }
}
