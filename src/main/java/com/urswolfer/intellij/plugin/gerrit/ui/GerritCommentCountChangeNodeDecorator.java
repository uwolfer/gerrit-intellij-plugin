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

import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import git4idea.repo.GitRepository;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Thomas Forrer
 */
public class GerritCommentCountChangeNodeDecorator implements GerritChangeNodeDecorator {
    private static final Joiner SUFFIX_JOINER = Joiner.on(", ").skipNulls();

    @Inject
    private GerritGitUtil gerritGitUtil;
    @Inject
    private ReviewCommentSink reviewCommentSink;
    @Inject
    private GerritApi gerritApi;

    private ChangeInfo selectedChange;
    private Supplier<Map<String, List<CommentInfo>>> comments = setupCommentsSupplier();

    @Override
    public void decorate(Project project, Change change, SimpleColoredComponent component, ChangeInfo selectedChange) {
        Map<String, List<CommentInfo>> commentsMap = comments.get();
        VirtualFile file = change.getVirtualFile();
        if (file != null) {
            String text = getNodeSuffix(project, selectedChange, commentsMap, file);
            if (!Strings.isNullOrEmpty(text)) {
                component.append(String.format(" (%s)", text), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
                component.repaint();
            }
        }
    }

    @Override
    public void onChangeSelected(Project project, ChangeInfo selectedChange) {
        this.selectedChange = selectedChange;
        comments = setupCommentsSupplier();
    }

    private String getNodeSuffix(Project project,
                                 ChangeInfo selectedChange,
                                 Map<String, List<CommentInfo>> commentsMap,
                                 VirtualFile file) {
        final String fileName = getRelativePath(project, file.getPath());
        List<CommentInfo> commentsForFile = commentsMap.get(fileName);
        Iterable<ReviewInput.CommentInput> drafts = getCommentsForFile(selectedChange, fileName);
        List<String> parts = Lists.newArrayList();
        if (commentsForFile != null) {
            parts.add(String.format("%s comment%s", commentsForFile.size(), commentsForFile.size() == 1 ? "" : "s"));
        }
        if (!Iterables.isEmpty(drafts)) {
            int numDrafts = Iterables.size(drafts);
            parts.add(String.format("%s draft%s", numDrafts, numDrafts == 1 ? "" : "s"));
        }
        return SUFFIX_JOINER.join(parts);
    }

    private Iterable<ReviewInput.CommentInput> getCommentsForFile(ChangeInfo selectedChange, final String fileName) {
        return Iterables.filter(reviewCommentSink.getCommentsForChange(selectedChange.id), new Predicate<ReviewInput.CommentInput>() {
            @Override
            public boolean apply(ReviewInput.CommentInput commentInput) {
                return commentInput.path.equals(fileName);
            }
        });
    }

    private String getRelativePath(Project project, String absoluteFilePath) {
        Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.getRepositoryForGerritProject(project, selectedChange.project);
        if (!gitRepositoryOptional.isPresent()) return null;
        GitRepository repository = gitRepositoryOptional.get();
        VirtualFile root = repository.getRoot();
        return FileUtil.getRelativePath(new File(root.getPath()), new File(absoluteFilePath));
    }

    private Supplier<Map<String, List<CommentInfo>>> setupCommentsSupplier() {
        return Suppliers.memoize(new Supplier<Map<String, List<CommentInfo>>>() {
            @Override
            public Map<String, List<CommentInfo>> get() {
                try {
                    return gerritApi.changes()
                            .id(selectedChange.id)
                            .revision(selectedChange.currentRevision)
                            .getComments();
                } catch (RestApiException e) {
                    throw Throwables.propagate(e);
                }
            }
        });
    }
}
