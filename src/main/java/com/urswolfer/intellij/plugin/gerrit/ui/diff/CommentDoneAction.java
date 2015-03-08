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
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class CommentDoneAction extends AnAction implements DumbAware {
    private final Editor editor;
    private final CommentsDiffTool commentsDiffTool;
    private final GerritUtil gerritUtil;
    private final GerritSettings gerritSettings;
    private final Comment fileComment;
    private final ChangeInfo changeInfo;
    private final String revisionId;

    public CommentDoneAction(Editor editor,
                             CommentsDiffTool commentsDiffTool,
                             GerritUtil gerritUtil,
                             GerritSettings gerritSettings,
                             Comment fileComment,
                             ChangeInfo changeInfo,
                             String revisionId) {
        super("Done", null, AllIcons.Actions.Checked);

        this.editor = editor;
        this.commentsDiffTool = commentsDiffTool;
        this.gerritUtil = gerritUtil;
        this.gerritSettings = gerritSettings;
        this.fileComment = fileComment;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        final DraftInput comment = new DraftInput();
        comment.inReplyTo = fileComment.id;
        comment.message = "Done";
        comment.line = fileComment.line;
        comment.path = fileComment.path;
        comment.side = fileComment.side;
        comment.range = fileComment.range;

        final Project project = e.getData(PlatformDataKeys.PROJECT);
        gerritUtil.saveDraftComment(changeInfo._number, revisionId, comment, project,
                new Consumer<CommentInfo>() {
                    @Override
                    public void consume(CommentInfo commentInfo) {
                        commentsDiffTool.addComment(editor, changeInfo, revisionId, project, commentInfo);
                    }
                });
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(gerritSettings.isLoginAndPasswordAvailable());
    }
}
