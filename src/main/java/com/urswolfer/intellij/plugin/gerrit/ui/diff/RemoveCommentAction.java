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
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class RemoveCommentAction extends AnAction implements DumbAware, UpdateInBackground {

    private final CommentsDiffTool commentsDiffTool;
    private final Editor editor;
    private final GerritUtil gerritUtil;
    private final ChangeInfo changeInfo;
    private final Comment comment;
    private final String revisionId;
    private final RangeHighlighter lineHighlighter;
    private final RangeHighlighter rangeHighlighter;

    public RemoveCommentAction(CommentsDiffTool commentsDiffTool,
                               Editor editor,
                               GerritUtil gerritUtil,
                               ChangeInfo changeInfo,
                               Comment comment,
                               String revisionId,
                               RangeHighlighter lineHighlighter,
                               RangeHighlighter rangeHighlighter) {
        super("Remove", "Remove selected comment", AllIcons.Actions.Cancel);

        this.commentsDiffTool = commentsDiffTool;
        this.comment = comment;
        this.gerritUtil = gerritUtil;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
        this.lineHighlighter = lineHighlighter;
        this.editor = editor;
        this.rangeHighlighter = rangeHighlighter;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        gerritUtil.deleteDraftComment(changeInfo._number, revisionId, comment.id, project,
                new Consumer<Void>() {
                    @Override
                    public void consume(Void aVoid) {
                        commentsDiffTool.removeComment(project, editor, lineHighlighter, rangeHighlighter);
                    }
                });
    }
}
