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

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.ui.action.SettingsAction;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.CommentsDiffTool;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.ChangesFilter;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.GerritChangesFilters;
import com.urswolfer.intellij.plugin.gerrit.util.GerritDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SymbolicRefs;
import git4idea.repo.GitRepository;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Callable;

/**
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
public class GerritToolWindow {
    @Inject
    private DiffManager diffManager;
    @Inject
    private GerritGitUtil gerritGitUtil;
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private CommentsDiffTool commentsDiffTool;
    @Inject
    private SettingsAction settingsAction;
    @Inject
    private GerritChangeListPanel changeListPanel;
    @Inject
    private ReviewCommentSink reviewCommentSink;
    @Inject
    private GerritUpdatesNotificationComponent gerritUpdatesNotificationComponent;
    @Inject
    private Logger log;
    @Inject
    private GerritChangesFilters changesFilters;
    @Inject
    private NotificationService notificationService;

    private RepositoryChangesBrowser myRepositoryChangesBrowser;
    private GerritChangeDetailsPanel myDetailsPanel;
    private Splitter myDetailsSplitter;
    private ChangeInfo mySelectedChange;

    public SimpleToolWindowPanel createToolWindowContent(final Project project) {
        diffManager.registerDiffTool(commentsDiffTool);

        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true);

        ActionToolbar toolbar = createToolbar(project);
        toolbar.setTargetComponent(changeListPanel);
        panel.setToolbar(toolbar.getComponent());

        myRepositoryChangesBrowser = createRepositoryChangesBrowser(project);

        myDetailsSplitter = new Splitter(true, 0.6f);
        myDetailsSplitter.setShowDividerControls(true);

        changeListPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT | SideBorder.BOTTOM));
        myDetailsSplitter.setFirstComponent(changeListPanel);

        myDetailsPanel = new GerritChangeDetailsPanel(project);
        JPanel details = myDetailsPanel.getComponent();
        details.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT));
        myDetailsSplitter.setSecondComponent(details);

        Splitter myHorizontalSplitter = new Splitter(false, 0.7f);
        myHorizontalSplitter.setShowDividerControls(true);
        myHorizontalSplitter.setFirstComponent(myDetailsSplitter);
        myHorizontalSplitter.setSecondComponent(myRepositoryChangesBrowser);

        panel.setContent(myHorizontalSplitter);

        reloadChanges(project, false);

        return panel;
    }

    private RepositoryChangesBrowser createRepositoryChangesBrowser(final Project project) {
        TableView<ChangeInfo> table = changeListPanel.getTable();

        RepositoryChangesBrowser repositoryChangesBrowser = new RepositoryChangesBrowser(project, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null) {
            @Override
            public void calcData(DataKey key, DataSink sink) {
                super.calcData(key, sink);
                sink.put(GerritDataKeys.CHANGE, mySelectedChange);
                sink.put(GerritDataKeys.REVIEW_COMMENT_SINK, reviewCommentSink);
            }
        };
        repositoryChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), table);
        repositoryChangesBrowser.getViewer().setScrollPaneBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP));

        changeListPanel.addListSelectionListener(new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                changeSelected(changeInfo, project);
                mySelectedChange = changeInfo;
            }
        });
        return repositoryChangesBrowser;
    }

    private void changeSelected(ChangeInfo changeInfo, final Project project) {
        gerritUtil.getChangeDetails(changeInfo.getNumber(), project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeDetails) {
                myDetailsPanel.setData(changeDetails);

                updateChangesBrowser(changeDetails, project);
            }
        });
    }

    private void updateChangesBrowser(final ChangeInfo changeDetails, final Project project) {
        myRepositoryChangesBrowser.getViewer().setEmptyText("Loading...");
        myRepositoryChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
        Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.getRepositoryForGerritProject(project, changeDetails.getProject());
        if (!gitRepositoryOptional.isPresent()) return;
        GitRepository gitRepository = gitRepositoryOptional.get();
        final VirtualFile virtualFile = gitRepository.getGitDir();
        final FilePathImpl filePath = new FilePathImpl(virtualFile);

        String ref = gerritUtil.getRef(changeDetails);
        if (Strings.isNullOrEmpty(ref)) {
            NotificationBuilder notification = new NotificationBuilder(
                    project, "Cannot fetch changes",
                    "No fetch information provided. If you are using Gerrit 2.8 or later, " +
                    "you need to install the plugin 'download-commands' in Gerrit."
            );
            notificationService.notifyError(notification);
            return;
        }

        gerritGitUtil.fetchChange(project, gitRepository, ref, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                final List<GitCommit> gitCommits;
                try {
                    gitCommits = GitHistoryUtils.commitsDetails(project, filePath, new SymbolicRefs(), Collections.singletonList(changeDetails.getCurrentRevision()));
                } catch (VcsException e) {
                    throw Throwables.propagate(e);
                }
                final GitCommit gitCommit = Iterables.get(gitCommits, 0);

                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        myRepositoryChangesBrowser.setChangesToDisplay(gitCommit.getChanges());
                    }
                });
                return null;
            }
        });
    }

    private void reloadChanges(final Project project, boolean requestSettingsIfNonExistent) {
        getChanges(project, requestSettingsIfNonExistent, changeListPanel);
    }

    private void getChanges(Project project, boolean requestSettingsIfNonExistent, Consumer<List<ChangeInfo>> consumer) {
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
        gerritUtil.getChanges(changesFilters.getQuery(), project, consumer);
    }

    private ActionToolbar createToolbar(final Project project) {
        DefaultActionGroup group = new DefaultActionGroup();

        Iterable<ChangesFilter> filters = changesFilters.getFilters();
        for (ChangesFilter filter : filters) {
            group.add(filter.getAction(project));
        }

        changesFilters.addObserver(new Observer() {
            @Override
            public void update(Observable observable, Object o) {
                reloadChanges(project, true);
            }
        });

        group.add(new Separator());

        final DumbAwareAction refreshActionAction = new DumbAwareAction("Refresh", "Refresh changes list", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(AnActionEvent anActionEvent) {
                final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
                reloadChanges(project, true);
                gerritUpdatesNotificationComponent.handleNotification();
            }
        };
        group.add(refreshActionAction);

        group.add(new Separator());

        group.add(settingsAction);

        return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    }
}
