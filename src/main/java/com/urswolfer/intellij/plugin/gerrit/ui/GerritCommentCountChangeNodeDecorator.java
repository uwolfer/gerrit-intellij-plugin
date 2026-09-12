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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;

import java.util.*;

/**
 * @author Thomas Forrer
 */
public class GerritCommentCountChangeNodeDecorator implements GerritChangeNodeDecorator {
    private static final Logger LOG = Logger.getInstance(GerritCommentCountChangeNodeDecorator.class);

    private static final Joiner SUFFIX_JOINER = Joiner.on(", ").skipNulls();

    private final GerritSettings gerritSettings = GerritSettings.getInstance();

    private final SelectedRevisions selectedRevisions;

    private ChangeInfo selectedChange;

    /** Loaded by {@link #loadData()}; only read and written on the event dispatch thread. */
    private Map<String, List<CommentInfo>> comments = Collections.emptyMap();
    private Map<String, List<CommentInfo>> drafts = Collections.emptyMap();
    private Set<String> reviewed = Collections.emptySet();

    private Runnable dataLoadedCallback;

    public GerritCommentCountChangeNodeDecorator(Project project, Disposable parent) {
        this.selectedRevisions = SelectedRevisions.getInstance(project);
        this.selectedRevisions.addListener(new SelectedRevisions.Listener() {
            @Override
            public void selectedRevisionChanged(String changeId) {
                if (changeId != null && selectedChange != null && selectedChange.id.equals(changeId)) {
                    loadData();
                }
            }
        }, parent);
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
            String text = getNodeSuffix(project, affectedFilePath);
            if (!Strings.isNullOrEmpty(text)) {
                component.append(String.format(" (%s)", text), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
                component.repaint();
            }
        }
    }

    @Override
    public void onChangeSelected(Project project, ChangeInfo selectedChange) {
        this.selectedChange = selectedChange;
        loadData();
    }

    /**
     * Loads the comments, drafts and reviewed files of the selected change in the background.
     */
    private void loadData() {
        comments = Collections.emptyMap();
        drafts = Collections.emptyMap();
        reviewed = Collections.emptySet();

        final ChangeInfo change = selectedChange;
        if (change == null) {
            return;
        }
        final String revisionId = selectedRevisions.get(change);
        if (revisionId == null) {
            return;
        }
        gerritSettings.preloadPassword(); // the password cannot be read from the background thread below

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                final Map<String, List<CommentInfo>> loadedComments = loadComments(change, revisionId);
                final Map<String, List<CommentInfo>> loadedDrafts = loadDrafts(change, revisionId);
                final Set<String> loadedReviewed = loadReviewed(change, revisionId);
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (change != selectedChange) { // another change has been selected in the meantime
                            return;
                        }
                        comments = loadedComments;
                        drafts = loadedDrafts;
                        reviewed = loadedReviewed;
                        if (dataLoadedCallback != null) {
                            dataLoadedCallback.run();
                        }
                    }
                });
            }
        });
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

    private String getNodeSuffix(Project project, String affectedFilePath) {
        String fileName = getRelativeOrAbsolutePath(project, affectedFilePath);
        fileName = PathUtils.ensureSlashSeparators(fileName);
        List<String> parts = Lists.newArrayList();

        List<CommentInfo> commentsForFile = comments.get(fileName);
        if (commentsForFile != null) {
            parts.add(String.format("%s comment%s", commentsForFile.size(), commentsForFile.size() == 1 ? "" : "s"));
        }

        List<CommentInfo> draftsForFile = drafts.get(fileName);
        if (draftsForFile != null) {
            parts.add(String.format("%s draft%s", draftsForFile.size(), draftsForFile.size() == 1 ? "" : "s"));
        }

        if (reviewed.contains(fileName)) {
            parts.add("reviewed");
        }

        return SUFFIX_JOINER.join(parts);
    }

    private String getRelativeOrAbsolutePath(Project project, String absoluteFilePath) {
        return PathUtils.getRelativeOrAbsolutePath(project, absoluteFilePath, selectedChange.project);
    }

    private Map<String, List<CommentInfo>> loadComments(ChangeInfo change, String revisionId) {
        try {
            return GerritApiProvider.getInstance().get().changes()
                    .id(change.id)
                    .revision(revisionId)
                    .comments();
        } catch (RestApiException e) {
            LOG.warn(e);
            return Collections.emptyMap();
        }
    }

    private Map<String, List<CommentInfo>> loadDrafts(ChangeInfo change, String revisionId) {
        if (!gerritSettings.isLoginAndPasswordAvailable()) {
            return Collections.emptyMap();
        }
        try {
            return GerritApiProvider.getInstance().get().changes()
                    .id(change.id)
                    .revision(revisionId)
                    .drafts();
        } catch (RestApiException e) {
            LOG.warn(e);
            return Collections.emptyMap();
        }
    }

    private Set<String> loadReviewed(ChangeInfo change, String revisionId) {
        if (!gerritSettings.isLoginAndPasswordAvailable()) {
            return Collections.emptySet();
        }
        try {
            return GerritApiProvider.getInstance().get().changes()
                    .id(change.id)
                    .revision(revisionId)
                    .reviewed();
        } catch (RestApiException e) {
            LOG.warn(e);
            return Collections.emptySet();
        }
    }
}
