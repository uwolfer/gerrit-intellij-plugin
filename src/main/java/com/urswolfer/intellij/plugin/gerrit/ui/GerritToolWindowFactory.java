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

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.ui.action.SettingsAction;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.CommentsDiffTool;
import com.urswolfer.intellij.plugin.gerrit.util.GerritDataKeys;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SymbolicRefs;
import git4idea.repo.GitRepository;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Callable;

/**
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
public class GerritToolWindowFactory implements ToolWindowFactory {
    private GerritChangeListPanel changeListPanel;
    private RepositoryChangesBrowser myRepositoryChangesBrowser;
    private Timer myTimer;
    private Set<String> myNotifiedChanges = new HashSet<String>();
    private GerritChangeDetailsPanel myDetailsPanel;
    private Splitter myDetailsSplitter;
    private ChangeInfo mySelectedChange;
    private ReviewCommentSink myReviewCommentSink;

    public GerritToolWindowFactory() {
    }

    @Override
    public void createToolWindowContent(final Project project, ToolWindow toolWindow) {
        DiffManager.getInstance().registerDiffTool(new CommentsDiffTool());

        myReviewCommentSink = new ReviewCommentSink();

        Component component = toolWindow.getComponent();

        changeListPanel = new GerritChangeListPanel(Lists.<ChangeInfo>newArrayList(), null, myReviewCommentSink);

        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

        ActionToolbar toolbar = createToolbar();
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

        component.getParent().add(panel);

        reloadChanges(project, false);

        setupRefreshTask(project);
    }

    private RepositoryChangesBrowser createRepositoryChangesBrowser(final Project project) {
        TableView<ChangeInfo> table = changeListPanel.getTable();

        RepositoryChangesBrowser repositoryChangesBrowser = new RepositoryChangesBrowser(project, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null) {
            @Override
            public void calcData(DataKey key, DataSink sink) {
                super.calcData(key, sink);
                sink.put(GerritDataKeys.CHANGE, mySelectedChange);
                sink.put(GerritDataKeys.REVIEW_COMMENT_SINK, myReviewCommentSink);
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
        final GerritSettings settings = GerritSettings.getInstance();
        final ChangeInfo changeDetails = GerritUtil.getChangeDetails(GerritApiUtil.getApiUrl(),
                settings.getLogin(), settings.getPassword(), changeInfo.getNumber());

        myDetailsPanel.setData(changeDetails);

        updateChangesBrowser(changeDetails, project);
    }

    private void updateChangesBrowser(final ChangeInfo changeDetails, final Project project) {
        myRepositoryChangesBrowser.getViewer().setEmptyText("Loading...");
        myRepositoryChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
        final GitRepository gitRepository = GerritGitUtil.getFirstGitRepository(project);
        final VirtualFile virtualFile = gitRepository.getGitDir();
        final FilePathImpl filePath = new FilePathImpl(virtualFile);

        String ref = GerritUtil.getRef(changeDetails);

        GerritGitUtil.fetchChange(project, ref, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                final List<GitCommit> gitCommits;
                try {
                    gitCommits = GitHistoryUtils.commitsDetails(project, filePath, new SymbolicRefs(), Collections.singletonList(changeDetails.getCurrentRevision()));
                } catch (VcsException e) {
                    throw new RuntimeException(e);
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

    private void setupRefreshTask(Project project) {
        final GerritSettings settings = GerritSettings.getInstance();
        long refreshTimeout = settings.getRefreshTimeout();
        if (settings.getAutomaticRefresh() && refreshTimeout > 0) {
            myTimer = new Timer();
            myTimer.schedule(new CheckReviewTask(myTimer, project, this), refreshTimeout * 60 * 1000);
        }
    }

    private void reloadChanges(Project project, boolean requestSettingsIfNonExistent) {
        final List<ChangeInfo> commits = getChanges(project, requestSettingsIfNonExistent);
        changeListPanel.setChanges(commits);

        // if there are no changes at all, there is no point to check if new notifications should be displayed
        if (!commits.isEmpty()) {
            handleNotification(project);
        }
    }

    private List<ChangeInfo> getChanges(Project project, boolean requestSettingsIfNonExistent) {
        final GerritSettings settings = GerritSettings.getInstance();
        String apiUrl = GerritApiUtil.getApiUrl();
        if (Strings.isNullOrEmpty(apiUrl)) {
            if (requestSettingsIfNonExistent) {
                final LoginDialog dialog = new LoginDialog(project);
                dialog.show();
                if (!dialog.isOK()) {
                    return Collections.emptyList();
                }
                apiUrl = GerritApiUtil.getApiUrl();
            } else {
                return Collections.emptyList();
            }
        }
        return GerritUtil.getChangesForProject(apiUrl, settings.getLogin(), settings.getPassword(), project);
    }

    private ActionToolbar createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();

        final DumbAwareAction refreshActionAction = new DumbAwareAction("Refresh", "Refresh changes list", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(AnActionEvent anActionEvent) {
                final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
                reloadChanges(project, true);
            }
        };
        group.add(refreshActionAction);

        group.add(new SettingsAction());

        return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
    }

    private void handleNotification(Project project) {
        final GerritSettings settings = GerritSettings.getInstance();

        if (!settings.getReviewNotifications()) {
            return;
        }

        String apiUrl = GerritApiUtil.getApiUrl();
        List<ChangeInfo> changes = GerritUtil.getChangesToReview(apiUrl, settings.getLogin(), settings.getPassword());

        boolean newChange = false;
        for (ChangeInfo change : changes) {
            if (!myNotifiedChanges.contains(change.getChangeId())) {
                newChange = true;
                break;
            }
        }
        if (newChange) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("<ul>");
            for (ChangeInfo change : changes) {
                stringBuilder
                        .append("<li>")
                        .append(!myNotifiedChanges.contains(change.getChangeId()) ? "<strong>NEW: </strong>" : "")
                        .append(change.getSubject())
                        .append(" (Owner: ").append(change.getOwner().getName()).append(')')
                        .append("</li>");

                myNotifiedChanges.add(change.getChangeId());
            }
            stringBuilder.append("</ul>");
            GerritUtil.notifyInformation(project, "Gerrit Changes waiting for my review", stringBuilder.toString());
        }
    }

    class CheckReviewTask extends TimerTask {
        private Timer myTimer;
        private Project myProject;
        private GerritToolWindowFactory myToolWindowFactory;

        public CheckReviewTask(Timer timer, Project project, GerritToolWindowFactory toolWindowFactory) {
            myTimer = timer;
            myProject = project;
            myToolWindowFactory = toolWindowFactory;
        }

        @Override
        public void run() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                    myToolWindowFactory.reloadChanges(myProject, false);
                }
            });

            final GerritSettings settings = GerritSettings.getInstance();
            long refreshTimeout = settings.getRefreshTimeout();
            if (settings.getAutomaticRefresh() && refreshTimeout > 0) {
                myTimer.schedule(new CheckReviewTask(myTimer, myProject, myToolWindowFactory), refreshTimeout * 60 * 1000);
            }
        }
    }
}
