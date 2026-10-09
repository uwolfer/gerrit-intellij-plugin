/*
 * Copyright 2013-2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergeInput;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.push.PushAccountCompletionProvider;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("ComponentNotRegistered")
public class CreateFeatureMergeChangeAction extends AnAction implements DumbAware, UpdateInBackground {
    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
    private final NotificationService notificationService = NotificationService.getInstance();

    public CreateFeatureMergeChangeAction() {
        super(AllIcons.Vcs.Merge);
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(e.getProject() != null
            && GerritProjectAccount.getInstance(e.getProject()).isLoginAndPasswordAvailable());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) {
            return;
        }
        GitRepository repository = findRepository(project);
        if (repository == null) {
            return;
        }
        if (!repository.isOnBranch()) {
            notifyError(project, FeatureMergeBranchResolver.detachedHeadMessage());
            return;
        }

        GitLocalBranch currentBranch = repository.getCurrentBranch();
        if (currentBranch == null) {
            notifyError(project, GerritBundle.message("merge.error.noBranch"));
            return;
        }

        GitRemoteBranch upstreamBranch = currentBranch.findTrackedBranch(repository);
        String gerritProject = getGerritProject(project, repository, upstreamBranch);
        if (gerritProject == null) {
            return;
        }
        if (gerritProject.isEmpty()) {
            notifyError(project, GerritBundle.message("merge.error.noProject"));
            return;
        }

        resolveSourceBranch(project, currentBranch, upstreamBranch, gerritProject);
    }

    static ChangeInput createInput(String project, String mergeSource, @Nullable String sourceBranch,
                                   String targetBranch, String subject, String topic) {
        ChangeInput input = new ChangeInput(project.trim(), targetBranch.trim(), subject.trim());
        String trimmedTopic = topic.trim();
        input.topic = trimmedTopic.isEmpty() ? null : trimmedTopic;
        input.workInProgress = true;
        input.merge = new MergeInput();
        String normalizedMergeSource = FeatureMergeBranchResolver.normalizeBranch(mergeSource);
        input.merge.source = normalizedMergeSource.isEmpty() ? mergeSource.trim() : normalizedMergeSource;
        if (sourceBranch == null) {
            input.merge.sourceBranch = null;
        } else {
            String normalizedSourceBranch = FeatureMergeBranchResolver.normalizeBranch(sourceBranch);
            input.merge.sourceBranch = normalizedSourceBranch.isEmpty()
                    ? sourceBranch.trim()
                    : normalizedSourceBranch;
        }
        input.merge.allowConflicts = false;
        return input;
    }

    private GitRepository findRepository(Project project) {
        List<GitRepository> repositories = new ArrayList<GitRepository>();
        for (GitRepository repository : gerritGitUtil.getRepositories(project)) {
            repositories.add(repository);
        }
        if (repositories.isEmpty()) {
            gerritGitUtil.showAddGitRepositoryNotification(project);
            return null;
        }

        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selectedFiles.length > 0) {
            GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
            GitRepository selectedRepository = repositoryManager.getRepositoryForFileQuick(selectedFiles[0]);
            if (selectedRepository != null) {
                return selectedRepository;
            }
        }

        if (repositories.size() == 1) {
            return repositories.get(0);
        }

        String[] choices = new String[repositories.size()];
        for (int i = 0; i < repositories.size(); i++) {
            choices[i] = repositories.get(i).getPresentableUrl();
        }
        int selected = Messages.showChooseDialog(project,
                GerritBundle.message("merge.select.repository"), GerritBundle.message("merge.title"),
                null, choices, choices[0]);
        return selected < 0 ? null : repositories.get(selected);
    }

    @Nullable
    private String getGerritProject(Project project,
                                    GitRepository repository,
                                    @Nullable GitRemoteBranch upstreamBranch) {
        List<String> projectNames;
        if (upstreamBranch == null || upstreamBranch.getRemote() == null) {
            projectNames = GerritRemotes.getProjectNames(project, repository.getRemotes());
        } else {
            projectNames = GerritRemotes.getProjectNames(project, Collections.singletonList(upstreamBranch.getRemote()));
        }

        Set<String> uniqueProjectNames = new LinkedHashSet<String>(projectNames);
        if (uniqueProjectNames.isEmpty()) {
            return "";
        }
        if (uniqueProjectNames.size() == 1) {
            return uniqueProjectNames.iterator().next();
        }

        String[] choices = uniqueProjectNames.toArray(new String[uniqueProjectNames.size()]);
        int selected = Messages.showChooseDialog(project,
                GerritBundle.message("merge.select.project"), GerritBundle.message("merge.title"),
                null, choices, choices[0]);
        return selected < 0 ? null : choices[selected];
    }

    private void resolveSourceBranch(final Project project,
                                     final GitLocalBranch currentBranch,
                                     @Nullable final GitRemoteBranch upstreamBranch,
                                     final String gerritProject) {
        if (upstreamBranch != null) {
            String sourceBranch = FeatureMergeBranchResolver.normalizeBranch(
                    upstreamBranch.getNameForRemoteOperations());
            if (sourceBranch.isEmpty()) {
                openDialogWithTarget(project, gerritProject, "", GerritBundle.message("merge.error.upstream"),
                        currentBranch.getName());
            } else {
                openDialogWithTarget(project, gerritProject, sourceBranch, "", currentBranch.getName());
            }
            return;
        }

        Integer reviewChangeNumber = FeatureMergeBranchResolver.reviewChangeNumber(currentBranch.getName());
        if (reviewChangeNumber == null) {
            openDialogWithTarget(project, gerritProject, "", FeatureMergeBranchResolver.missingUpstreamMessage(),
                    currentBranch.getName());
            return;
        }

        gerritUtil.getChangeDetailsOrNull(gerritProject, reviewChangeNumber, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                String sourceBranch = changeInfo == null
                        ? ""
                        : FeatureMergeBranchResolver.normalizeBranch(changeInfo.branch);
                String resolvedProject = changeInfo == null || changeInfo.project == null || changeInfo.project.trim().isEmpty()
                        ? gerritProject
                        : changeInfo.project.trim();
                if (sourceBranch.isEmpty()) {
                    openDialogWithTarget(project, resolvedProject, "",
                            GerritBundle.message("merge.error.review", String.valueOf(reviewChangeNumber)), currentBranch.getName());
                } else {
                    openDialogWithTarget(project, resolvedProject, sourceBranch, "", currentBranch.getName());
                }
            }
        });
    }

    private void openDialogWithTarget(final Project project,
                                      final String gerritProject,
                                      final String sourceBranch,
                                      final String sourceExplanation,
                                      final String currentBranchName) {
        gerritUtil.getProjectHead(gerritProject, project, new Consumer<String>() {
            @Override
            public void consume(String head) {
                String targetBranch = FeatureMergeBranchResolver.normalizeHead(head);
                if (targetBranch.isEmpty()) {
                    notifyError(project, GerritBundle.message("merge.error.defaultBranch", gerritProject));
                    return;
                }
                String dialogExplanation = sourceExplanation;
                if (FeatureMergeBranchResolver.isDefaultBranch(sourceBranch, targetBranch)
                        || FeatureMergeBranchResolver.isDefaultBranch(
                        FeatureMergeBranchResolver.normalizeBranch(currentBranchName), targetBranch)) {
                    dialogExplanation = FeatureMergeBranchResolver.defaultBranchMessage(targetBranch)
                            + (sourceExplanation.isEmpty() ? "" : " " + sourceExplanation);
                }

                showDialog(project, new MergeDefaults(gerritProject, sourceBranch, targetBranch, dialogExplanation));
            }
        });
    }

    private void showDialog(final Project project, MergeDefaults defaults) {
        CreateMergeDialog dialog = new CreateMergeDialog(project, defaults);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }

        ChangeInput input = createInput(dialog.projectField.getText(), dialog.getMergeSource(),
                dialog.getSourceBranch(), dialog.targetBranchField.getText(),
                dialog.subjectField.getText(), dialog.topicField.getText());
        final List<ReviewerInput> reviewers = MergeChangeReviewers.inputs(
                dialog.reviewersField.getText(), dialog.ccField.getText());
        gerritUtil.createMergeChange(input, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(final ChangeInfo changeInfo) {
                if (reviewers.isEmpty()) {
                    ActionUtil.reloadChanges(project);
                    notifyCreated(project, changeInfo, Collections.<String, String>emptyMap());
                    return;
                }
                gerritUtil.addReviewers(changeInfo.id, reviewers, project, new Consumer<Map<String, String>>() {
                    @Override
                    public void consume(Map<String, String> failures) {
                        ActionUtil.reloadChanges(project);
                        notifyCreated(project, changeInfo, failures);
                    }
                });
            }
        });
    }

    private void notifyCreated(Project project, ChangeInfo changeInfo, Map<String, String> failures) {
        NotificationBuilder notification = new NotificationBuilder(project, GerritBundle.message("merge.created.title"),
                MergeChangeReviewers.createdText(String.valueOf(changeInfo._number), changeInfo.subject, failures));
        // the change exists either way: a warning, not an error
        if (failures.isEmpty()) {
            notificationService.notifyInformation(notification);
        } else {
            notificationService.notifyWarning(notification);
        }
    }

    static String defaultSubject(String sourceBranch) {
        return FeatureMergeBranchResolver.defaultSubject(sourceBranch);
    }

    private void notifyError(Project project, String message) {
        notificationService.notifyError(new NotificationBuilder(project, GerritBundle.message("merge.title"), message));
    }

    private class CreateMergeDialog extends DialogWrapper {
        private final JTextField projectField = new JTextField();
        private final FeatureMergeBranchSelector sourceBranchField;
        private final FeatureMergeBranchSelector targetBranchField;
        private final JTextField subjectField = new JTextField();
        private final JTextField topicField = new JTextField();
        private final TextFieldWithCompletion reviewersField;
        private final TextFieldWithCompletion ccField;
        private final MergeDefaults defaults;
        private final Project project;
        private final javax.swing.Timer branchRefreshTimer;
        private long branchRequestSerial;

        CreateMergeDialog(Project project, MergeDefaults defaults) {
            super(project, true);
            this.project = project;
            this.defaults = defaults;
            projectField.setText(defaults.project);
            reviewersField = new TextFieldWithCompletion(project, new PushAccountCompletionProvider(project), "", true, true, false);
            ccField = new TextFieldWithCompletion(project, new PushAccountCompletionProvider(project), "", true, true, false);
            reviewersField.setToolTipText(GerritBundle.message("merge.field.reviewers.tooltip"));
            ccField.setToolTipText(GerritBundle.message("merge.field.reviewers.tooltip"));
            sourceBranchField = new FeatureMergeBranchSelector(project, defaults.sourceBranch);
            targetBranchField = new FeatureMergeBranchSelector(project, defaults.targetBranch);
            subjectField.setText(defaultSubject(defaults.sourceBranch));
            subjectField.setToolTipText(GerritBundle.message("merge.field.subject.tooltip"));
            topicField.setToolTipText(GerritBundle.message("merge.field.topic.tooltip"));
            setTitle(GerritBundle.message("merge.title"));
            setOKButtonText(GerritBundle.message("merge.ok"));
            final Runnable validationListener = new Runnable() {
                @Override public void run() {
                    refreshOkEnabled();
                }
            };
            projectField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) {
                    refreshOkEnabled();
                    scheduleBranchSuggestions();
                }
                @Override public void removeUpdate(DocumentEvent e) {
                    refreshOkEnabled();
                    scheduleBranchSuggestions();
                }
                @Override public void changedUpdate(DocumentEvent e) {
                    refreshOkEnabled();
                    scheduleBranchSuggestions();
                }
            });
            sourceBranchField.addTextChangeListener(validationListener);
            sourceBranchField.addTextChangeListener(new FeatureMergeSubjectUpdater(
                    sourceBranchField::getText, subjectField));
            targetBranchField.addTextChangeListener(validationListener);
            subjectField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) {
                    validationListener.run();
                }
                @Override public void removeUpdate(DocumentEvent e) {
                    validationListener.run();
                }
                @Override public void changedUpdate(DocumentEvent e) {
                    validationListener.run();
                }
            });
            branchRefreshTimer = new javax.swing.Timer(300, e -> loadBranchSuggestions());
            branchRefreshTimer.setRepeats(false);
            init();
            scheduleBranchSuggestions();
        }

        @Override
        protected void init() {
            super.init();
            refreshOkEnabled();
        }

        @Override
        protected void dispose() {
            branchRefreshTimer.stop();
            branchRequestSerial++;
            super.dispose();
        }

        private void refreshOkEnabled() {
            if (getOKAction() != null) {
                getOKAction().setEnabled(hasText(projectField.getText()) && hasText(sourceBranchField.getText())
                        && hasText(targetBranchField.getText()) && hasText(subjectField));
            }
        }

        private String getMergeSource() {
            return sourceBranchField.getText();
        }

        @Nullable
        private String getSourceBranch() {
            return sourceBranchField.getText();
        }

        @Nullable
        @Override
        protected ValidationInfo doValidate() {
            if (!hasText(projectField)) return new ValidationInfo(GerritBundle.message("merge.validation.project"), projectField);
            if (!hasText(sourceBranchField.getText())) return new ValidationInfo(GerritBundle.message("merge.validation.source"), sourceBranchField);
            if (!hasText(targetBranchField.getText())) return new ValidationInfo(GerritBundle.message("merge.validation.target"), targetBranchField);
            if (!hasText(subjectField)) return new ValidationInfo(GerritBundle.message("merge.validation.subject"), subjectField);
            String sourceBranch = FeatureMergeBranchResolver.normalizeBranch(sourceBranchField.getText());
            String targetBranch = FeatureMergeBranchResolver.normalizeBranch(targetBranchField.getText());
            if (sourceBranch.isEmpty()) {
                return new ValidationInfo(GerritBundle.message("merge.validation.sourceRef"), sourceBranchField);
            }
            if (targetBranch.isEmpty()) {
                return new ValidationInfo(GerritBundle.message("merge.validation.targetRef"), targetBranchField);
            }
            if (FeatureMergeBranchResolver.isDefaultBranch(sourceBranch, targetBranch)) {
                return new ValidationInfo(GerritBundle.message("merge.validation.different"), targetBranchField);
            }
            return null;
        }

        private void scheduleBranchSuggestions() {
            branchRefreshTimer.restart();
        }

        private void loadBranchSuggestions() {
            final String projectName = projectField.getText().trim();
            final long requestSerial = ++branchRequestSerial;
            if (projectName.isEmpty()) {
                sourceBranchField.setVariants(Collections.<String>emptyList());
                targetBranchField.setVariants(Collections.<String>emptyList());
                return;
            }

            // Do not offer branches from the previously selected Gerrit project while this one loads.
            sourceBranchField.setVariants(Collections.<String>emptyList());
            targetBranchField.setVariants(Collections.<String>emptyList());

            gerritUtil.getProjectBranches(projectName, project, new Consumer<List<String>>() {
                @Override
                public void consume(List<String> branches) {
                    if (requestSerial != branchRequestSerial
                            || !projectName.equals(projectField.getText().trim())) {
                        return;
                    }
                    sourceBranchField.setVariants(branches);
                    targetBranchField.setVariants(branches);
                }
            });
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            int row = 0;
            if (!defaults.sourceExplanation.isEmpty()) {
                GridBagConstraints messageConstraints = new GridBagConstraints();
                messageConstraints.gridx = 0;
                messageConstraints.gridy = row++;
                messageConstraints.gridwidth = 2;
                messageConstraints.anchor = GridBagConstraints.WEST;
                messageConstraints.insets = new Insets(3, 0, 8, 0);
                panel.add(new JLabel(defaults.sourceExplanation), messageConstraints);
            }
            addRow(panel, row++, GerritBundle.message("merge.label.project"), projectField);
            addRow(panel, row++, GerritBundle.message("merge.label.source"), sourceBranchField);
            addRow(panel, row++, GerritBundle.message("merge.label.target"), targetBranchField);
            addRow(panel, row++, GerritBundle.message("merge.label.subject"), subjectField);
            addRow(panel, row++, GerritBundle.message("merge.label.topic"), topicField);
            addRow(panel, row++, GerritBundle.message("merge.label.reviewers"), reviewersField);
            addRow(panel, row, GerritBundle.message("merge.label.ccs"), ccField);
            return panel;
        }
    }

    private static class MergeDefaults {
        private final String project;
        private final String sourceBranch;
        private final String targetBranch;
        private final String sourceExplanation;

        private MergeDefaults(String project, String sourceBranch, String targetBranch, String sourceExplanation) {
            this.project = project;
            this.sourceBranch = sourceBranch;
            this.targetBranch = targetBranch;
            this.sourceExplanation = sourceExplanation;
        }
    }
    private static boolean hasText(JTextField field) {
        return hasText(field.getText());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void addRow(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(3, 0, 3, 8);
        panel.add(new JLabel(label), labelConstraints);
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(3, 0, 3, 0);
        if (field instanceof JTextField) {
            ((JTextField) field).setColumns(35);
        }
        panel.add(field, fieldConstraints);
    }

}
