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

import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

import java.util.Optional;

/**
 * @author Urs Wolfer
 */
public class SubmitAction extends AbstractLoggedInChangeAction {
    private final NotificationService notificationService = NotificationService.getInstance();

    public SubmitAction() {
        super(AllIcons.ToolbarDecorator.Export);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selectedChange = getSelectedChange(e);
        if (selectedChange.isPresent() && !canSubmit(selectedChange.get())) {
            e.getPresentation().setEnabled(false);
        }
    }

    private boolean canSubmit(ChangeInfo selectedChange) {
        // null if the Gerrit instance does not report it (it is only set when the query asks for SUBMITTABLE, and
        // older instances do not know that option at all); assume the change can be submitted in that case
        return !Boolean.FALSE.equals(selectedChange.submittable);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        submit(selectedChange.get(), anActionEvent.getProject());
    }

    /** Entry point for other actions; {@code actionPerformed} is override-only and must not be invoked. */
    public void submit(final ChangeInfo change, final Project project) {
        SubmitInput submitInput = new SubmitInput();
        gerritUtil.postSubmit(change.id, submitInput, project, new Consumer<Void>() {
            @Override
            public void consume(Void aVoid) {
                NotificationBuilder notification = new NotificationBuilder(
                        project, GerritBundle.message("submit.title"), getSuccessMessage(change)
                ).hideBalloon();
                notificationService.notifyInformation(notification);
                ActionUtil.reloadChanges(project);
            }
        });
    }

    private String getSuccessMessage(ChangeInfo changeInfo) {
        return GerritBundle.message("submit.success", StringUtil.escapeXmlEntities(changeInfo.subject));
    }

}
