/*
 * Copyright 2013-2015 Urs Wolfer
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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.inject.Inject;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.git.RevisionFetcher;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.ChangesWithCommitMessageProvider;
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.CommitDiffBuilder;
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.SelectBaseRevisionAction;
import com.urswolfer.intellij.plugin.gerrit.util.GerritUserDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitCommit;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * @author Thomas Forrer
 */
public class RepositoryChangesBrowserProvider {
    @Inject
    private GerritGitUtil gerritGitUtil;
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private NotificationService notificationService;
    @Inject
    private Logger log;
    @Inject
    private Set<GerritChangeNodeDecorator> changeNodeDecorators;
    @Inject
    private SelectedRevisions selectedRevisions;

    private SelectBaseRevisionAction selectBaseRevisionAction;

    public RepositoryChangesBrowser get(Project project, GerritChangeListPanel changeListPanel) {
        selectBaseRevisionAction = new SelectBaseRevisionAction(selectedRevisions);

        TableView<ChangeInfo> table = changeListPanel.getTable();

        final GerritRepositoryChangesBrowser changesBrowser = new GerritRepositoryChangesBrowser(project);
        changesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), table);
        changesBrowser.getViewerScrollPane().setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP));
        changesBrowser.getViewer().setChangeDecorator(changesBrowser.getChangeNodeDecorator());

        changeListPanel.addListSelectionListener(new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                changesBrowser.setSelectedChange(changeInfo);
            }
        });
        return changesBrowser;
    }

    private final class GerritRepositoryChangesBrowser extends RepositoryChangesBrowser {
        private ChangeInfo selectedChange;
        private Optional<Pair<String, RevisionInfo>> baseRevision = Optional.absent();
        private Project project;

        public GerritRepositoryChangesBrowser(Project project) {
            super(project, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null);
            this.project = project;
            selectBaseRevisionAction.addRevisionSelectedListener(new SelectBaseRevisionAction.Listener() {
                @Override
                public void revisionSelected(Optional<Pair<String, RevisionInfo>> revisionInfo) {
                    baseRevision = revisionInfo;
                    updateChangesBrowser();
                }
            });
            selectedRevisions.addObserver(new Observer() {
                @Override
                public void update(Observable o, Object arg) {
                    if (arg != null && arg instanceof String && selectedChange != null && selectedChange.id.equals(arg)) {
                        updateChangesBrowser();
                    }
                }
            });
        }

        @Override
        protected void updateDiffContext(@NotNull ShowDiffContext context) {
            context.putChainContext(GerritUserDataKeys.CHANGE, selectedChange);
            context.putChainContext(GerritUserDataKeys.BASE_REVISION, baseRevision);
        }

        @Override
        protected void buildToolBar(DefaultActionGroup toolBarGroup) {
            toolBarGroup.add(selectBaseRevisionAction);
            toolBarGroup.add(new Separator());
            super.buildToolBar(toolBarGroup);
        }

        protected void setSelectedChange(ChangeInfo changeInfo) {
            selectedChange = changeInfo;
            gerritUtil.getChangeDetails(changeInfo._number, project, new Consumer<ChangeInfo>() {
                @Override
                public void consume(ChangeInfo changeDetails) {
                    if (selectedChange.id.equals(changeDetails.id)) {
                        selectedChange = changeDetails;
                        baseRevision = Optional.absent();
                        selectBaseRevisionAction.setSelectedChange(selectedChange);
                        for (GerritChangeNodeDecorator decorator : changeNodeDecorators) {
                            decorator.onChangeSelected(project, selectedChange);
                        }
                        updateChangesBrowser();
                    }
                }
            });
        }

        protected void updateChangesBrowser() {
            getViewer().setEmptyText("Loading...");
            setChangesToDisplay(Collections.<Change>emptyList());
            Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.getRepositoryForGerritProject(project, selectedChange.project);
            if (!gitRepositoryOptional.isPresent()) {
                getViewer().setEmptyText("Diff cannot be displayed as no local repository was found");
                return;
            }
            final GitRepository gitRepository = gitRepositoryOptional.get();

            Map<String, RevisionInfo> revisions = selectedChange.revisions;
            final String revisionId = selectedRevisions.get(selectedChange);
            RevisionInfo currentRevision = revisions.get(revisionId);
            RevisionFetcher revisionFetcher = new RevisionFetcher(gerritUtil, gerritGitUtil, notificationService, project, gitRepository)
                .addRevision(revisionId, currentRevision);
            if (baseRevision.isPresent()) {
                revisionFetcher.addRevision(baseRevision.get().first, baseRevision.get().getSecond());
            }
            revisionFetcher.fetch(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    final Collection<Change> totalDiff;
                    try {
                        VirtualFile gitRepositoryRoot = gitRepository.getRoot();
                        CommitDiffBuilder.ChangesProvider changesProvider = new ChangesWithCommitMessageProvider();
                        GitCommit currentCommit = getCommit(gitRepositoryRoot, revisionId);
                        if (baseRevision.isPresent()) {
                            GitCommit baseCommit = getCommit(gitRepositoryRoot, baseRevision.get().first);
                            totalDiff = new CommitDiffBuilder(project, gitRepositoryRoot, baseCommit, currentCommit)
                                .withChangesProvider(changesProvider).getDiff();
                        } else {
                            totalDiff = changesProvider.provide(currentCommit);
                        }
                    } catch (VcsException e) {
                        log.warn("Error getting Git commit details.", e);
                        NotificationBuilder notification = new NotificationBuilder(
                                project, "Cannot show change",
                                "Git error occurred while getting commit. Please check if Gerrit is configured as remote " +
                                        "for the currently used Git repository."
                        );
                        notificationService.notifyError(notification);
                        return null;
                    }

                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            getViewer().setEmptyText("No changes");
                            setChangesToDisplay(Lists.newArrayList(totalDiff));
                        }
                    });
                    return null;
                }
            });
        }

        private GitCommit getCommit(VirtualFile gitRepositoryRoot, String revisionId) throws VcsException {
            // -1: limit; log exactly this commit; git show would do this job also, but there is no api in GitHistoryUtils
            // ("git show hash" <-> "git log hash -1")
            List<GitCommit> history = GitHistoryUtils.history(project, gitRepositoryRoot, revisionId, "-1");
            return Iterables.getOnlyElement(history);
        }

        private ChangeNodeDecorator getChangeNodeDecorator() {
            return new ChangeNodeDecorator() {
                @Override
                public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
                    for (GerritChangeNodeDecorator decorator : changeNodeDecorators) {
                        decorator.decorate(project, change, component, selectedChange);
                    }
                }

                @Nullable
                @Override
                public List<Pair<String, Stress>> stressPartsOfFileName(Change change, String parentPath) {
                    return null;
                }

                @Override
                public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
                }
            };
        }
    }
}
