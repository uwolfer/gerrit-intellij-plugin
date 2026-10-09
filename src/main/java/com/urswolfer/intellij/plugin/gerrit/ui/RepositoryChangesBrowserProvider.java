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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.editor.ChainDiffVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.git.RevisionFetcher;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.action.ToggleReviewedAction;
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.ChangesWithCommitMessageProvider;
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.CommitDiffBuilder;
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.RebaseFilter;
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.SelectBaseRevisionAction;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.GoToCommentAction;
import com.urswolfer.intellij.plugin.gerrit.util.GerritUserDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitCommit;
import git4idea.changes.GitChangeUtils;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * @author Thomas Forrer
 */
public class RepositoryChangesBrowserProvider {
    private static final Logger LOG = Logger.getInstance(RepositoryChangesBrowserProvider.class);

    private final GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final NotificationService notificationService = NotificationService.getInstance();
    private GerritCommentCountChangeNodeDecorator commentCountChangeNodeDecorator;
    private SelectedRevisions selectedRevisions;
    private SelectBaseRevisionAction selectBaseRevisionAction;

    /**
     * @return the decorators applied to every change node, in the order they are applied
     */
    private List<GerritChangeNodeDecorator> changeNodeDecorators() {
        return List.of(commentCountChangeNodeDecorator);
    }

    public GerritRepositoryChangesBrowser get(Project project, GerritChangeListPanel changeListPanel, Disposable parent) {
        selectedRevisions = SelectedRevisions.getInstance(project);
        commentCountChangeNodeDecorator = new GerritCommentCountChangeNodeDecorator(project, parent);
        selectBaseRevisionAction = new SelectBaseRevisionAction(project, parent);

        TableView<ChangeInfo> table = changeListPanel.getTable();

        final GerritRepositoryChangesBrowser changesBrowser = new GerritRepositoryChangesBrowser(project, parent);
        changesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), table);
        changesBrowser.getViewerScrollPane().setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP));
        changesBrowser.setChangeNodeDecorator(changesBrowser.getChangeNodeDecorator());

        changeListPanel.addListSelectionListener(new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                changesBrowser.setSelectedChange(changeInfo);
            }
        });
        changeListPanel.addSelectionClearedListener(changesBrowser::clearSelectedChange);
        // the comment counts are loaded in background, the nodes displaying them have to be repainted afterwards
        commentCountChangeNodeDecorator.setDataLoadedCallback(new Runnable() {
            @Override
            public void run() {
                changesBrowser.getViewer().repaint();
            }
        });
        return changesBrowser;
    }

    private final class GerritRepositoryChangesBrowser extends CommittedChangesBrowser implements ListedChangeFiles {
        private ChangeInfo selectedChange;
        private Optional<Pair<String, RevisionInfo>> baseRevision = Optional.empty();
        private Integer baseParent;
        // what the listed files are of; the selected change moves on at once, while its files are still loading
        private ChangeInfo listedChange;
        private String listedRevision;
        private Project project;
        private int changesUpdate;
        private List<String> builtDiff;
        private int builtUpdate;
        private Window diffWindow;
        private DiffRequestChain diffChain;
        private boolean showingDiff;

        public GerritRepositoryChangesBrowser(Project project, Disposable parent) {
            super(project);
            this.project = project;
            selectBaseRevisionAction.addRevisionSelectedListener(new SelectBaseRevisionAction.Listener() {
                @Override
                public void revisionSelected(Optional<Pair<String, RevisionInfo>> revisionInfo) {
                    baseRevision = revisionInfo;
                    updateChangesBrowser();
                }
            });
            selectedRevisions.addListener(new SelectedRevisions.Listener() {
                @Override
                public void selectedRevisionChanged(String changeId) {
                    if (changeId != null && selectedChange != null && selectedChange.id.equals(changeId)) {
                        updateChangesBrowser();
                    }
                }
            }, parent);
        }

        /**
         * The platform opens another diff on every call, so each double click added a window, or an editor tab where
         * diffs open in the editor. Like the diff preview of the platform's commit view, the diff opened last is
         * replaced instead. It is closed rather than switched to the file, as it may show another one by now.
         *
         * TODO once the minimum IDE has ChangesBrowserBase.setShowDiffActionPreview (2020.3 has not, 2026.2 has):
         * hand the browser an EditorTabPreview there and drop this override and closeDiff(). The diff then switches
         * to the file in place, like the platform's commit view, and keeps its state. The DiffPreview API differs
         * between 2020.3 and 2026.2, so it cannot be used before.
         */
        @Override
        public void showDiff() {
            closeDiff();
            // DiffDialogHints could hand over the window, but asking for it makes the platform open a window also where
            // diffs open in an editor tab; so the window is the frame which appeared while the diff opened
            Set<Window> windows = new HashSet<>(Arrays.asList(Window.getWindows()));
            showingDiff = true;
            try {
                super.showDiff();
            } finally {
                showingDiff = false;
            }
            diffWindow = Arrays.stream(Window.getWindows())
                .filter(window -> window instanceof Frame && window.isShowing() && !windows.contains(window))
                .findFirst()
                .orElse(null);
        }

        private void closeDiff() {
            Window window = diffWindow;
            DiffRequestChain chain = diffChain;
            diffWindow = null;
            diffChain = null;
            // a comment form is a popup of the diff window, and the comment being written in it would be gone with it
            if (window != null && Arrays.stream(window.getOwnedWindows()).anyMatch(Window::isShowing)) {
                return;
            }
            boolean closed = false;
            if (chain != null) {
                // in every editor window, as the tab may have been moved to its own; such a window closes once empty
                FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
                for (VirtualFile file : fileEditorManager.getOpenFiles()) {
                    if (file instanceof ChainDiffVirtualFile && ((ChainDiffVirtualFile) file).getChain() == chain) {
                        for (EditorWindow editorWindow : fileEditorManager.getWindows()) {
                            if (editorWindow.isFileOpen(file)) {
                                fileEditorManager.closeFile(file, editorWindow);
                                closed = true;
                            }
                        }
                    }
                }
            }
            if (!closed && window != null && window.isDisplayable()) {
                // as if its close button was clicked, so that the platform releases the diff as it does then
                window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
            }
        }

        @Override
        protected void updateDiffContext(@NotNull DiffRequestChain chain) {
            super.updateDiffContext(chain);
            if (showingDiff) {
                diffChain = chain;
            }
            chain.putUserData(GerritUserDataKeys.CHANGE, listedChange);
            chain.putUserData(GerritUserDataKeys.REVISION, listedRevision);
            chain.putUserData(GerritUserDataKeys.BASE_REVISION, baseRevision);
            chain.putUserData(GerritUserDataKeys.BASE_PARENT, baseParent);
        }

        @Override
        protected @NotNull List<AnAction> createDiffActions() {
            ActionManager actionManager = ActionManager.getInstance();
            return ContainerUtil.concat(super.createDiffActions(), Arrays.asList(
                actionManager.getAction(GoToCommentAction.PREVIOUS_ID),
                actionManager.getAction(GoToCommentAction.NEXT_ID)));
        }

        @Override
        public ChangeInfo getListedChange() {
            return listedChange;
        }

        @Override
        public String getListedRevision() {
            return listedRevision;
        }

        @Override
        protected @NotNull List<AnAction> createPopupMenuActions() {
            return ContainerUtil.concat(super.createPopupMenuActions(),
                Collections.singletonList(ActionManager.getInstance().getAction(ToggleReviewedAction.ID)));
        }

        @Override
        protected @NotNull List<AnAction> createToolbarActions() {
            return ContainerUtil.prepend(super.createToolbarActions(), selectBaseRevisionAction, new Separator());
        }

        protected void setSelectedChange(ChangeInfo changeInfo) {
            // its diff only starts loading once its details are there; a reload selects the same change again, whose
            // diff may still be building and stays valid
            if (selectedChange == null || !selectedChange.id.equals(changeInfo.id)) {
                changesUpdate++;
                // the files and patch sets of the change selected before are not this one's, and stay for good when
                // its details do not come
                forgetListedChange(GerritBundle.message("details.loading"));
            }
            selectedChange = changeInfo;
            gerritUtil.getChangeDetailsOrNull(null, changeInfo._number, project, new Consumer<ChangeInfo>() {
                @Override
                public void consume(ChangeInfo changeDetails) {
                    if (changeDetails == null) {
                        // shown only while nothing is listed, so the files of an earlier load stay
                        if (selectedChange != null && selectedChange.id.equals(changeInfo.id)) {
                            getViewer().setEmptyText(GerritBundle.message("details.loadFailed"));
                        }
                        return;
                    }
                    if (selectedChange != null && selectedChange.id.equals(changeDetails.id)) {
                        selectedChange = changeDetails;
                        selectBaseRevisionAction.setSelectedChange(selectedChange);
                        baseRevision = selectBaseRevisionAction.getSelectedValue();
                        for (GerritChangeNodeDecorator decorator : changeNodeDecorators()) {
                            decorator.onChangeSelected(project, selectedChange);
                        }
                        // the diff between the same commits is still the same; building it again would fetch and run
                        // git once more for every reload
                        if (builtUpdate != changesUpdate || !diffToDisplay().equals(builtDiff)) {
                            updateChangesBrowser();
                        }
                    }
                }
            });
        }

        private List<String> diffToDisplay() {
            return Arrays.asList(selectedChange.id, selectedRevisions.get(selectedChange),
                baseRevision.map(revision -> revision.getFirst()).orElse(null));
        }

        private void clearSelectedChange() {
            selectedChange = null;
            changesUpdate++;
            forgetListedChange("");
        }

        private void forgetListedChange(String emptyText) {
            baseRevision = Optional.empty();
            baseParent = null;
            listedChange = null;
            listedRevision = null;
            selectBaseRevisionAction.clearSelectedChange();
            getViewer().setEmptyText(emptyText);
            setChangesToDisplay(Collections.<Change>emptyList());
        }

        protected void updateChangesBrowser() {
            if (selectedChange == null) { // "Diff against: Base" can still be picked once the change is gone
                return;
            }
            // without a repository there is nothing built, so a reload after the mappings changed tries again; a
            // failing fetch or git call is not retried, as it would report its error again after every action
            builtDiff = null;
            getViewer().setEmptyText(GerritBundle.message("details.loading"));
            baseParent = null;
            listedChange = null;
            listedRevision = null;
            setChangesToDisplay(Collections.<Change>emptyList());
            Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.getRepositoryForChange(project, selectedChange);
            if (!gitRepositoryOptional.isPresent()) {
                getViewer().setEmptyText(GerritBundle.message("browser.noRepository"));
                return;
            }
            final GitRepository gitRepository = gitRepositoryOptional.get();

            final ChangeInfo change = selectedChange;
            Map<String, RevisionInfo> revisions = change.revisions;
            final String revisionId = selectedRevisions.get(change);
            RevisionInfo currentRevision = revisions.get(revisionId);
            RevisionFetcher revisionFetcher = new RevisionFetcher(project, gitRepository)
                .addRevision(revisionId, currentRevision);
            // the diff is built in the background, while the user may pick another revision, base or change
            final Optional<Pair<String, RevisionInfo>> base = baseRevision;
            final int update = ++changesUpdate;
            builtDiff = diffToDisplay();
            builtUpdate = update;
            if (base.isPresent()) {
                revisionFetcher.addRevision(base.get().first, base.get().getSecond());
            }
            revisionFetcher.fetch(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    final Collection<Change> totalDiff;
                    final Integer parent;
                    try {
                        VirtualFile gitRepositoryRoot = gitRepository.getRoot();
                        CommitDiffBuilder.ChangesProvider changesProvider = new ChangesWithCommitMessageProvider();
                        GitCommit currentCommit = getCommit(gitRepositoryRoot, revisionId);
                        if (base.isPresent()) {
                            GitCommit baseCommit = getCommit(gitRepositoryRoot, base.get().first);
                            CommitDiffBuilder diffBuilder =
                                new CommitDiffBuilder(project, gitRepositoryRoot, baseCommit, currentCommit)
                                    .withChangesProvider(changesProvider);
                            // on the same parent, Gerrit lists all files which differ; without its list, those the
                            // rebase changed stay listed as they always were
                            if (!baseCommit.getParents().equals(currentCommit.getParents())) {
                                Set<String> listedPaths =
                                    gerritUtil.getFilePaths(change._number, revisionId, base.get().first, project);
                                if (listedPaths != null) {
                                    diffBuilder.withFileFilter(
                                        RebaseFilter.keepListed(listedPaths, gitRepositoryRoot.getPath()));
                                }
                            }
                            totalDiff = diffBuilder.getDiff();
                            parent = null;
                        } else if (currentCommit.getParents().size() > 1) {
                            // the changes git4idea lists for a merge are those against every parent, which leaves
                            // out all a clean merge brings in; Gerrit shows the first parent unless told otherwise
                            String firstParent = currentCommit.getParents().get(0).asString();
                            totalDiff = GitChangeUtils.getDiff(project, gitRepositoryRoot, firstParent, revisionId, null);
                            totalDiff.add(ChangesWithCommitMessageProvider.commitMessageChange(currentCommit));
                            parent = 1;
                        } else {
                            totalDiff = changesProvider.provide(currentCommit);
                            parent = null;
                        }
                    } catch (VcsException e) {
                        LOG.warn("Error getting Git commit details.", e);
                        NotificationBuilder notification = new NotificationBuilder(
                                project, GerritBundle.message("browser.gitError.title"),
                                GerritBundle.message("browser.gitError")
                        );
                        notificationService.notifyError(notification);
                        return null;
                    }

                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (update != changesUpdate) {
                                return;
                            }
                            getViewer().setEmptyText(GerritBundle.message("browser.noChanges"));
                            baseParent = parent;
                            listedChange = change;
                            listedRevision = revisionId;
                            setChangesToDisplay(new ArrayList<>(totalDiff));
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
            if (history.size() != 1) {
                throw new VcsException(GerritBundle.message("browser.error.commit", revisionId, String.valueOf(history.size())));
            }
            return history.get(0);
        }

        private ChangeNodeDecorator getChangeNodeDecorator() {
            return new ChangeNodeDecorator() {
                @Override
                public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
                    for (GerritChangeNodeDecorator decorator : changeNodeDecorators()) {
                        decorator.decorate(project, change, component, selectedChange);
                    }
                }

                @Override
                public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
                }
            };
        }
    }
}
