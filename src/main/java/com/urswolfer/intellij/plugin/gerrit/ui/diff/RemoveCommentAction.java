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
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.AnActionButton;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewCommentSink;

import java.util.List;

/**
 * @author Urs Wolfer
 */
public class RemoveCommentAction extends AnActionButton implements DumbAware {

    private final CommentInfo myComment;
    private final ReviewCommentSink myReviewCommentSink;
    private final ChangeInfo myChangeInfo;

    public RemoveCommentAction(CommentInfo comment, ReviewCommentSink reviewCommentSink, ChangeInfo changeInfo) {
        super("Remove Comment", "Remove selected comment", AllIcons.Actions.Delete);
        myComment = comment;
        myReviewCommentSink = reviewCommentSink;
        myChangeInfo = changeInfo;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        List<CommentInput> commentInputs = myReviewCommentSink.getCommentsForChange(myChangeInfo.getId());
        for (int i = 0; i < commentInputs.size(); i++) {
            CommentInput commentInput = commentInputs.get(i);
            if (commentInput.equals(myComment)) {
                int index = commentInputs.indexOf(commentInput);
                commentInputs.remove(index);
            }
        }
    }
}
