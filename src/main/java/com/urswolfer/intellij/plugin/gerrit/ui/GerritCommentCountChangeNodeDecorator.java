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
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;

import java.util.*;

/**
 * @author Thomas Forrer
 */
public class GerritCommentCountChangeNodeDecorator implements GerritChangeNodeDecorator {
    private static final Joiner SUFFIX_JOINER = Joiner.on(", ").skipNulls();

    @Inject
    private GerritRestApi gerritApi;
    @Inject
    private PathUtils pathUtils;
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private Logger log;

    private final SelectedRevisions selectedRevisions;

    private ChangeInfo selectedChange;
    private Supplier<Map<String, List<CommentInfo>>> comments = setupCommentsSupplier();
    private Supplier<Map<String, List<CommentInfo>>> drafts = setupDraftsSupplier();
    private Supplier<Set<String>> reviewed = setupReviewedSupplier();

    @Inject
    public GerritCommentCountChangeNodeDecorator(SelectedRevisions selectedRevisions) {
        this.selectedRevisions = selectedRevisions;
        this.selectedRevisions.addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                if (arg instanceof String && selectedChange != null && selectedChange.id.equals(arg)) {
                    refreshSuppliers();
                }
            }
        });
    }

    private void refreshSuppliers() {
        comments = setupCommentsSupplier();
        drafts = setupDraftsSupplier();
        reviewed = setupReviewedSupplier();
    }

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
        refreshSuppliers();
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

        Map<String, List<CommentInfo>> commentsMap = comments.get();
        List<CommentInfo> commentsForFile = commentsMap.get(fileName);
        if (commentsForFile != null) {
            parts.add(String.format("%s comment%s", commentsForFile.size(), commentsForFile.size() == 1 ? "" : "s"));
        }

        Map<String, List<CommentInfo>> draftsMap = drafts.get();
        List<CommentInfo> draftsForFile = draftsMap.get(fileName);
        if (draftsForFile != null) {
            parts.add(String.format("%s draft%s", draftsForFile.size(), draftsForFile.size() == 1 ? "" : "s"));
        }

        if (reviewed.get().contains(fileName)) {
            parts.add("reviewed");
        }

        return SUFFIX_JOINER.join(parts);
    }

    private String getRelativeOrAbsolutePath(Project project, String absoluteFilePath) {
        return pathUtils.getRelativeOrAbsolutePath(project, absoluteFilePath, selectedChange.project);
    }

    private Supplier<Map<String, List<CommentInfo>>> setupCommentsSupplier() {
        return Suppliers.memoize(new Supplier<Map<String, List<CommentInfo>>>() {
            @Override
            public Map<String, List<CommentInfo>> get() {
                try {
                    return gerritApi.changes()
                            .id(selectedChange.id)
                            .revision(getSelectedRevisionId())
                            .comments();
                } catch (RestApiException e) {
                    log.warn(e);
                    return Collections.emptyMap();
                }
            }
        });
    }

    private Supplier<Map<String, List<CommentInfo>>> setupDraftsSupplier() {
        return Suppliers.memoize(new Supplier<Map<String, List<CommentInfo>>>() {
            @Override
            public Map<String, List<CommentInfo>> get() {
                if (!gerritSettings.isLoginAndPasswordAvailable()) {
                    return Collections.emptyMap();
                }
                try {
                    return gerritApi.changes()
                            .id(selectedChange.id)
                            .revision(getSelectedRevisionId())
                            .drafts();
                } catch (RestApiException e) {
                    log.warn(e);
                    return Collections.emptyMap();
                }
            }
        });
    }

    private Supplier<Set<String>> setupReviewedSupplier() {
        return Suppliers.memoize(new Supplier<Set<String>>() {
            @Override
            public Set<String> get() {
                if (!gerritSettings.isLoginAndPasswordAvailable()) {
                    return Collections.emptySet();
                }
                try {
                    return gerritApi.changes()
                            .id(selectedChange.id)
                            .revision(getSelectedRevisionId())
                            .reviewed();
                } catch (RestApiException e) {
                    log.warn(e);
                    return Collections.emptySet();
                }
            }
        });
    }

    private String getSelectedRevisionId() {
        return selectedRevisions.get(selectedChange);
    }
}
