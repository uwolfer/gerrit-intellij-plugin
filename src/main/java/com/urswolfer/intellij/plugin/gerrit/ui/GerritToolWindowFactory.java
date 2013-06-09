/*
 * Copyright 2013 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.ui;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import com.google.common.collect.Iterables;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.util.CollectConsumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.FetchInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.RevisionInfo;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.util.GitCommitCompareInfo;

/**
 * @author Urs Wolfer
 */
public class GerritToolWindowFactory implements ToolWindowFactory {
    private GerritChangeListPanel changeListPanel;
    private ChangeInfo currentChangeInfo;

    public GerritToolWindowFactory() {
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        Component component = toolWindow.getComponent();

        changeListPanel = new GerritChangeListPanel(getChanges(), null);
        changeListPanel.addListSelectionListener(new CollectConsumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                currentChangeInfo = changeInfo;
            }
        });

        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
        panel.setContent(changeListPanel);

        ActionToolbar toolbar = createToolbar();
        toolbar.setTargetComponent(changeListPanel);
        panel.setToolbar(toolbar.getComponent());

        component.getParent().add(panel);
    }

    private void reloadChanges() {
        final List<ChangeInfo> commits = getChanges();
        changeListPanel.setChanges(commits);
    }

    private List<ChangeInfo> getChanges() {
        final GerritSettings settings = GerritSettings.getInstance();
        return GerritUtil.getChanges(GerritApiUtil.getApiUrl(), settings.getLogin(), settings.getPassword());
    }

    private ActionToolbar createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();

        final DumbAwareAction refreshActionAction = new DumbAwareAction("Refresh", "Refresh changes list", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(AnActionEvent anActionEvent) {
                reloadChanges();
            }
        };
        group.add(refreshActionAction);

        final DumbAwareAction fetchChangeButton = new DumbAwareAction("Fetch", "Fetch change", AllIcons.Actions.Download) {
            @Override
            public void actionPerformed(AnActionEvent anActionEvent) {
                final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
                final GerritSettings settings = GerritSettings.getInstance();
                final ChangeInfo changeDetails = GerritUtil.getChangeDetails(GerritApiUtil.getApiUrl(),
                        settings.getLogin(), settings.getPassword(),
                        currentChangeInfo.getNumber());

                String ref = null;
                final TreeMap<String,RevisionInfo> revisions = changeDetails.getRevisions();
                for (RevisionInfo revisionInfo : revisions.values()) {
                    final TreeMap<String,FetchInfo> fetch = revisionInfo.getFetch();
                    for (FetchInfo fetchInfo : fetch.values()) {
                        ref = fetchInfo.getRef();
                    }
                }

                GerritGitUtil.fetchChange(project, ref);
            }
        };
        group.add(fetchChangeButton);

        final DumbAwareAction diffChangeAction = new DumbAwareAction("Compare", "Compare change", AllIcons.Actions.DiffWithCurrent) {
            @Override
            public void actionPerformed(AnActionEvent anActionEvent) {
                final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
                diffChange(project);
            }
        };
        group.add(diffChangeAction);

        return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
    }

    private void diffChange(Project project) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        final Collection<GitRepository> repositoriesFromRoots = repositoryManager.getRepositories();

        final GitRepository gitRepository = Iterables.get(repositoriesFromRoots, 0);

        final String branchName = "FETCH_HEAD";
        final String currentBranch = gitRepository.getCurrentBranch().getFullName();

        final GitCommitCompareInfo compareInfo = GerritGitUtil.loadCommitsToCompare(repositoriesFromRoots, branchName, project);
        new GitCompareBranchesDialog(project, branchName, currentBranch, compareInfo, gitRepository).show();
    }
}
