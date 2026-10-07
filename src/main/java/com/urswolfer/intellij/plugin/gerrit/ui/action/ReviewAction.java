/*
 * Copyright 2013-2016 Urs Wolfer
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

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.ui.DraftComment;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindow;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewDialog;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // created per label and rating by ReviewActionGroup
public class ReviewAction extends AbstractLoggedInChangeAction {
    private final SubmitAction submitAction = new SubmitAction();
    private final NotificationService notificationService = NotificationService.getInstance();

    private String label;
    private int rating;
    private boolean showDialog;

    public ReviewAction(String label, int rating, Icon icon, boolean showDialog) {
        this((rating > 0 ? "+" : "") + rating + (showDialog ? "..." : ""),
            "Review Change with " + rating + (showDialog ? " adding Comment" : ""), icon, label, rating, showDialog);
    }

    private ReviewAction(String text, String description, Icon icon, String label, int rating, boolean showDialog) {
        super(text, description, icon);
        this.label = label;
        this.rating = rating;
        this.showDialog = showDialog;
    }

    /**
     * Publishes the drafts with a message but without a vote, as Gerrit's Reply does: also where the user may not vote,
     * e.g. on their own change, and no label is offered.
     */
    static ReviewAction reply() {
        return new ReviewAction(GerritBundle.message("action.Gerrit.Reply.text"),
            GerritBundle.message("action.Gerrit.Reply.description"), AllIcons.Actions.Back, null, 0, true);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();

        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        final ChangeInfo changeDetails = selectedChange.get();
        // the review goes to the patch set shown now, even if a reload resets the selected one while the dialog is open
        final String revision = SelectedRevisions.getInstance(project).get(changeDetails);
        final GerritToolWindow toolWindow = anActionEvent.getData(GerritToolWindow.GERRIT_TOOL_WINDOW);
        final ReviewInput reviewInput = createReviewInput(label, rating);

        boolean submitChange = false;
        if (showDialog) {
            final ReviewDialog dialog = new ReviewDialog(project, changeDetails._number, revision,
                loadDrafts(project, changeDetails, revision));
            dialog.show();
            if (!dialog.isOK()) {
                if (dialog.getReviewPanel().isDraftsChanged()) {
                    ActionUtil.reloadChanges(toolWindow, project);
                }
                return;
            }
            final String message = dialog.getReviewPanel().getMessage();
            if (message != null && !message.isEmpty()) {
                reviewInput.message = message;
            }
            submitChange = dialog.getReviewPanel().getSubmitChange();

            if (!dialog.getReviewPanel().getDoNotify()) {
                reviewInput.notify = NotifyHandling.NONE;
            }
        }

        final boolean finalSubmitChange = submitChange;
        gerritUtil.postReview(changeDetails.id,
                revision,
                reviewInput,
                project,
                new Consumer<Void>() {
                    @Override
                    public void consume(Void result) {
                        NotificationBuilder notification = new NotificationBuilder(
                                project, "Review posted",
                                buildSuccessMessage(changeDetails, reviewInput))
                                .hideBalloon();
                        notificationService.notifyInformation(notification);
                        // also when submitting, which reloads once more but may fail
                        ActionUtil.reloadChanges(toolWindow, project);
                        if (finalSubmitChange) {
                            submitAction.submit(changeDetails, project, toolWindow);
                        }
                    }
                }
        );
    }

    /**
     * Only those of the revision, which are the ones the review publishes. Without them the dialog still opens: the
     * review publishes them anyway, as it did before they were listed.
     */
    private List<DraftComment> loadDrafts(Project project, ChangeInfo changeDetails, String revision) {
        if (revision == null) {
            return Collections.emptyList();
        }
        try {
            return DraftComment.sorted(
                gerritUtil.getDraftCommentsWithModalProgress(changeDetails._number, revision, project));
        } catch (RuntimeException e) {
            notificationService.notifyError(new NotificationBuilder(project,
                GerritBundle.message("review.drafts.loadFailed"),
                gerritUtil.getErrorTextFromException(e.getCause() != null ? e.getCause() : e)));
            return Collections.emptyList();
        }
    }

    /**
     * The drafts of the revision are published by Gerrit rather than sent along as comments: Gerrit checks a comment
     * sent along against the files of the revision, which for a merge commit are those against the auto-merge, and
     * refuses the whole review for a draft on a file which differs from the first parent only.
     */
    static ReviewInput createReviewInput(String label, int rating) {
        ReviewInput reviewInput = new ReviewInput();
        if (label != null) {
            reviewInput.label(label, rating);
        }
        reviewInput.drafts = ReviewInput.DraftHandling.PUBLISH;
        return reviewInput;
    }

    private String buildSuccessMessage(ChangeInfo changeInfo, ReviewInput reviewInput) {
        StringBuilder stringBuilder = new StringBuilder(
                String.format("Review for change '%s' posted", StringUtil.escapeXmlEntities(changeInfo.subject))
        );
        if (reviewInput.labels != null && !reviewInput.labels.isEmpty()) {
            stringBuilder.append(": ");
            stringBuilder.append(reviewInput.labels.entrySet().stream()
                    .map(label -> label.getKey() + ": " + label.getValue())
                    .collect(Collectors.joining(", ")));
        }
        return stringBuilder.toString();
    }

}
