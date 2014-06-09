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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.inject.Inject;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
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
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SymbolicRefs;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author Thomas Forrer
 */
public class RepositoryChangesBrowserProvider {
    @Inject
    private GerritChangeListPanel changeListPanel;
    @Inject
    private ReviewCommentSink reviewCommentSink;
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

    public RepositoryChangesBrowser get(final Project project) {
        TableView<ChangeInfo> table = changeListPanel.getTable();

        final GerritRepositoryChangesBrowser changesBrowser = new GerritRepositoryChangesBrowser(project);
        changesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), table);
        changesBrowser.getViewer().setScrollPaneBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP));
        changesBrowser.getViewer().setChangeDecorator(changesBrowser.getChangeNodeDecorator());

        reviewCommentSink.addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                changesBrowser.repaint();
            }
        });

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
        private Project project;

        public GerritRepositoryChangesBrowser(Project project) {
            super(project, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null);
            this.project = project;
        }

        @Override
        public void calcData(DataKey key, DataSink sink) {
            super.calcData(key, sink);
            sink.put(GerritDataKeys.CHANGE, selectedChange);
            sink.put(GerritDataKeys.REVIEW_COMMENT_SINK, reviewCommentSink);
        }

        protected void setSelectedChange(ChangeInfo changeInfo) {
            gerritUtil.getChangeDetails(changeInfo._number, project, new Consumer<ChangeInfo>() {
                @Override
                public void consume(ChangeInfo changeDetails) {
                    selectedChange = changeDetails;
                    updateChangesBrowser();
                    for (GerritChangeNodeDecorator decorator : changeNodeDecorators) {
                        decorator.onChangeSelected(project, selectedChange);
                    }
                }
            });
        }

        protected void updateChangesBrowser() {
            getViewer().setEmptyText("Loading...");
            setChangesToDisplay(Collections.<Change>emptyList());
            Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.getRepositoryForGerritProject(project, selectedChange.project);
            if (!gitRepositoryOptional.isPresent()) return;
            GitRepository gitRepository = gitRepositoryOptional.get();
            final VirtualFile virtualFile = gitRepository.getGitDir();
            final FilePathImpl filePath = new FilePathImpl(virtualFile);

            FetchInfo firstFetchInfo = gerritUtil.getFirstFetchInfo(selectedChange);
            String ref = firstFetchInfo != null ? firstFetchInfo.ref : null;

            if (Strings.isNullOrEmpty(ref)) {
                NotificationBuilder notification = new NotificationBuilder(
                        project, "Cannot fetch changes",
                        "No fetch information provided. If you are using Gerrit 2.8 or later, " +
                                "you need to install the plugin 'download-commands' in Gerrit."
                );
                notificationService.notifyError(notification);
                return;
            }

            String url = firstFetchInfo != null ? firstFetchInfo.url : null;
            gerritGitUtil.fetchChange(project, gitRepository, url, ref, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    final List<GitCommit> gitCommits;
                    try {
                        gitCommits = GitHistoryUtils.commitsDetails(project, filePath, new SymbolicRefs(), Collections.singletonList(selectedChange.currentRevision));
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
                    final GitCommit gitCommit = Iterables.get(gitCommits, 0);

                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            setChangesToDisplay(gitCommit.getChanges());
                        }
                    });
                    return null;
                }
            });
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
