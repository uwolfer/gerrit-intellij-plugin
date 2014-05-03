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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.AnActionButton;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.util.CommentHelper;

/**
 * @author Urs Wolfer
 */
public class RemoveCommentAction extends AnActionButton implements DumbAware {

    private final ReviewInput.CommentInput comment;
    private final ReviewCommentSink reviewCommentSink;
    private final ChangeInfo changeInfo;
    private final RangeHighlighter highlighter;
    private final MarkupModel markup;

    public RemoveCommentAction(ReviewInput.CommentInput comment, ReviewCommentSink reviewCommentSink, ChangeInfo changeInfo, RangeHighlighter highlighter, MarkupModel markup) {
        super("Remove Comment", "Remove selected comment", AllIcons.Actions.Delete);
        this.comment = comment;
        this.reviewCommentSink = reviewCommentSink;
        this.changeInfo = changeInfo;
        this.highlighter = highlighter;
        this.markup = markup;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Iterable<ReviewInput.CommentInput> commentInputs = reviewCommentSink.getCommentsForChange(changeInfo.id);
        ReviewInput.CommentInput toRemove = null;
        for (ReviewInput.CommentInput commentInput : commentInputs) {
            if (CommentHelper.equals(commentInput, comment)) {
                toRemove = commentInput;
                break;
            }
        }
        if (toRemove != null) {
            reviewCommentSink.removeCommentForChange(changeInfo.id, toRemove);
            markup.removeHighlighter(highlighter);
            highlighter.dispose();
        }
    }
}
