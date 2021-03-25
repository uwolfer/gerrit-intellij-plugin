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

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldProvider;
import com.intellij.ui.SoftWrapsEditorCustomization;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class AddReviewersAction extends AbstractLoggedInChangeAction {
    @Inject
    private GerritRestApi gerritApi;

    public AddReviewersAction() {
        super("Add Reviewers", "Add Reviewers to Change", AllIcons.Toolwindows.ToolWindowTodo);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);

        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }

        AddReviewersDialog dialog = new AddReviewersDialog(project, true, gerritApi, selectedChange.get());
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }
        String content = dialog.reviewTextField.getText();
        Iterable<String> reviewerNames = Splitter.on(',').omitEmptyStrings().trimResults().split(content);
        for (String reviewerName : reviewerNames) {
            gerritUtil.addReviewer(selectedChange.get().id, reviewerName, project);
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

            EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
            Set<EditorCustomization> editorFeatures = new HashSet<EditorCustomization>();
            editorFeatures.add(SoftWrapsEditorCustomization.ENABLED);
            editorFeatures.add(SpellCheckingEditorCustomizationProvider.getInstance().getDisabledCustomization());
            reviewTextField = service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, editorFeatures);
            reviewTextField.setMinimumSize(new Dimension(500, 100));
            buildTextFieldCompletion(gerritApi, changeInfo);

            init();
        }

        private void buildTextFieldCompletion(final GerritApi gerritApi, final ChangeInfo changeInfo) {
            TextFieldCompletionProviderDumbAware completionProvider = new TextFieldCompletionProviderDumbAware(true) {
                @NotNull
                @Override
                protected String getPrefix(@NotNull String currentTextPrefix) {
                    int text = currentTextPrefix.lastIndexOf(',');
                    return text == -1 ? currentTextPrefix : currentTextPrefix.substring(text + 1).trim();
                }

                @Override
                protected void addCompletionVariants(@NotNull final String text,
                                                     int offset,
                                                     @NotNull final String prefix,
                                                     @NotNull final CompletionResultSet result) {
                    if (Strings.isNullOrEmpty(prefix)) {
                        return;
                    }
                    try {
                        List<SuggestedReviewerInfo> suggestedReviewers = gerritApi.changes()
                            .id(changeInfo._number).suggestReviewers(prefix).withLimit(20).get();
                        if (result.isStopped()) {
                            return;
                        }
                        for (SuggestedReviewerInfo suggestedReviewer : suggestedReviewers) {
                            Optional<LookupElementBuilder> lookupElementBuilderOptional = buildLookupElement(suggestedReviewer);
                            if (lookupElementBuilderOptional.isPresent()) {
                                result.addElement(lookupElementBuilderOptional.get());
                            }
                        }
                    } catch (RestApiException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            completionProvider.apply(reviewTextField);
        }

        private Optional<LookupElementBuilder> buildLookupElement(SuggestedReviewerInfo suggestedReviewer) {
            String presentableText;
            String reviewerName;
            if (suggestedReviewer.account != null) {
                AccountInfo account = suggestedReviewer.account;
                if (account.email != null) {
                    presentableText = String.format("%s <%s>", account.name, account.email);
                } else {
                    presentableText = String.format("%s (%s)", account.name, account._accountId);
                }
                reviewerName = presentableText;
            } else if (suggestedReviewer.group != null) {
                presentableText = String.format("%s (group)", suggestedReviewer.group.name);
                reviewerName = suggestedReviewer.group.name;
            } else {
                return Optional.absent();
            }
            return Optional.of(LookupElementBuilder.create(reviewerName + ',').withPresentableText(presentableText));
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

    public static class Proxy extends AddReviewersAction {
        private final AddReviewersAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(AddReviewersAction.class);
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
