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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Urs Wolfer
 */
public class GerritPushExtensionPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance(GerritPushExtensionPanel.class);

    private static final int FIELD_WIDTH = 250;

    private static final String GITREVIEW_FILENAME = ".gitreview";
    private static final String REVIEW_REF_PREFIX = "refs/for/";
    private static final String DRAFTS_REF_PREFIX = "refs/drafts/";

    private JPanel indentedSettingPanel;

    private JCheckBox pushToGerritCheckBox;
    private JCheckBox privateCheckBox;
    private JCheckBox unmarkPrivateCheckBox;
    private JCheckBox publishDraftCommentsCheckBox;
    private JCheckBox wipCheckBox;
    private JCheckBox draftChangeCheckBox;
    private JCheckBox submitChangeCheckBox;
    private JCheckBox readyCheckBox;
    private JTextField branchTextField;
    private JTextField topicTextField;
    private JTextField hashTagTextField;
    private JComponent reviewersTextField;
    private JComponent ccTextField;
    private JTextField patchsetDescriptionTextField;
    private JLabel validationLabel;
    private JLabel noNewChangesLabel;
    private String noNewChangesKey;
    private final Map<GerritPushTargetUpdater, String> pushTargets = new LinkedHashMap<>();
    private JTree registeredTree;

    /**
     * What was chosen last for "Push to Gerrit" in a push dialog of a project, by the key of the project. Every
     * dialog gets a panel of its own, hence the static: a user who ticks the box expects the following push of
     * that project to go to Gerrit as well, and not directly to the branch. Until the box is clicked, the
     * setting decides. Other projects are left alone, a plain Git project must not get the box ticked.
     */
    private static final Map<String, Boolean> LAST_PUSH_TO_GERRIT = new ConcurrentHashMap<>();

    /**
     * Creates the completion of the reviewers and CC fields. This class is copied into the class loader of the Git
     * plugin, which knows neither the Gerrit plugin nor its REST client, so the completion is handed over from the
     * Gerrit plugin, as types of the platform and the JDK which both class loaders share.
     */
    private static volatile Function<Project, TextCompletionProvider> accountCompletion;

    /** A new default has to win over the boxes clicked so far, as it did when only a restart changed it. */
    static void forgetClickedBoxes() {
        LAST_PUSH_TO_GERRIT.clear();
    }

    private final String projectKey;

    public GerritPushExtensionPanel(boolean pushToGerritByDefault, String projectKey, @Nullable Project project) {
        this.projectKey = projectKey;
        createLayout(project);

        pushToGerritCheckBox.setSelected(LAST_PUSH_TO_GERRIT.getOrDefault(projectKey, pushToGerritByDefault));
        pushToGerritCheckBox.addActionListener(new SettingsStateActionListener());
        setSettingsEnabled(pushToGerritCheckBox.isSelected());

        addChangeListener();
    }

    @SuppressWarnings("unused") // called by reflection, on the copy in the Git plugin class loader
    public static void setAccountCompletion(Function<Project, TextCompletionProvider> accountCompletion) {
        GerritPushExtensionPanel.accountCompletion = accountCompletion;
    }

    JCheckBox getPushToGerritCheckBox() {
        return pushToGerritCheckBox;
    }

    @Override
    public void addNotify() {
        super.addNotify();

        // the repository rows are looked up in the push dialog this panel got added to; a deferred update
        // leaves the dialog the time to finish its construction
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                registerPushTargets();
            }
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();

        // the rows belong to the push dialog this panel was removed from
        registeredTree = null;
        pushTargets.clear();
        noNewChangesKey = null;
    }

    /**
     * Collects the repository rows of the push dialog and applies the Gerrit push settings to them.
     *
     * The rows of a dialog are collected once: a repeated registration would undo a push target which the user
     * has edited by hand.
     */
    private void registerPushTargets() {
        JTree tree = GerritPushTargetUpdater.findPushDialogTree(this);
        if (tree == null || tree == registeredTree) {
            return;
        }
        registeredTree = tree;

        pushTargets.clear();
        for (GerritPushTargetUpdater pushTarget : GerritPushTargetUpdater.collect(tree)) {
            pushTargets.put(pushTarget, getBranchName(pushTarget.getInitialBranch()));
        }

        if (pushTargets.size() == 1) {
            Optional<String> gitReviewBranchName = getGitReviewBranchName();
            branchTextField.setText(gitReviewBranchName.orElse(pushTargets.values().iterator().next()));
        }
        initDestinationBranch();
    }

    /**
     * Returns the branch the Gerrit push settings are applied to. A ref which already contains them (the
     * repository is configured with a Gerrit push spec) is reduced to it.
     */
    private static String getBranchName(String ref) {
        return ref.replaceAll("^(" + REVIEW_REF_PREFIX + "|" + DRAFTS_REF_PREFIX + ")", "").replaceAll("%.*$", "");
    }

    private Project getProject() {
        DataContext dataContext = DataManager.getInstance().getDataContext(this);
        return dataContext != null ? CommonDataKeys.PROJECT.getData(dataContext) : null;
    }

    private Optional<String> getGitReviewBranchName() {
        Optional<String> branchName = Optional.empty();

        Optional<Project> openedProject = Optional.ofNullable(getProject());

        if (openedProject.isPresent()) {
            String gitReviewFilePath = openedProject.get().getBasePath() + File.separator + GITREVIEW_FILENAME;

            File gitReviewFile = new File(gitReviewFilePath);
            if (gitReviewFile.exists() && gitReviewFile.isFile()) {
                FileInputStream fileInputStream = null;
                try {
                    fileInputStream = new FileInputStream(gitReviewFilePath);

                    Properties properties = new Properties();
                    properties.load(fileInputStream);
                    branchName = Optional.ofNullable(properties.getProperty("defaultbranch"))
                        .filter(branch -> !branch.isEmpty());
                } catch (IOException e) {
                    //no need to handle as branch name is already absent and ready to be returned
                } finally {
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e) {
                            //no need to handle as branch name is already absent and ready to be returned
                        }
                    }
                }
            }
        }

        return branchName;
    }

    private void createLayout(@Nullable Project project) {
        JPanel mainPanel = new JPanel();
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        pushToGerritCheckBox = new JCheckBox(PushMessages.message("push.enabled"));
        mainPanel.add(pushToGerritCheckBox);

        noNewChangesLabel = new JLabel(AllIcons.General.Warning);
        noNewChangesLabel.setVisible(false);
        mainPanel.add(noNewChangesLabel);

        indentedSettingPanel = new JPanel(new GridLayoutManager(14, 2));

        privateCheckBox = new JCheckBox(PushMessages.message("push.private"));
        privateCheckBox.setToolTipText(PushMessages.message("push.private.tooltip"));
        indentedSettingPanel.add(privateCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        unmarkPrivateCheckBox = new JCheckBox(PushMessages.message("push.unmarkPrivate"));
        unmarkPrivateCheckBox.setToolTipText(PushMessages.message("push.unmarkPrivate.tooltip"));
        indentedSettingPanel.add(unmarkPrivateCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        wipCheckBox = new JCheckBox(PushMessages.message("push.wip"));
        wipCheckBox.setToolTipText(PushMessages.message("push.wip.tooltip"));
        indentedSettingPanel.add(wipCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        readyCheckBox = new JCheckBox(PushMessages.message("push.ready"));
        readyCheckBox.setToolTipText(PushMessages.message("push.ready.tooltip"));
        indentedSettingPanel.add(readyCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        publishDraftCommentsCheckBox = new JCheckBox(PushMessages.message("push.publishComments"));
        publishDraftCommentsCheckBox.setToolTipText(PushMessages.message("push.publishComments.tooltip"));
        indentedSettingPanel.add(publishDraftCommentsCheckBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        draftChangeCheckBox = new JCheckBox(PushMessages.message("push.draft"));
        draftChangeCheckBox.setToolTipText(PushMessages.message("push.draft.tooltip"));
        indentedSettingPanel.add(draftChangeCheckBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        submitChangeCheckBox = new JCheckBox(PushMessages.message("push.submit"));
        submitChangeCheckBox.setToolTipText(PushMessages.message("push.submit.tooltip"));
        indentedSettingPanel.add(submitChangeCheckBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        branchTextField = addTextField(
                PushMessages.message("push.field.branch"),
                PushMessages.message("push.field.branch.tooltip"),
                7);

        topicTextField = addTextField(
                PushMessages.message("push.field.topic"),
                PushMessages.message("push.field.topic.tooltip"),
                8);

        hashTagTextField = addTextField(
                PushMessages.message("push.field.hashtag"),
                PushMessages.message("push.field.hashtag.tooltip"),
                9);

        patchsetDescriptionTextField = addTextField(
                PushMessages.message("push.field.description"),
                PushMessages.message("push.field.description.tooltip"),
                10);

        reviewersTextField = addField(
                PushMessages.message("push.field.reviewers"),
                PushMessages.message("push.field.reviewers.tooltip"),
                11,
                createAccountField(project));

        ccTextField = addField(
                PushMessages.message("push.field.cc"),
                PushMessages.message("push.field.cc.tooltip"),
                12,
                createAccountField(project));

        validationLabel = new JLabel();
        validationLabel.setForeground(JBColor.RED);
        indentedSettingPanel.add(
                validationLabel,
                new GridConstraints(13, 0, 1, 2,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null, null, null)
        );

        final JPanel settingLayoutPanel = new JPanel();
        settingLayoutPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingLayoutPanel.setLayout(new BoxLayout(settingLayoutPanel, BoxLayout.X_AXIS));
        settingLayoutPanel.add(Box.createRigidArea(new Dimension(20, 0)));
        settingLayoutPanel.add(indentedSettingPanel);

        mainPanel.add(settingLayoutPanel);

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(mainPanel);
        add(Box.createHorizontalGlue());
    }

    private JTextField addTextField(String label, String toolTipText, int row) {
        return addField(label, toolTipText, row, new JTextField());
    }

    private <T extends JComponent> T addField(String label, String toolTipText, int row, T field) {
        indentedSettingPanel.add(
                new JLabel(label),
                new GridConstraints(row, 0, 1, 1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        null, null, null)
        );

        field.setToolTipText(toolTipText);
        indentedSettingPanel.add(
                field,
                new GridConstraints(row, 1, 1, 1,
                        GridConstraints.ANCHOR_WEST,
                        GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_WANT_GROW,
                        GridConstraints.SIZEPOLICY_FIXED,
                        new Dimension(FIELD_WIDTH, 0), null, null)
        );
        return field;
    }

    /**
     * A field which suggests accounts, or a plain one where there is nothing to ask for them: no project, or a panel
     * which was not handed the completion (unit tests).
     */
    private static JComponent createAccountField(@Nullable Project project) {
        Function<Project, TextCompletionProvider> completion = accountCompletion;
        if (project == null || completion == null) {
            return new JTextField();
        }
        try {
            // no completion hint: an empty field has nothing to suggest
            TextFieldWithCompletion field =
                    new TextFieldWithCompletion(project, completion.apply(project), "", true, true, false);
            // an editor is as wide as its text, which would push a long list of accounts out of the dialog
            field.setPreferredWidth(FIELD_WIDTH);
            // the editor covers the field, so the tooltip of the field itself never shows
            field.addSettingsProvider(editor -> editor.getContentComponent().setToolTipText(field.getToolTipText()));
            return field;
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            // this runs in the rewritten GitPushSupport#createOptionsPanel: a field the platform cannot build any
            // more must cost the suggestions, not the push dialog
            LOG.warn("Failed to add account suggestions to Gerrit push UI.", e);
            return new JTextField();
        }
    }

    private static String getText(JComponent field) {
        return field instanceof TextFieldWithCompletion
                ? ((TextFieldWithCompletion) field).getText()
                : ((JTextField) field).getText();
    }

    private static void addDocumentListener(JComponent field, ChangeTextActionListener listener) {
        if (field instanceof TextFieldWithCompletion) {
            ((TextFieldWithCompletion) field).addDocumentListener(listener);
        } else {
            ((JTextField) field).getDocument().addDocumentListener(listener);
        }
    }

    private void addChangeListener() {
        ChangeActionListener gerritPushChangeListener = new ChangeActionListener();
        pushToGerritCheckBox.addActionListener(gerritPushChangeListener);
        privateCheckBox.addActionListener(gerritPushChangeListener);
        unmarkPrivateCheckBox.addActionListener(gerritPushChangeListener);
        wipCheckBox.addActionListener(gerritPushChangeListener);
        publishDraftCommentsCheckBox.addActionListener(gerritPushChangeListener);
        draftChangeCheckBox.addActionListener(gerritPushChangeListener);
        submitChangeCheckBox.addActionListener(gerritPushChangeListener);
        readyCheckBox.addActionListener(gerritPushChangeListener);

        ChangeTextActionListener gerritPushTextChangeListener = new ChangeTextActionListener();
        branchTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        topicTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        hashTagTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        patchsetDescriptionTextField.getDocument().addDocumentListener(gerritPushTextChangeListener);
        addDocumentListener(reviewersTextField, gerritPushTextChangeListener);
        addDocumentListener(ccTextField, gerritPushTextChangeListener);
    }

    /**
     * Builds the push target ref for the provided branch out of the Gerrit push settings which can be used.
     *
     * The values entered by the user (branch, topic, patch set description, ...) are appended as they are,
     * apart from surrounding whitespace which gets trimmed. They must never be handled as a format string:
     * the patch set description is percent-encoded, and sequences like "%2E" would be interpreted as
     * (invalid) format specifiers.
     *
     * A value which cannot be transported in a ref (e.g. a topic containing a space) is left out, and so is
     * every value from a text field without {@code withTextOptions}; {@link #validateSettings()} reports
     * them. Keeping the previous ref of the row instead is not safe: it may be the plain branch, which would
     * bypass the review, or carry a push option the user has turned off since (e.g. "submit").
     *
     * A branch which cannot be used falls back to the branch of the push target, so the push still goes to
     * review: a change on the wrong branch can be abandoned, a direct push cannot be taken back.
     */
    private String getRef(String branch, boolean withTextOptions) {
        StringBuilder ref = new StringBuilder();
        ref.append(draftChangeCheckBox.isSelected() ? DRAFTS_REF_PREFIX : REVIEW_REF_PREFIX);
        ref.append(getTargetBranch(branch));
        List<String> gerritSpecs = new ArrayList<>();
        if (privateCheckBox.isSelected()) {
            gerritSpecs.add("private");
        } else if (unmarkPrivateCheckBox.isSelected()) {
            gerritSpecs.add("remove-private");
        }
        if (wipCheckBox.isSelected()) {
            gerritSpecs.add("wip");
        } else if (readyCheckBox.isSelected()) {
            gerritSpecs.add("ready");
        }
        if (publishDraftCommentsCheckBox.isSelected()) {
            gerritSpecs.add("publish-comments");
        }
        if (submitChangeCheckBox.isSelected()) {
            gerritSpecs.add("submit");
        }
        if (withTextOptions) {
            addOption(gerritSpecs, "topic", getTrimmedText(topicTextField));
            addOption(gerritSpecs, "hashtag", getTrimmedText(hashTagTextField));
            String patchsetDescription = getTrimmedText(patchsetDescriptionTextField);
            if (!patchsetDescription.isEmpty()) {
                gerritSpecs.add("m=" + UrlUtils.encodePatchSetDescription(patchsetDescription));
            }
            for (String reviewer : splitCommaSeparated(getText(reviewersTextField))) {
                addOption(gerritSpecs, "r", reviewer);
            }
            for (String cc : splitCommaSeparated(getText(ccTextField))) {
                addOption(gerritSpecs, "cc", cc);
            }
        }
        String gerritSpec = String.join(",", gerritSpecs);
        if (!gerritSpec.isEmpty()) {
            ref.append('%').append(gerritSpec);
        }
        return ref.toString();
    }

    private String getTargetBranch(String branch) {
        String branchName = getTrimmedText(branchTextField);
        return !branchName.isEmpty() && isUsableBranch(branchName) ? branchName : branch;
    }

    private static void addOption(List<String> gerritSpecs, String option, String value) {
        if (!value.isEmpty() && PushOptionValidator.isValidOption(value)) {
            gerritSpecs.add(option + '=' + value);
        }
    }

    private static boolean isUsableBranch(String branchName) {
        return PushOptionValidator.validateBranch(PushMessages.message("push.label.branch"), branchName) == null
                && PushOptionValidator.isUsableAsBranchName(branchName);
    }

    private void handleExclusiveCheckBoxes() {
        privateCheckBox.setEnabled(!unmarkPrivateCheckBox.isSelected());
        unmarkPrivateCheckBox.setEnabled(!privateCheckBox.isSelected());
        wipCheckBox.setEnabled(!readyCheckBox.isSelected());
        readyCheckBox.setEnabled(!wipCheckBox.isSelected());
    }

    /**
     * Returns the content of a text field without surrounding whitespace: it would end up in the push ref,
     * where it cannot be used.
     */
    private static String getTrimmedText(JTextField textField) {
        return PushOptionValidator.trim(textField.getText());
    }

    /**
     * Checks all values which are added to the push ref and marks the invalid ones. The message for the first
     * of them is returned: a ref built out of such a value is not written to the push dialog.
     */
    private String validateSettings() {
        String error = null;
        if (pushToGerritCheckBox.isSelected()) {
            error = firstError(
                    validateBranch(branchTextField),
                    validateOption(topicTextField, PushMessages.message("push.label.topic")),
                    validateOption(hashTagTextField, PushMessages.message("push.label.hashtag")),
                    validateUserNames(reviewersTextField, PushMessages.message("push.label.reviewer")),
                    validateUserNames(ccTextField, PushMessages.message("push.label.cc")));
        } else {
            for (JComponent field : List.of(branchTextField, topicTextField, hashTagTextField,
                    reviewersTextField, ccTextField)) {
                markInvalid(field, false);
            }
        }
        return error;
    }

    private String validateBranch(JTextField textField) {
        String branch = getTrimmedText(textField);
        String error = PushOptionValidator.validateBranch(PushMessages.message("push.label.branch"), branch);
        if (error == null && !PushOptionValidator.isUsableAsBranchName(branch)) {
            error = PushMessages.message("push.error.branch", branch);
        }
        markInvalid(textField, error != null);
        return error;
    }

    private String validateOption(JTextField textField, String label) {
        String error = PushOptionValidator.validateOption(label, getTrimmedText(textField));
        markInvalid(textField, error != null);
        return error;
    }

    private String validateUserNames(JComponent field, String label) {
        String error = null;
        for (String item : splitCommaSeparated(getText(field))) {
            error = firstError(error, PushOptionValidator.validateOption(label, item));
        }
        markInvalid(field, error != null);
        return error;
    }

    /** Splits a comma separated value the way the push ref carries it: no surrounding whitespace, no empty entries. */
    private static List<String> splitCommaSeparated(String value) {
        return Arrays.stream(value.split(","))
                .map(PushOptionValidator::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private static String firstError(String... errors) {
        for (String error : errors) {
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    /**
     * Marks a text field with the error outline of the current IDE theme, or removes that marking again.
     */
    private static void markInvalid(JComponent component, boolean invalid) {
        component.putClientProperty("JComponent.outline", invalid ? "error" : null);
        component.repaint();
    }

    private void initDestinationBranch() {
        updateDestinationBranches(true);
    }

    private void updateDestinationBranch() {
        updateDestinationBranches(false);
    }

    /**
     * Writes the ref built out of the Gerrit push settings into the checked repository rows of the push
     * dialog, and shows the first value which cannot be used.
     */
    private void updateDestinationBranches(boolean init) {
        String error = validateSettings();
        for (Map.Entry<GerritPushTargetUpdater, String> entry : pushTargets.entrySet()) {
            String branch;
            if (pushToGerritCheckBox.isSelected()) {
                String ref = getRef(entry.getValue(), true);
                String refError = validateRef(ref);
                if (refError != null) {
                    // which of the text values the ref cannot carry is not known, so all of them are left out
                    error = firstError(error, refError);
                    ref = getRef(entry.getValue(), false);
                }
                branch = validateRef(ref) == null ? ref : null;
            } else {
                // The row gets back what the IDE proposed, untouched until "Push to Gerrit" was checked. The
                // branch the settings are applied to is no substitute: for a Gerrit push spec the IDE proposes
                // "refs/for/master", and "master" would push to the branch directly, bypassing the review.
                branch = init ? null : entry.getKey().getInitialBranch();
            }
            if (init) {
                entry.getKey().initBranch(branch);
            } else {
                entry.getKey().updateBranch(branch);
            }
        }
        validationLabel.setText(error == null ? "" : error);
        updateNoNewChangesHint();
    }

    /**
     * Warns when a push to review cannot create any change: the source is already on another branch of the
     * remote, e.g. after a feature branch was submitted and then merged into the local branch with a
     * fast-forward. The push dialog lists nothing then, as for a ref it does not know (refs/for/...) it shows
     * the commits which are on no branch of the remote, and Gerrit answers "no new changes". Unchecking "Push to
     * Gerrit" lists the commits again, which is easy to misread as the review push having lost them.
     *
     * Git is asked again only when the source, the remote or the target branch changes, not on every keystroke.
     */
    private void updateNoNewChangesHint() {
        Project project = pushToGerritCheckBox.isSelected() && !pushTargets.isEmpty() ? getProject() : null;
        List<String[]> checks = new ArrayList<>();
        if (project != null) {
            for (Map.Entry<GerritPushTargetUpdater, String> entry : pushTargets.entrySet()) {
                GerritPushTargetUpdater target = entry.getKey();
                // Gerrit takes refs/for/refs/heads/master as well, its remote-tracking branch is origin/master
                String targetBranch = getTargetBranch(entry.getValue()).replaceFirst("^refs/heads/", "");
                checks.add(new String[]{target.getRepositoryName(), target.getSourceName(), target.getRemoteName(),
                        targetBranch});
            }
        }
        String key = checks.stream().map(check -> String.join("\n", check)).collect(Collectors.joining("\n\n"));
        if (key.equals(noNewChangesKey)) {
            return;
        }
        noNewChangesKey = key;
        noNewChangesLabel.setVisible(false);
        if (checks.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String hint = findNoNewChanges(project, checks, checks.size() > 1);
            // not the IDE's invokeLater: its default modality would hold it back until the push dialog is closed
            SwingUtilities.invokeLater(() -> {
                if (key.equals(noNewChangesKey) && hint != null) {
                    noNewChangesLabel.setText(hint);
                    noNewChangesLabel.setVisible(true);
                }
            });
        });
    }

    private static String findNoNewChanges(Project project, List<String[]> checks, boolean nameRepository) {
        for (GitRepository repository : GitUtil.getRepositoryManager(project).getRepositories()) {
            for (String[] check : checks) {
                if (!DvcsUtil.getShortRepositoryName(repository).equals(check[0])) {
                    continue;
                }
                String source = check[1];
                String remote = check[2];
                GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.BRANCH);
                handler.setSilent(true);
                handler.addParameters("-r", "--contains", source);
                GitCommandResult result = Git.getInstance().runCommand(handler);
                if (!result.success()) {
                    continue;
                }
                List<String> containing = result.getOutput().stream()
                        .map(String::trim)
                        .filter(branch -> branch.startsWith(remote + "/") && !branch.contains(" -> "))
                        .collect(Collectors.toList());
                if (!containing.isEmpty() && !containing.contains(remote + "/" + check[3])) {
                    return PushMessages.message("push.nothingNew",
                            StringUtil.escapeXmlEntities(nameRepository ? check[0] + ": " + source : source),
                            StringUtil.escapeXmlEntities(containing.get(0)));
                }
            }
        }
        return null;
    }

    /**
     * Checks the assembled ref the way the push dialog does before it builds a push target out of it. It
     * catches the characters which are invalid in a ref name, which the checks per field let through (e.g.
     * the topic "bug~1").
     */
    private static String validateRef(String ref) {
        if (GitRefNameValidator.getInstance().checkInput(ref)) {
            return null;
        }
        return PushMessages.message("push.error.ref", ref);
    }

    private void setSettingsEnabled(boolean enabled) {
        UIUtil.setEnabled(indentedSettingPanel, enabled, true);
        if (enabled) {
            handleExclusiveCheckBoxes();
        }
    }

    /**
     * Updates destination branch text field after every config change.
     */
    private class ChangeActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            updateDestinationBranch();
            handleExclusiveCheckBoxes();
        }
    }

    /**
     * Updates destination branch text field after every text-field config change, of the Swing text fields and of
     * the editor based ones which suggest accounts.
     */
    private class ChangeTextActionListener
            implements javax.swing.event.DocumentListener, com.intellij.openapi.editor.event.DocumentListener {
        @Override
        public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
            // not within the write action and the command of the typing: the push targets of the rows are editors
            // as well, and their change would become part of what undo reverts in this field. Not the IDE's
            // invokeLater either: its default modality would hold it back until the push dialog is closed.
            SwingUtilities.invokeLater(this::handleChange);
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            handleChange();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            handleChange();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            handleChange();
        }

        private void handleChange() {
            updateDestinationBranch();
        }
    }

    /**
     * Activates or deactivates settings according to checkbox.
     */
    private class SettingsStateActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            setSettingsEnabled(pushToGerritCheckBox.isSelected());
            LAST_PUSH_TO_GERRIT.put(projectKey, pushToGerritCheckBox.isSelected());
        }
    }
}
