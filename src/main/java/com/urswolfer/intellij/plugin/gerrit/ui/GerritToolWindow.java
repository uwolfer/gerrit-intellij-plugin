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
import com.google.inject.Inject;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.LoadChangesProxy;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.CommentsDiffTool;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.ChangesFilter;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.GerritChangesFilters;

import javax.swing.*;
import java.util.Observable;
import java.util.Observer;

/**
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
public class GerritToolWindow {
    @Inject
    private DiffManager diffManager;
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private CommentsDiffTool commentsDiffTool;
    @Inject
    private GerritChangeListPanel changeListPanel;
    @Inject
    private Logger log;
    @Inject
    private GerritChangesFilters changesFilters;
    @Inject
    private RepositoryChangesBrowserProvider repositoryChangesBrowserProvider;

    private GerritChangeDetailsPanel detailsPanel;

    public SimpleToolWindowPanel createToolWindowContent(final Project project) {
        changeListPanel.registerChangeListPanel(this);
        changeListPanel.setProject(project);
        diffManager.registerDiffTool(commentsDiffTool);

        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true);

        ActionToolbar toolbar = createToolbar(project);
        toolbar.setTargetComponent(changeListPanel);
        panel.setToolbar(toolbar.getComponent());

        RepositoryChangesBrowser repositoryChangesBrowser = repositoryChangesBrowserProvider.get(project, changeListPanel);

        Splitter detailsSplitter = new Splitter(true, 0.6f);
        detailsSplitter.setShowDividerControls(true);

        changeListPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT | SideBorder.BOTTOM));
        detailsSplitter.setFirstComponent(changeListPanel);

        detailsPanel = new GerritChangeDetailsPanel(project);
        changeListPanel.addListSelectionListener(new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                changeSelected(changeInfo, project);
            }
        });
        JPanel details = detailsPanel.getComponent();
        details.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT));
        detailsSplitter.setSecondComponent(details);

        Splitter horizontalSplitter = new Splitter(false, 0.7f);
        horizontalSplitter.setShowDividerControls(true);
        horizontalSplitter.setFirstComponent(detailsSplitter);
        horizontalSplitter.setSecondComponent(repositoryChangesBrowser);

        panel.setContent(horizontalSplitter);

        reloadChanges(project, false);

        changeListPanel.showSetupHintWhenRequired(project);

        return panel;
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
                final LoginDialog dialog = new LoginDialog(project, gerritSettings, gerritUtil, log);
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

        changesFilters.addObserver(new Observer() {
            @Override
            public void update(Observable observable, Object o) {
                reloadChanges(project, true);
            }
        });

        return ActionManager.getInstance().createActionToolbar("Gerrit.Toolbar", group, true);
    }
}
