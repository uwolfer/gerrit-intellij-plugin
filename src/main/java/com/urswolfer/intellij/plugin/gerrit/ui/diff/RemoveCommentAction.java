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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class RemoveCommentAction extends AnAction implements DumbAware {

    private final CommentsDiffTool commentsDiffTool;
    private final Editor editor;
    private final ReviewCommentSink reviewCommentSink;
    private final SelectedRevisions selectedRevisions;
    private final ChangeInfo changeInfo;
    private final ReviewInput.CommentInput comment;
    private final RangeHighlighter lineHighlighter;
    private final RangeHighlighter rangeHighlighter;

    public RemoveCommentAction(CommentsDiffTool commentsDiffTool,
                               Editor editor,
                               ReviewCommentSink reviewCommentSink,
                               SelectedRevisions selectedRevisions,
                               ChangeInfo changeInfo,
                               ReviewInput.CommentInput comment,
                               RangeHighlighter lineHighlighter,
                               RangeHighlighter rangeHighlighter) {
        super("Remove", "Remove selected comment", AllIcons.Actions.Delete);

        this.commentsDiffTool = commentsDiffTool;
        this.selectedRevisions = selectedRevisions;
        this.comment = comment;
        this.reviewCommentSink = reviewCommentSink;
        this.changeInfo = changeInfo;
        this.lineHighlighter = lineHighlighter;
        this.editor = editor;
        this.rangeHighlighter = rangeHighlighter;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        reviewCommentSink.removeCommentForChange(changeInfo.id, selectedRevisions.get(changeInfo), comment);
        commentsDiffTool.removeComment(editor, lineHighlighter, rangeHighlighter);
    }
}
