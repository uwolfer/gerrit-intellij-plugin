/*
 * Copyright 2013 Urs Wolfer
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.LoadChangesProxy;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.ChangesFilter;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.GerritChangesFilters;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
public class GerritToolWindow implements Disposable {
    /**
     * Provided by the tool window content panel, so that actions can reach the tool window they were invoked from
     * instead of looking it up in a global holder.
     */
    public static final DataKey<GerritToolWindow> GERRIT_TOOL_WINDOW = DataKey.create("Gerrit.ToolWindow");

    private static final Logger LOG = Logger.getInstance(GerritToolWindow.class);

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private GerritChangeListPanel changeListPanel;
    private final GerritChangesFilters changesFilters = new GerritChangesFilters();
    private final RepositoryChangesBrowserProvider repositoryChangesBrowserProvider = new RepositoryChangesBrowserProvider();

    private GerritChangeDetailsPanel detailsPanel;

    /**
     * Nothing to release here: this is the parent the tool window content's listeners are registered against, and
     * the platform disposes it with the content.
     */
    @Override
    public void dispose() {
    }

    public SimpleToolWindowPanel createToolWindowContent(final Project project) {
        changeListPanel = new GerritChangeListPanel(project);

        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true) {
            @Override
            public Object getData(@NotNull String dataId) {
                if (GERRIT_TOOL_WINDOW.is(dataId)) {
                    return GerritToolWindow.this;
                }
                return super.getData(dataId);
            }
        };

        ActionToolbar toolbar = createToolbar(project);
        toolbar.setTargetComponent(changeListPanel);
        panel.setToolbar(toolbar.getComponent());

        CommittedChangesBrowser repositoryChangesBrowser = repositoryChangesBrowserProvider.get(project, changeListPanel, this);

        JBSplitter detailsSplitter = new OnePixelSplitter(true, 0.6f);
        detailsSplitter.setSplitterProportionKey("Gerrit.ListDetailSplitter.Proportion");
        detailsSplitter.setFirstComponent(changeListPanel);

        detailsPanel = new GerritChangeDetailsPanel(project);
        changeListPanel.addListSelectionListener(new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                changeSelected(changeInfo, project);
            }
        });
        JPanel details = detailsPanel.getComponent();
        detailsSplitter.setSecondComponent(details);

        JBSplitter horizontalSplitter = new OnePixelSplitter(false, 0.7f);
        horizontalSplitter.setSplitterProportionKey("Gerrit.DetailRepositoryChangeBrowser.Proportion");
        horizontalSplitter.setFirstComponent(detailsSplitter);
        horizontalSplitter.setSecondComponent(repositoryChangesBrowser);

        panel.setContent(horizontalSplitter);

        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (!repositories.isEmpty()) {
            reloadChanges(project, false);
        }

        registerVcsChangeListener(project);

        changeListPanel.showSetupHintWhenRequired(project);

        return panel;
    }

    private void registerVcsChangeListener(final Project project) {
        VcsRepositoryMappingListener vcsListener = new VcsRepositoryMappingListener() {
            @Override
            public void mappingChanged() {
                reloadChanges(project, false);
            }
        };
        project.getMessageBus().connect(this).subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, vcsListener);
    }

    private void changeSelected(ChangeInfo changeInfo, final Project project) {
        gerritUtil.getChangeDetails(changeInfo._number, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeDetails) {
                detailsPanel.setData(changeDetails);
            }
        });
    }

    public void reloadChanges(final Project project, boolean requestSettingsIfNonExistent) {
        getChanges(project, requestSettingsIfNonExistent, changeListPanel);
    }

    private void getChanges(Project project, boolean requestSettingsIfNonExistent, Consumer<LoadChangesProxy> consumer) {
        String apiUrl = gerritSettings.getHost();
        if (Strings.isNullOrEmpty(apiUrl)) {
            if (requestSettingsIfNonExistent) {
                final LoginDialog dialog = new LoginDialog(project, gerritSettings, gerritUtil);
                dialog.show();
                if (!dialog.isOK()) {
                    return;
                }
            } else {
                return;
            }
        }
        gerritUtil.getChangesForProject(changesFilters.getQuery(), project, consumer);
    }

    private ActionToolbar createToolbar(final Project project) {
        DefaultActionGroup groupFromConfig = (DefaultActionGroup) ActionManager.getInstance().getAction("Gerrit.Toolbar");
        DefaultActionGroup group = new DefaultActionGroup(groupFromConfig); // copy required (otherwise config action group gets modified)

        DefaultActionGroup filterGroup = new DefaultActionGroup();
        Iterable<ChangesFilter> filters = changesFilters.getFilters();
        for (ChangesFilter filter : filters) {
            filterGroup.add(filter.getAction(project));
        }
        filterGroup.add(new Separator());
        group.add(filterGroup, Constraints.FIRST);

        changesFilters.addListener(new GerritChangesFilters.Listener() {
            @Override
            public void filtersChanged() {
                reloadChanges(project, true);
            }
        });

        return ActionManager.getInstance().createActionToolbar("Gerrit.Toolbar", group, true);
    }
}
