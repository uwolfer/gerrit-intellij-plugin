/*
 * Copyright 2026 Urs Wolfer
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

import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RevertAction extends AbstractLoggedInChangeAction {
    private static final Pattern REVERT_SUBJECT = Pattern.compile("^Revert(?:\\^([0-9]{1,6}))? \"(.*)\"$");

    private final NotificationService notificationService = NotificationService.getInstance();

    public RevertAction() {
        super(AllIcons.Actions.Rollback);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selectedChange = getSelectedChange(e);
        if (selectedChange.isPresent() && !ChangeActionAvailability.canRevert(selectedChange.get())) {
            e.getPresentation().setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (project == null || !selectedChange.isPresent()) {
            return;
        }
        ChangeInfo change = selectedChange.get();

        ChangeMessageDialog dialog = new ChangeMessageDialog(project, GerritBundle.message("revert.title"),
            GerritBundle.message("revert.ok"), defaultMessage(change.subject, change.currentRevision));
        if (!dialog.showAndGet()) {
            return;
        }
        RevertInput revertInput = new RevertInput();
        revertInput.message = dialog.message();

        gerritUtil.postRevert(change.id, revertInput, project, revertingId -> {
            ActionUtil.reloadChanges(project);
            NotificationBuilder notification = new NotificationBuilder(project, GerritBundle.message("revert.done.title"),
                GerritBundle.message("revert.done.text", StringUtil.escapeXmlEntities(subjectOf(change))))
                .action(NotificationAction.createSimple(GerritBundle.message("revert.show"), () ->
                    ActionUtil.showChanges(project, "change:" + decoded(revertingId))));
            notificationService.notifyInformation(notification);
        });
    }

    /**
     * What Gerrit's web UI proposes, which it assembles in the browser: the revert of a revert counts up.
     */
    @VisibleForTesting
    static String defaultMessage(String subject, @Nullable String commit) {
        String title = subjectOf(subject);
        String revertTitle = "Revert \"" + title + "\"";
        Matcher matcher = REVERT_SUBJECT.matcher(title);
        if (matcher.matches()) {
            int number = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) + 1 : 2;
            revertTitle = "Revert^" + number + " \"" + matcher.group(2) + "\"";
        }
        return revertTitle + "\n\n"
            + (commit != null ? "This reverts commit " + commit + ".\n\n" : "")
            + "Reason for revert: <INSERT REASONING HERE>\n";
    }

    /** The id of a change names its project percent-encoded, which a query does not take. */
    static String decoded(String id) {
        try {
            return URLDecoder.decode(id, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return id;
        }
    }

    private static String subjectOf(ChangeInfo change) {
        return subjectOf(change.subject);
    }

    private static String subjectOf(@Nullable String subject) {
        return subject != null ? subject : "";
    }
}
