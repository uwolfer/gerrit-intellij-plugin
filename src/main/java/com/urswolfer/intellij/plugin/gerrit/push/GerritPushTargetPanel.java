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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class GerritPushTargetPanel extends GitPushTargetPanel {

    private static final Logger LOG = Logger.getInstance(GerritPushTargetPanel.class);
    private String branch;

    public GerritPushTargetPanel(@NotNull GitPushSupport support, @NotNull GitRepository repository, @Nullable GitPushTarget defaultTarget, GerritPushOptionsPanel gerritPushOptionsPanel) {
        super(support, repository, defaultTarget);

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
            Field myTargetEditorField = getField("myTargetEditor");
            PushTargetTextField myTargetEditor = (PushTargetTextField) myTargetEditorField.get(this);
            myTargetEditor.setText(branch);

            fireOnChange();

            myFireOnChangeAction.run();
        } catch (NoSuchFieldException e) {
            LOG.error(e);
        } catch (IllegalAccessException e) {
            LOG.error(e);
        }
    }

    private Field getField(String fieldName) throws NoSuchFieldException {
        Field field = GitPushTargetPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    public void setBranch(String branch) {
        if (branch == null || branch.isEmpty() || branch.endsWith("/")) {
            this.branch = null;
            return;
        }
        this.branch = branch.trim();
    }
}
