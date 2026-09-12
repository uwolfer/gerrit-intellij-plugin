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

import com.intellij.dvcs.push.PushTarget;
import com.intellij.dvcs.push.RepositoryNodeListener;
import com.intellij.dvcs.push.ui.PushTargetTextField;
import com.intellij.dvcs.push.ui.RepositoryNode;
import com.intellij.dvcs.push.ui.RepositoryWithBranchPanel;
import com.intellij.openapi.diagnostic.Logger;
import git4idea.push.GitPushSupport;
import git4idea.push.GitPushTarget;
import git4idea.push.GitPushTargetPanel;
import git4idea.repo.GitRepository;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class GerritPushTargetPanel extends GitPushTargetPanel {

    private static final Logger LOG = Logger.getInstance(GerritPushTargetPanel.class);

    private String branch;
    private Field errorField;
    private String platformError;

    public GerritPushTargetPanel(@NotNull GitPushSupport support, @NotNull GitRepository repository, @Nullable GitPushTarget defaultTarget, GerritPushOptionsPanel gerritPushOptionsPanel) {
        super(support, repository, defaultTarget);

        try {
            errorField = getField("myError");
            // an error which the IDE has set itself (e.g. for a detached head); it must not be overwritten
            platformError = (String) errorField.get(this);
        } catch (NoSuchFieldException e) {
            LOG.warn("Push target error field not available; invalid branch names cannot be marked", e);
        } catch (IllegalAccessException e) {
            LOG.warn("Push target error field not accessible; invalid branch names cannot be marked", e);
        }

        String initialBranch = null;
        if (defaultTarget != null) {
            initialBranch = defaultTarget.getBranch().getNameForRemoteOperations();
        }
        gerritPushOptionsPanel.getGerritPushExtensionPanel().registerGerritPushTargetPanel(this, initialBranch);
    }

    public void initBranch(final String branch, boolean pushToGerritByDefault) {
        setBranch(branch);
        try {
            Field myFireOnChangeActionField = getField("myFireOnChangeAction");
            final Runnable myFireOnChangeAction = (Runnable) myFireOnChangeActionField.get(this);
            if (myFireOnChangeAction != null) {
                Field repoPanelField = myFireOnChangeAction.getClass().getDeclaredField("val$repoPanel");
                repoPanelField.setAccessible(true);
                RepositoryWithBranchPanel repoPanel = (RepositoryWithBranchPanel) repoPanelField.get(myFireOnChangeAction);
                //noinspection unchecked
                repoPanel.addRepoNodeListener(new RepositoryNodeListener<PushTarget>() {
                    @Override
                    public void onTargetChanged(PushTarget newTarget) {}

                    @Override
                    public void onSelectionChanged(boolean isSelected) {
                        if (isSelected) {
                            updateBranchTextField(myFireOnChangeAction);
                        }
                    }

                    @Override
                    public void onTargetInEditMode(@NotNull String s) {}
                });

                if (pushToGerritByDefault) {
                    updateBranchTextField(myFireOnChangeAction);
                }
            }
        } catch (NoSuchFieldException e) {
            LOG.error(e);
        } catch (IllegalAccessException e) {
            LOG.error(e);
        }
        updateBranch(branch);
    }

    public void updateBranch(String branch) {
        setBranch(branch);
        try {
            Field myFireOnChangeActionField = getField("myFireOnChangeAction");
            Runnable myFireOnChangeAction = (Runnable) myFireOnChangeActionField.get(this);
            if (myFireOnChangeAction != null) {
                Field repoNodeField = myFireOnChangeAction.getClass().getDeclaredField("val$repoNode");
                repoNodeField.setAccessible(true);
                RepositoryNode repoNode = (RepositoryNode) repoNodeField.get(myFireOnChangeAction);
                if (repoNode.isChecked()) {
                    updateBranchTextField(myFireOnChangeAction);
                }
            }
        } catch (NoSuchFieldException e) {
            LOG.error(e);
        } catch (IllegalAccessException e) {
            LOG.error(e);
        }
    }

    private void updateBranchTextField(Runnable myFireOnChangeAction) {
        try {
            if (branch != null) {
                Field myTargetEditorField = getField("myTargetEditor");
                PushTargetTextField myTargetEditor = (PushTargetTextField) myTargetEditorField.get(this);
                myTargetEditor.setText(branch);

                fireOnChange();
            }

            // also run it for an invalid branch name: it repaints the push dialog entry, which then shows the error
            // set by setBranch instead of a push target which would not be used
            myFireOnChangeAction.run();
        } catch (NoSuchFieldException e) {
            LOG.error(e);
        } catch (IllegalAccessException e) {
            LOG.error(e);
        }
    }

    /**
     * Marks the push target as invalid, or as valid again for a {@code null} error. As long as an error is set, the
     * IDE does not build a push target out of the text field content and the push dialog shows the error instead of
     * a branch name.
     */
    private void setError(String error) {
        if (errorField == null || platformError != null) {
            return;
        }
        try {
            errorField.set(this, error);
        } catch (IllegalAccessException e) {
            LOG.warn("Cannot update push target error", e);
        }
    }

    private Field getField(String fieldName) throws NoSuchFieldException {
        Field field = GitPushTargetPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    public void setBranch(String branch) {
        String trimmedBranch = branch == null ? "" : branch.trim();
        if (GitRefNameValidator.getInstance().checkInput(trimmedBranch)) {
            this.branch = trimmedBranch;
            setError(null);
            return;
        }
        // Values which are no valid ref names must not be set: the IDE rejects them when it builds the push target
        // out of the text field content, which makes it log an error. Such values occur regularly while the user is
        // still typing a branch name (e.g. "refs/for/release/" on the way to "refs/for/release/1.0"). Mark the push
        // target as invalid instead of leaving a branch name behind which would not be the one pushed to.
        this.branch = null;
        setError("Invalid destination branch name: " + trimmedBranch);
    }
}
