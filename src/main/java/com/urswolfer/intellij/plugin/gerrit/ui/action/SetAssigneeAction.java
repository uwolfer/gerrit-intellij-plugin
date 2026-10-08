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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritChangesListener;
import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;

public class SetAssigneeAction extends AbstractLoggedInChangeAction {
    public SetAssigneeAction() {
        super(AllIcons.General.User);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        if (project == null) {
            return;
        }
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        ChangeInfo changeInfo = selectedChange.get();

        SetAssigneeDialog dialog = new SetAssigneeDialog(project, GerritApiProvider.getInstance().get(GerritProjectAccount.getInstance(project).get()), changeInfo);
        if (!dialog.showAndGet()) {
            return;
        }
        // not String#trim(): a pasted name can be surrounded by whitespace which it does not remove
        String assignee = Whitespace.trim(dialog.assigneeField.getText());
        if (assignee.equals(currentAssignee(changeInfo))) {
            return;
        }
        // the list row is what the dialog prefills from next time
        gerritUtil.setAssignee(changeInfo.id, assignee, project, newAssignee ->
            project.getMessageBus().syncPublisher(GerritChangesListener.TOPIC)
                .changeModified(changeInfo.id, change -> change.assignee = newAssignee));
    }

    static String currentAssignee(ChangeInfo changeInfo) {
        return changeInfo.assignee != null ? AccountLookup.identifier(changeInfo.assignee) : "";
    }

    private static class SetAssigneeDialog extends DialogWrapper {
        private final TextFieldWithCompletion assigneeField;

        SetAssigneeDialog(Project project, GerritApi gerritApi, ChangeInfo changeInfo) {
            super(project, true);
            setTitle("Set Assignee of Change");
            setOKButtonText("Set Assignee");

            assigneeField = new TextFieldWithCompletion(project, createCompletionProvider(gerritApi, changeInfo),
                currentAssignee(changeInfo), true, true, true);
            assigneeField.setPreferredWidth(400);

            init();
        }

        private static TextFieldCompletionProviderDumbAware createCompletionProvider(final GerritApi gerritApi,
                                                                                    final ChangeInfo changeInfo) {
            AccountCompletion completion = new AccountCompletion();
            return new TextFieldCompletionProviderDumbAware(true) {
                @NotNull
                @Override
                protected String getPrefix(@NotNull String currentTextPrefix) {
                    return AccountCompletion.prefix(currentTextPrefix, "");
                }

                @Nullable
                @Override
                public CharFilter.Result acceptChar(char c) {
                    return AccountCompletion.acceptChar(c, "");
                }

                @Override
                protected void addCompletionVariants(@NotNull String text,
                                                     int offset,
                                                     @NotNull String prefix,
                                                     @NotNull CompletionResultSet result) {
                    List<SuggestedReviewerInfo> suggestions = completion.fetch(prefix, result, query ->
                        gerritApi.changes().id(changeInfo._number).suggestReviewers(query).withLimit(20).get());
                    for (SuggestedReviewerInfo suggestion : suggestions) {
                        // groups are suggested as reviewers, but only an account can be assigned
                        if (suggestion.account != null) {
                            result.addElement(AccountLookup.lookupElement(suggestion.account, ""));
                        }
                    }
                }
            };
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, 4));
            panel.add(new JBLabel("Assignee (leave empty to remove it):"), BorderLayout.NORTH);
            panel.add(assigneeField, BorderLayout.CENTER);
            return panel;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return assigneeField;
        }
    }
}
