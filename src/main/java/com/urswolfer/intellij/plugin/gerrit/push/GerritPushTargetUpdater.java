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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.intellij.dvcs.push.PushTarget;
import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.dvcs.push.RepositoryNodeListener;
import com.intellij.dvcs.push.ui.PushLog;
import com.intellij.dvcs.push.ui.RepositoryNode;
import com.intellij.dvcs.push.ui.RepositoryWithBranchPanel;
import com.intellij.util.ui.UIUtil;
import git4idea.push.GitPushTarget;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Writes the Gerrit push ref into the row of one repository of the push dialog.
 *
 * This is the way the IDE updates such a row itself when the push targets of all repositories are edited at
 * once ({@code PushLog#fireEditorUpdated}): set the text of the target editor, then let the row build its push
 * target out of it. The rows are not handed to the plugin anywhere, so they are looked up in the tree of the
 * push dialog the Gerrit push settings are shown in.
 *
 * @author Urs Wolfer
 */
public class GerritPushTargetUpdater implements RepositoryNodeListener<PushTarget> {

    private final JTree tree;
    private final RepositoryNode repositoryNode;
    private final RepositoryWithBranchPanel repositoryPanel;
    private final String initialBranch;

    private String branch;

    private GerritPushTargetUpdater(JTree tree,
                                    RepositoryNode repositoryNode,
                                    RepositoryWithBranchPanel repositoryPanel,
                                    String initialBranch) {
        this.tree = tree;
        this.repositoryNode = repositoryNode;
        this.repositoryPanel = repositoryPanel;
        this.initialBranch = initialBranch;
    }

    /**
     * Returns the tree with the repository rows of the push dialog {@code component} is shown in, or
     * {@code null} as long as the component is not part of a push dialog.
     */
    public static JTree findPushDialogTree(JComponent component) {
        JRootPane rootPane = SwingUtilities.getRootPane(component);
        if (rootPane == null) {
            return null;
        }
        PushLog pushLog = UIUtil.findComponentOfType(rootPane, PushLog.class);
        return pushLog == null ? null : pushLog.getTree();
    }

    /**
     * Returns an updater for every Git repository of the push dialog.
     *
     * Rows which the IDE has no push target for are skipped: a row of another VCS, and a Git repository where
     * it cannot tell where to push to (e.g. a repository without any remote). Such a row does not accept a
     * push target built out of a text, so there is nothing a Gerrit ref could be written to.
     */
    public static List<GerritPushTargetUpdater> collect(JTree tree) {
        Object root = tree.getModel().getRoot();
        if (!(root instanceof TreeNode)) {
            return Collections.emptyList();
        }
        TreeNode rootNode = (TreeNode) root;
        List<GerritPushTargetUpdater> updaters = new ArrayList<>();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            TreeNode child = rootNode.getChildAt(i);
            if (!(child instanceof RepositoryNode)) {
                continue;
            }
            RepositoryNode repositoryNode = (RepositoryNode) child;
            Object userObject = repositoryNode.getUserObject();
            if (!(userObject instanceof RepositoryWithBranchPanel)) {
                continue;
            }
            RepositoryWithBranchPanel repositoryPanel = (RepositoryWithBranchPanel) userObject;
            PushTargetPanel targetPanel = repositoryPanel.getTargetPanel();
            Object target = targetPanel.getValue();
            if (!(target instanceof GitPushTarget)) {
                continue;
            }
            updaters.add(new GerritPushTargetUpdater(tree, repositoryNode, repositoryPanel,
                    ((GitPushTarget) target).getBranch().getNameForRemoteOperations()));
        }
        return updaters;
    }

    /**
     * Returns the ref the IDE pushes this repository to without the Gerrit push settings applied.
     */
    public String getInitialBranch() {
        return initialBranch;
    }

    /**
     * Returns whether the IDE is still loading the commits of this repository.
     *
     * Writing the push target of such a row cancels that load, and the IDE starts the replacement as a
     * follow-up load - the one kind after which it never unchecks a repository again.
     */
    public boolean isLoading() {
        return repositoryNode.isLoading();
    }

    /**
     * Starts following the checked state of this repository and writes the first ref into its row.
     */
    public void initBranch(String branch) {
        //noinspection unchecked
        repositoryPanel.addRepoNodeListener(this);
        updateBranch(branch);
    }

    /**
     * Sets the ref to push to. A {@code null} branch reports Gerrit push settings which cannot be transported
     * in a ref: the last usable ref is kept, the one the push dialog already shows and the one the push would
     * use, instead of a ref which does not contain what the user entered.
     *
     * A row which is not checked is left alone: writing to it checks it (the IDE checks a repository as soon
     * as its push target changes), which would select every repository of the project for the push. Such a
     * row gets the ref once the user checks it, see {@link #onSelectionChanged(boolean)}.
     */
    public void updateBranch(String branch) {
        if (branch == null) {
            return;
        }
        this.branch = branch;
        if (repositoryNode.isChecked()) {
            updateBranchTextField();
        }
    }

    private void updateBranchTextField() {
        if (branch == null) {
            return;
        }
        repositoryNode.forceUpdateUiModelWithTypedText(branch);
        repositoryNode.fireOnChange();
        // tell the tree to repaint the changed row
        ((DefaultTreeModel) tree.getModel()).nodeChanged(repositoryNode);
    }

    @Override
    public void onTargetChanged(PushTarget newTarget) {}

    /**
     * Writes the last usable ref built out of the Gerrit push settings into a row the user has just checked:
     * it was skipped as long as the repository was not part of the push.
     */
    @Override
    public void onSelectionChanged(boolean isSelected) {
        if (isSelected) {
            updateBranchTextField();
        }
    }

    @Override
    public void onTargetInEditMode(@NotNull String currentValue) {}
}
