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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.AnActionButton;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;

/**
 * @author Urs Wolfer
 */
public class RemoveCommentAction extends AnActionButton implements DumbAware {

    private final CommentInfo comment;
    private final ReviewCommentSink reviewCommentSink;
    private final ChangeInfo changeInfo;
    private final RangeHighlighter highlighter;
    private final MarkupModel markup;

    public RemoveCommentAction(CommentInfo comment, ReviewCommentSink reviewCommentSink, ChangeInfo changeInfo, RangeHighlighter highlighter, MarkupModel markup) {
        super("Remove Comment", "Remove selected comment", AllIcons.Actions.Delete);
        this.comment = comment;
        this.reviewCommentSink = reviewCommentSink;
        this.changeInfo = changeInfo;
        this.highlighter = highlighter;
        this.markup = markup;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Iterable<CommentInput> commentInputs = reviewCommentSink.getCommentsForChange(changeInfo.getId());
        CommentInput toRemove = null;
        for (CommentInput commentInput : commentInputs) {
            //noinspection EqualsBetweenInconvertibleTypes
            if (commentInput.equals(comment)) { // implemented in base class
                toRemove = commentInput;
                break;
            }
        }
        if (toRemove != null) {
            reviewCommentSink.removeCommentForChange(changeInfo.getId(), toRemove);
            markup.removeHighlighter(highlighter);
            highlighter.dispose();
        }
    }
}
