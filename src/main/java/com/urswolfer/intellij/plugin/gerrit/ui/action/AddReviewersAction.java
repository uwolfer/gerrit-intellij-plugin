/*
 * Copyright 2013-2015 Urs Wolfer
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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldProvider;
import com.intellij.ui.SoftWrapsEditorCustomization;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Urs Wolfer
 */
public class AddReviewersAction extends AbstractLoggedInChangeAction {
    private static final String SEPARATOR = ",";

    public AddReviewersAction() {
        super(AllIcons.Toolwindows.ToolWindowTodo);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();

        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }

        AddReviewersDialog dialog = new AddReviewersDialog(project, true, GerritApiProvider.getInstance().get(GerritProjectAccount.getInstance(project).get()), selectedChange.get());
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }
        String content = dialog.reviewTextField.getText();
        for (String reviewer : content.split(SEPARATOR)) {
            // not String#trim(): a pasted name can be surrounded by whitespace which it does not remove
            String reviewerName = Whitespace.trim(reviewer);
            if (!reviewerName.isEmpty()) {
                // each one reloads: the last reload to start follows the last reviewer to be added
                gerritUtil.addReviewer(selectedChange.get().id, reviewerName, project,
                    result -> ActionUtil.reloadChanges(project));
            }
        }
    }

    private static class AddReviewersDialog extends DialogWrapper {
        private final EditorTextField reviewTextField;

        protected AddReviewersDialog(Project project,
                                     boolean canBeParent,
                                     final GerritApi gerritApi,
                                     final ChangeInfo changeInfo) {
            super(project, canBeParent);
            setTitle("Add Reviewers to Change");
            setOKButtonText("Add Reviewers");

            EditorTextFieldProvider service = ApplicationManager.getApplication().getService(EditorTextFieldProvider.class);
            Set<EditorCustomization> editorFeatures = new HashSet<EditorCustomization>();
            editorFeatures.add(SoftWrapsEditorCustomization.ENABLED);
            editorFeatures.add(SpellCheckingEditorCustomizationProvider.getInstance().getDisabledCustomization());
            reviewTextField = service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, editorFeatures);
            reviewTextField.setMinimumSize(new Dimension(500, 100));
            buildTextFieldCompletion(gerritApi, changeInfo);

            init();
        }

        private void buildTextFieldCompletion(final GerritApi gerritApi, final ChangeInfo changeInfo) {
            AccountCompletion completion = new AccountCompletion();
            TextFieldCompletionProviderDumbAware completionProvider = new TextFieldCompletionProviderDumbAware(true) {
                @NotNull
                @Override
                protected String getPrefix(@NotNull String currentTextPrefix) {
                    return AccountCompletion.prefix(currentTextPrefix, SEPARATOR);
                }

                @Nullable
                @Override
                public CharFilter.Result acceptChar(char c) {
                    return AccountCompletion.acceptChar(c, SEPARATOR);
                }

                @Override
                protected void addCompletionVariants(@NotNull final String text,
                                                     int offset,
                                                     @NotNull final String prefix,
                                                     @NotNull final CompletionResultSet result) {
                    List<SuggestedReviewerInfo> suggestedReviewers = completion.fetch(prefix, result, query ->
                        gerritApi.changes().id(changeInfo._number).suggestReviewers(query).withLimit(20).get());
                    for (SuggestedReviewerInfo suggestedReviewer : suggestedReviewers) {
                        buildLookupElement(suggestedReviewer).ifPresent(result::addElement);
                    }
                }
            };
            completionProvider.apply(reviewTextField);
        }

        private Optional<LookupElementBuilder> buildLookupElement(SuggestedReviewerInfo suggestedReviewer) {
            if (suggestedReviewer.account != null) {
                return Optional.of(AccountLookup.lookupElement(suggestedReviewer.account, SEPARATOR));
            }
            if (suggestedReviewer.group != null) {
                String groupName = suggestedReviewer.group.name;
                return Optional.of(LookupElementBuilder.create(groupName + SEPARATOR)
                    .withPresentableText(String.format("%s (group)", groupName)));
            }
            return Optional.empty();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            return reviewTextField;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return reviewTextField;
        }
    }

}
