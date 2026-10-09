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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.util.CommitChanges;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.MyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shows the Gerrit changes of the commits selected in the VCS log in the Gerrit tool window.
 *
 * Not {@link com.intellij.openapi.actionSystem.UpdateInBackground}: the selection of the log is read straight from
 * its Swing table.
 *
 * @author Urs Wolfer
 */
public class OpenCommitInGerritAction extends AnAction implements DumbAware {

    /**
     * The query ends up in the URL of the REST request, which a proxy in front of Gerrit may limit to 8 KB.
     */
    private static final int MAX_COMMITS = 50;

    public OpenCommitInGerritAction() {
        super(MyIcons.Gerrit);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
        Project project = e.getProject();
        if (project == null || log == null || !GerritProjectSettings.isEnabled(project)
            || GerritProjectAccount.getInstance(project).getHost().isEmpty()) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        // the selection resolves a commit only when it is asked for one, but select-all can span the whole history
        List<CommitId> commits = log.getSelectedCommits();
        boolean tooMany = commits.size() > MAX_COMMITS;
        // too many commits to look at are not too many to say so, as long as the log has a Gerrit repository at all
        Collection<VirtualFile> roots = tooMany ? log.getLogProviders().keySet() : getRoots(commits);
        e.getPresentation().setVisible(!getGerritRoots(project, roots).isEmpty());
        e.getPresentation().setEnabled(!tooMany);
        // a menu shows nothing else of a disabled action
        String text = GerritBundle.message("action.Gerrit.OpenCommitInGerrit.text");
        e.getPresentation().setText(tooMany ? GerritBundle.message("action.Gerrit.OpenCommitInGerrit.tooMany", text, MAX_COMMITS) : text);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
        Project project = e.getProject();
        if (project == null || log == null) {
            return;
        }
        List<CommitId> selection = log.getSelectedCommits();
        if (selection.size() > MAX_COMMITS) {
            return;
        }
        // each read of the selection looks the commit up again
        List<CommitId> commits = new ArrayList<>(selection);
        Set<VirtualFile> gerritRoots = getGerritRoots(project, getRoots(commits));
        List<String> hashes = commits.stream()
            .filter(commit -> commit != null && gerritRoots.contains(commit.getRoot()))
            .map(commit -> commit.getHash().asString())
            .collect(Collectors.toList());
        if (hashes.isEmpty()) {
            return;
        }
        ActionUtil.showChanges(project, getQuery(hashes));
    }

    /**
     * The log storage may not know a selected row yet, as while it is being rebuilt.
     */
    private static Set<VirtualFile> getRoots(List<CommitId> commits) {
        return commits.stream().filter(Objects::nonNull).map(CommitId::getRoot).collect(Collectors.toSet());
    }

    private static Set<VirtualFile> getGerritRoots(Project project, Collection<VirtualFile> roots) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        return roots.stream()
            .filter(root -> {
                GitRepository repository = repositoryManager.getRepositoryForRootQuick(root);
                return repository != null && GerritRemotes.getGerritProject(repository, null) != null;
            })
            .collect(Collectors.toSet());
    }

    @VisibleForTesting
    static String getQuery(Collection<String> hashes) {
        return CommitChanges.getQuery(hashes);
    }
}
