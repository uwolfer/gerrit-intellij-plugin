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
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.inject.Inject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class SubmitAction extends AbstractLoggedInChangeAction {
    @Inject
    private NotificationService notificationService;

    public SubmitAction() {
        super("Submit", "Submit Change", AllIcons.ToolbarDecorator.Export);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selectedChange = getSelectedChange(e);
        if (selectedChange.isPresent() && isSubmittable(selectedChange.get())) {
            e.getPresentation().setEnabled(false);
        }
    }

    private boolean isSubmittable(ChangeInfo selectedChange) {
        return Boolean.FALSE.equals(selectedChange.submittable);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);

        final Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        SubmitInput submitInput = new SubmitInput();
        gerritUtil.postSubmit(selectedChange.get().id, submitInput, project, new Consumer<Void>() {
            @Override
            public void consume(Void aVoid) {
                NotificationBuilder notification = new NotificationBuilder(
                        project, "Change submitted", getSuccessMessage(selectedChange.get())
                ).hideBalloon();
                notificationService.notifyInformation(notification);
            }
        });
    }

    private String getSuccessMessage(ChangeInfo changeInfo) {
        return String.format("Change '%s' submitted successfully.", changeInfo.subject);
    }

    public static class Proxy extends SubmitAction {
        private final SubmitAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(SubmitAction.class);
        }

        @Override
        public void update(AnActionEvent e) {
            delegate.update(e);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
