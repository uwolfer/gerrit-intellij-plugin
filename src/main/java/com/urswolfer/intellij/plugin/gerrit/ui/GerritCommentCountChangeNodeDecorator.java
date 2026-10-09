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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;
import git4idea.repo.GitRepository;

import java.util.*;

/**
 * @author Thomas Forrer
 */
public class GerritCommentCountChangeNodeDecorator implements GerritChangeNodeDecorator, Disposable {
    private static final Logger LOG = Logger.getInstance(GerritCommentCountChangeNodeDecorator.class);

    /**
     * A character after the file name rather than an icon: the node already has the icon of its file type, and
     * layering a mark on it, or a second icon, would have to be fitted to every icon theme and to the selected row.
     * The text is as legible in both themes as the green is, which {@link JBColor#GREEN} has for each.
     */
    private static final String REVIEWED_TICK = "\u2713";
    private static final SimpleTextAttributes REVIEWED_TICK_ATTRIBUTES =
        new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.GREEN);

    private final GerritSettings gerritSettings = GerritSettings.getInstance();

    private final SelectedRevisions selectedRevisions;
    private final ReviewedFilesService reviewedFiles;
    private final Project project;

    private ChangeInfo selectedChange;
    /**
     * The repository of {@link #selectedChange}, resolved on the event dispatch thread when the first node is painted
     * rather than once per node; null until then.
     */
    private Optional<GitRepository> selectedRepository;

    /** Loaded by {@link #loadData()}; only read and written on the event dispatch thread. */
    private Map<String, List<CommentInfo>> comments = Collections.emptyMap();
    private Map<String, List<CommentInfo>> drafts = Collections.emptyMap();

    /** Incremented on the event dispatch thread for every load, so that a load in progress can tell it is obsolete. */
    private volatile long loadGeneration;

    private Runnable dataLoadedCallback;

    /** Own flag, as the Disposer forgets what it has disposed, e.g. on a major GC. */
    private volatile boolean disposed;

    public GerritCommentCountChangeNodeDecorator(Project project, Disposable parent) {
        this.project = project;
        Disposer.register(parent, this);
        this.selectedRevisions = SelectedRevisions.getInstance(project);
        this.reviewedFiles = ReviewedFilesService.getInstance(project);
        // a file marked by its diff or by the toggle shows at once; nodes are repainted as when data has been loaded
        this.reviewedFiles.addListener(() -> {
            if (!disposed && dataLoadedCallback != null) {
                dataLoadedCallback.run();
            }
        }, this);
        this.selectedRevisions.addListener(new SelectedRevisions.Listener() {
            @Override
            public void selectedRevisionChanged(String changeId) {
                if (changeId != null && selectedChange != null && selectedChange.id.equals(changeId)) {
                    loadData();
                }
            }
        }, this);
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    /**
     * @param dataLoadedCallback executed on the event dispatch thread once the data of the selected change has been
     *                           loaded, so that the nodes displaying it can be repainted
     */
    public void setDataLoadedCallback(Runnable dataLoadedCallback) {
        this.dataLoadedCallback = dataLoadedCallback;
    }

    /**
     * Called while a change node gets painted, so it must not perform any remote calls: it displays what
     * {@link #loadData()} has loaded so far.
     */
    @Override
    public void decorate(Project project, Change change, SimpleColoredComponent component, ChangeInfo selectedChange) {
        String affectedFilePath = getAffectedFilePath(change);
        if (affectedFilePath != null) {
            String fileName = getFileName(project, affectedFilePath);
            boolean reviewed = reviewedFiles.isReviewed(fileName);
            if (reviewed) {
                component.append(" " + REVIEWED_TICK, REVIEWED_TICK_ATTRIBUTES);
            }
            String text = getNodeSuffix(fileName);
            if (!text.isEmpty()) {
                component.append(String.format(" (%s)", text), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
            }
            if (reviewed || !text.isEmpty()) {
                component.repaint();
            }
        }
    }

    @Override
    public void onChangeSelected(Project project, ChangeInfo selectedChange) {
        this.selectedChange = selectedChange;
        this.selectedRepository = null;
        loadData();
    }

    /**
     * Loads the comments, drafts and reviewed files of the selected change in the background. Every load gets a
     * generation which is checked before each request and before the result is published, so that a load which has
     * been superseded (another change or another revision of it got selected) stops instead of overwriting newer
     * data, and one which outlived the tool window does not touch it any more.
     */
    private void loadData() {
        comments = Collections.emptyMap();
        drafts = Collections.emptyMap();

        final long generation = ++loadGeneration; // only written here, and this runs on the event dispatch thread

        final ChangeInfo change = selectedChange;
        final String revisionId = change == null ? null : selectedRevisions.get(change);
        // also forgets the reviewed files of what was selected before
        reviewedFiles.startLoading(change, revisionId);
        if (change == null || revisionId == null) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                if (isObsolete(generation)) {
                    return;
                }
                final Map<String, List<CommentInfo>> loadedComments = loadComments(change, revisionId);
                if (isObsolete(generation)) {
                    return;
                }
                final Map<String, List<CommentInfo>> loadedDrafts = loadDrafts(change, revisionId);
                if (isObsolete(generation)) {
                    return;
                }
                final Set<String> loadedReviewed = loadReviewed(change, revisionId); // null: not loaded
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (isObsolete(generation)) {
                            return;
                        }
                        comments = loadedComments;
                        drafts = loadedDrafts;
                        if (loadedReviewed != null) {
                            reviewedFiles.loaded(change, revisionId, loadedReviewed);
                        } else {
                            reviewedFiles.loadFailed(change, revisionId);
                        }
                        if (dataLoadedCallback != null) {
                            dataLoadedCallback.run();
                        }
                    }
                });
            }
        });
    }

    private boolean isObsolete(long generation) {
        return generation != loadGeneration || project.isDisposed() || disposed;
    }

    private String getAffectedFilePath(Change change) {
        ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null) {
            return afterRevision.getFile().getPath();
        }
        ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null) {
            return beforeRevision.getFile().getPath();
        }
        return null;
    }

    private String getFileName(Project project, String affectedFilePath) {
        return PathUtils.ensureSlashSeparators(getRelativeOrAbsolutePath(project, affectedFilePath));
    }

    private String getNodeSuffix(String fileName) {
        List<String> parts = new ArrayList<>();

        List<CommentInfo> commentsForFile = comments.get(fileName);
        if (commentsForFile != null) {
            parts.add(String.format("%s comment%s", commentsForFile.size(), commentsForFile.size() == 1 ? "" : "s"));
        }

        List<CommentInfo> draftsForFile = drafts.get(fileName);
        if (draftsForFile != null) {
            parts.add(String.format("%s draft%s", draftsForFile.size(), draftsForFile.size() == 1 ? "" : "s"));
        }

        return String.join(", ", parts);
    }

    private String getRelativeOrAbsolutePath(Project project, String absoluteFilePath) {
        if (selectedRepository == null) {
            selectedRepository = GerritGitUtil.getInstance().getRepositoryForChange(project, selectedChange);
        }
        return PathUtils.getRelativeOrAbsolutePath(selectedRepository, absoluteFilePath);
    }

    private Map<String, List<CommentInfo>> loadComments(ChangeInfo change, String revisionId) {
        try {
            return GerritApiProvider.getInstance().get(GerritProjectAccount.getInstance(project).get()).changes()
                    .id(change.id)
                    .revision(revisionId)
                    .comments();
        } catch (RestApiException e) {
            LOG.warn(e);
            return Collections.emptyMap();
        }
    }

    private Map<String, List<CommentInfo>> loadDrafts(ChangeInfo change, String revisionId) {
        if (!GerritProjectAccount.getInstance(project).isLoginAndPasswordAvailable()) {
            return Collections.emptyMap();
        }
        try {
            return GerritApiProvider.getInstance().get(GerritProjectAccount.getInstance(project).get()).changes()
                    .id(change.id)
                    .revision(revisionId)
                    .drafts();
        } catch (RestApiException e) {
            LOG.warn(e);
            return Collections.emptyMap();
        }
    }

    private Set<String> loadReviewed(ChangeInfo change, String revisionId) {
        if (!GerritProjectAccount.getInstance(project).isLoginAndPasswordAvailable()) {
            return null;
        }
        try {
            return GerritApiProvider.getInstance().get(GerritProjectAccount.getInstance(project).get()).changes()
                    .id(change.id)
                    .revision(revisionId)
                    .reviewed();
        } catch (RestApiException e) {
            LOG.warn(e);
            return null;
        }
    }
}
