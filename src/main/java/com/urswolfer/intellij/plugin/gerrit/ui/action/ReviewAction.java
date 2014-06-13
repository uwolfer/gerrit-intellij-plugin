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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewDialog;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.intellij.icons.AllIcons.Actions.*;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // needs to be setup with correct parameters (ctor); see corresponding factory
public class ReviewAction extends AbstractChangeAction {
    public static final String CODE_REVIEW = "Code-Review";
    public static final String VERIFIED = "Verified";

    private ReviewCommentSink reviewCommentSink;
    private SubmitAction submitAction;

    private String label;
    private int rating;
    private boolean showDialog;

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
                reviewInput.label(label, rating);

                Iterable<ReviewInput.CommentInput> commentInputs = reviewCommentSink
                        .getCommentsForChange(changeDetails.id, changeDetails.currentRevision);
                for (ReviewInput.CommentInput commentInput : commentInputs) {
                    addComment(reviewInput, commentInput.path, commentInput);
                }

                boolean submitChange = false;
                if (showDialog) {
                    final ReviewDialog dialog = new ReviewDialog(project);
                    dialog.show();
                    if (!dialog.isOK()) {
                        return;
                    }
                    final String message = dialog.getReviewPanel().getMessage();
                    if (!Strings.isNullOrEmpty(message)) {
                        reviewInput.message = message;
                    }
                    submitChange = dialog.getReviewPanel().getSubmitChange();

                    if (!dialog.getReviewPanel().getDoNotify()) {
                        reviewInput.notify = ReviewInput.NotifyHandling.NONE;
                    }
                }

                final boolean finalSubmitChange = submitChange;
                gerritUtil.postReview(changeDetails.id,
                        changeDetails.currentRevision,
                        reviewInput,
                        project,
                        new Consumer<Void>() {
                            @Override
                            public void consume(Void result) {
                                reviewCommentSink.removeCommentsForChange(changeDetails.id, changeDetails.currentRevision);
                                if (finalSubmitChange) {
                                    submitAction.actionPerformed(anActionEvent);
                                }
                            }
                        }
                );
            }
        });
    }

    private void addComment(ReviewInput reviewInput, String path, ReviewInput.CommentInput comment) {
        List<ReviewInput.CommentInput> commentInputs;
        Map<String, List<ReviewInput.CommentInput>> comments = reviewInput.comments;
        if (comments == null) {
            comments = Maps.newHashMap();
            reviewInput.comments = comments;
        }
        if (comments.containsKey(path)) {
            commentInputs = comments.get(path);
        } else {
            commentInputs = Lists.newArrayList();
            comments.put(path, commentInputs);
        }
        commentInputs.add(comment);
    }

    public abstract static class Proxy extends AnAction implements DumbAware {
        private final ReviewActionFactory reviewActionFactory;
        private final ReviewAction delegate;

        public Proxy(String label, int rating, Icon icon, boolean showDialog) {
            super((rating > 0 ? "+" : "") + rating + (showDialog ? "..." : ""), "Review Change with " + rating, icon);

            reviewActionFactory = GerritModule.getInstance(ReviewActionFactory.class);
            delegate = reviewActionFactory.get(label, rating, icon, showDialog);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }

    public static class ReviewPlusTwoProxy extends Proxy {
        public ReviewPlusTwoProxy() {
            super(CODE_REVIEW, 2, Checked, false);
        }
    }

    public static class ReviewPlusTwoDialogProxy extends Proxy {
        public ReviewPlusTwoDialogProxy() {
            super(CODE_REVIEW, 2, Checked, true);
        }
    }

    public static class ReviewPlusOneProxy extends Proxy {
        public ReviewPlusOneProxy() {
            super(CODE_REVIEW, 1, MoveUp, false);
        }
    }

    public static class ReviewPlusOneDialogProxy extends Proxy {
        public ReviewPlusOneDialogProxy() {
            super(CODE_REVIEW, 1, MoveUp, true);
        }
    }

    public static class ReviewNeutralProxy extends Proxy {
        public ReviewNeutralProxy() {
            super(CODE_REVIEW, 0, Forward, false);
        }
    }

    public static class ReviewNeutralDialogProxy extends Proxy {
        public ReviewNeutralDialogProxy() {
            super(CODE_REVIEW, 0, Forward, true);
        }
    }

    public static class ReviewMinusOneProxy extends Proxy {
        public ReviewMinusOneProxy() {
            super(CODE_REVIEW, -1, MoveDown, false);
        }
    }

    public static class ReviewMinusOneDialogProxy extends Proxy {
        public ReviewMinusOneDialogProxy() {
            super(CODE_REVIEW, -1, MoveDown, true);
        }
    }

    public static class ReviewMinusTwoProxy extends Proxy {
        public ReviewMinusTwoProxy() {
            super(CODE_REVIEW, -2, Cancel, false);
        }
    }

    public static class ReviewMinusTwoDialogProxy extends Proxy {
        public ReviewMinusTwoDialogProxy() {
            super(CODE_REVIEW, -2, Cancel, true);
        }
    }


    public static class VerifyPlusOneProxy extends Proxy {
        public VerifyPlusOneProxy() {
            super(VERIFIED, 1, Checked, false);
        }
    }

    public static class VerifyPlusOneDialogProxy extends Proxy {
        public VerifyPlusOneDialogProxy() {
            super(VERIFIED, 1, Checked, true);
        }
    }

    public static class VerifyNeutralProxy extends Proxy {
        public VerifyNeutralProxy() {
            super(VERIFIED, 0, Forward, false);
        }
    }

    public static class VerifyNeutralDialogProxy extends Proxy {
        public VerifyNeutralDialogProxy() {
            super(VERIFIED, 0, Forward, true);
        }
    }

    public static class VerifyMinusOneProxy extends Proxy {
        public VerifyMinusOneProxy() {
            super(VERIFIED, -1, Cancel, false);
        }
    }

    public static class VerifyMinusOneDialogProxy extends Proxy {
        public VerifyMinusOneDialogProxy() {
            super(VERIFIED, -1, Cancel, true);
        }
    }

}
