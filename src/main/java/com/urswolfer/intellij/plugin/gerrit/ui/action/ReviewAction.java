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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ReviewInput;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewDialog;

import javax.swing.*;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // needs to be setup with correct parameters (ctor)
public class ReviewAction extends AbstractChangeAction {
    public static final String CODE_REVIEW = "Code-Review";
    public static final String VERIFIED = "Verified";

    private final String label;
    private final int rating;
    private final boolean showDialog;
    private final ReviewCommentSink reviewCommentSink;
    private final SubmitAction submitAction;

    public ReviewAction(String label, int rating, Icon icon, boolean showDialog,
                        ReviewCommentSink reviewCommentSink,
                        GerritUtil gerritUtil,
                        SubmitAction submitAction) {
        super((rating > 0 ? "+" : "") + rating + (showDialog ? "..." : ""), "Review Change with " + rating, icon);
        this.label = label;
        this.rating = rating;
        this.showDialog = showDialog;
        this.submitAction = submitAction;
        this.gerritUtil = gerritUtil;
        this.reviewCommentSink = reviewCommentSink;
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);

        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        getChangeDetail(selectedChange.get(), project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(final ChangeInfo changeDetails) {
                final ReviewInput reviewInput = new ReviewInput();
                reviewInput.addLabel(label, rating);

                Iterable<CommentInput> commentInputs = reviewCommentSink.getCommentsForChange(changeDetails.getId());
                for (CommentInput commentInput : commentInputs) {
                    reviewInput.addComment(commentInput.getPath(), commentInput);
                }

                boolean submitChange = false;
                if (showDialog) {
                    final ReviewDialog dialog = new ReviewDialog();
                    dialog.show();
                    if (!dialog.isOK()) {
                        return;
                    }
                    final String message = dialog.getReviewPanel().getMessage();
                    if (!Strings.isNullOrEmpty(message)) {
                        reviewInput.setMessage(message);
                    }
                    submitChange = dialog.getReviewPanel().getSubmitChange();
                }

                final boolean finalSubmitChange = submitChange;
                gerritUtil.postReview(changeDetails.getId(),
                        changeDetails.getCurrentRevision(),
                        reviewInput,
                        project,
                        new Consumer<Void>() {
                            @Override
                            public void consume(Void result) {
                                reviewCommentSink.removeCommentsForChange(changeDetails.getId());
                                if (finalSubmitChange) {
                                    submitAction.actionPerformed(anActionEvent);
                                }
                            }
                        }
                );
            }
        });
    }
}
