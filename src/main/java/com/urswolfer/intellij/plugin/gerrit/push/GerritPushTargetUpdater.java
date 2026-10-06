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
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.push.GitPushTarget;
import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Writes the Gerrit push ref into the row of one repository of the push dialog.
 *
 * This is the way the IDE updates such a row itself when the push targets of all repositories are edited at
 * once ({@code PushLog#fireEditorUpdated}): set the text of the target editor, then let the row build its push
 * target out of it. The rows are not handed to the plugin anywhere, so they are looked up in the tree of the
 * push dialog the Gerrit push settings are shown in.
 *
 * A repository which pushes to a host without Gerrit, such as a submodule from GitHub, gets no Gerrit ref. This
 * depends on the remote the row pushes to, which the user can change in the dialog.
 *
 * @author Urs Wolfer
 */
public class GerritPushTargetUpdater implements RepositoryNodeListener<PushTarget> {
    // Hosts which serve no Gerrit. Any other host may be Gerrit under another name (an SSH host alias, a CNAME),
    // and a Gerrit repository left without its refs/for/... ref would be pushed past the review.
    private static final Set<String> NON_GERRIT_HOSTS = Set.of(
        "github.com", "www.github.com", "ssh.github.com", "gitlab.com", "altssh.gitlab.com",
        "bitbucket.org", "altssh.bitbucket.org", "codeberg.org", "dev.azure.com", "ssh.dev.azure.com");

    private final JTree tree;
    private final RepositoryNode repositoryNode;
    private final RepositoryWithBranchPanel repositoryPanel;
    private final String initialBranch;

    private String branch;
    private GitRemote remote;
    private boolean nonGerritHost;

    private GerritPushTargetUpdater(JTree tree,
                                    RepositoryNode repositoryNode,
                                    RepositoryWithBranchPanel repositoryPanel,
                                    GitPushTarget target) {
        this.tree = tree;
        this.repositoryNode = repositoryNode;
        this.repositoryPanel = repositoryPanel;
        this.initialBranch = target.getBranch().getNameForRemoteOperations();
        this.remote = target.getBranch().getRemote();
        this.nonGerritHost = pushesToNonGerritHost(remote);
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
            updaters.add(new GerritPushTargetUpdater(tree, repositoryNode, repositoryPanel, (GitPushTarget) target));
        }
        return updaters;
    }

    static boolean pushesToNonGerritHost(GitRemote remote) {
        // without a push URL of its own, a remote pushes to its fetch URL
        Collection<String> pushUrls = remote.getPushUrls().isEmpty() ? remote.getUrls() : remote.getPushUrls();
        if (pushUrls.isEmpty()) {
            return false;
        }
        for (String pushUrl : pushUrls) {
            String host;
            try {
                host = UrlUtils.createUriFromGitConfigString(pushUrl).getHost();
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            // and Azure DevOps under its former name, with a host per organization
            if (!NON_GERRIT_HOSTS.contains(host) && !host.endsWith(".visualstudio.com")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the ref the IDE pushes this repository to without the Gerrit push settings applied.
     */
    public String getInitialBranch() {
        return initialBranch;
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
     * Sets the ref to push to. A {@code null} branch reports that no usable ref could be built: the row keeps
     * the last one, as writing a ref the push dialog rejects would only log an error.
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
        // a row on a host without Gerrit only gets back what the IDE proposed, e.g. once "Push to Gerrit" is off
        if (branch == null || nonGerritHost && !branch.equals(initialBranch)) {
            return;
        }
        writeBranch(branch);
    }

    private void writeBranch(String branch) {
        repositoryNode.forceUpdateUiModelWithTypedText(branch);
        repositoryNode.fireOnChange();
        // tell the tree to repaint the changed row
        ((DefaultTreeModel) tree.getModel()).nodeChanged(repositoryNode);
    }

    /**
     * Follows the remote the user picks for this row. The IDE resets the branch of a row it has not seen edited,
     * so a remote which may be Gerrit gets the Gerrit ref again, or the row would be pushed past the review. It
     * keeps one it has seen edited, so a host without Gerrit gets back what the IDE proposed instead of the
     * Gerrit ref. Written once the IDE has told every listener about the change, as writing notifies them again.
     */
    @Override
    public void onTargetChanged(PushTarget newTarget) {
        if (!(newTarget instanceof GitPushTarget)) {
            return;
        }
        GitRemote newRemote = ((GitPushTarget) newTarget).getBranch().getRemote();
        if (newRemote.equals(remote)) {
            return;
        }
        remote = newRemote;
        nonGerritHost = pushesToNonGerritHost(newRemote);
        if (!repositoryNode.isChecked()) {
            return;
        }
        if (!nonGerritHost) {
            SwingUtilities.invokeLater(this::updateBranchTextField);
        } else if (branch != null && !branch.equals(initialBranch)
                && branch.equals(((GitPushTarget) newTarget).getBranch().getNameForRemoteOperations())) {
            SwingUtilities.invokeLater(() -> writeBranch(initialBranch));
        }
    }

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
