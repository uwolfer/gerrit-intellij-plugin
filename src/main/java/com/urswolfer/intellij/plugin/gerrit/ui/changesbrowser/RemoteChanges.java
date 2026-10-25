/*
 * Copyright 2026 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser;

import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The files of a patch set as Gerrit lists them, for a change whose project has no local repository. Their content
 * is read from Gerrit once a diff shows them.
 */
public final class RemoteChanges {
    public final List<Change> changes;
    /**
     * The parent of a merge commit the patch set is diffed against, null for any other diff.
     */
    @Nullable
    public final Integer parent;

    private RemoteChanges(List<Change> changes, @Nullable Integer parent) {
        this.changes = changes;
        this.parent = parent;
    }

    /**
     * Runs in the calling thread.
     *
     * @param baseRevisionId the patch set to diff against, or null for the parent
     */
    public static RemoteChanges load(Project project, int changeNr, String revisionId,
                                     @Nullable String baseRevisionId) throws RestApiException {
        GerritUtil gerritUtil = GerritUtil.getInstance();
        if (baseRevisionId != null) {
            Map<String, FileInfo> files = gerritUtil.getFiles(changeNr, revisionId, baseRevisionId, project);
            return new RemoteChanges(changes(files,
                path -> GerritContentRevision.create(project, changeNr, baseRevisionId, null, path, baseRevisionId),
                path -> GerritContentRevision.create(project, changeNr, revisionId, null, path, revisionId)), null);
        }
        CommitInfo commit = gerritUtil.getCommit(changeNr, revisionId, project);
        if (commit.parents == null || commit.parents.isEmpty()) {
            return new RemoteChanges(changes(gerritUtil.getRevisionFiles(changeNr, revisionId, project),
                path -> null,
                path -> GerritContentRevision.create(project, changeNr, revisionId, null, path, revisionId)), null);
        }
        // the first parent also of a merge, as the diff of a local repository shows, rather than Gerrit's auto-merge
        String parentCommit = commit.parents.get(0).commit;
        Map<String, FileInfo> files = gerritUtil.getFiles(changeNr, revisionId, 1, project);
        return new RemoteChanges(changes(files,
            path -> GerritContentRevision.create(project, changeNr, revisionId, 1, path, parentCommit),
            path -> GerritContentRevision.create(project, changeNr, revisionId, null, path, revisionId)),
            commit.parents.size() > 1 ? 1 : null);
    }

    private static List<Change> changes(Map<String, FileInfo> files, Function<String, ContentRevision> base,
                                        Function<String, ContentRevision> revision) {
        List<Change> changes = new ArrayList<>();
        for (Pair<String, String> paths : paths(files)) {
            ContentRevision before = paths.first != null ? base.apply(paths.first) : null;
            ContentRevision after = paths.second != null ? revision.apply(paths.second) : null;
            if (before != null || after != null) {
                changes.add(new Change(before, after));
            }
        }
        return changes;
    }

    /**
     * @return for each file, its path on the side diffed against, null where it is added, and its path in the patch
     * set, null where it is deleted
     */
    @VisibleForTesting
    static List<Pair<String, String>> paths(Map<String, FileInfo> files) {
        List<Pair<String, String>> paths = new ArrayList<>();
        for (Map.Entry<String, FileInfo> entry : files.entrySet()) {
            String path = entry.getKey();
            FileInfo file = entry.getValue();
            char status = file.status != null ? file.status : 'M';
            String basePath = (status == 'R' || status == 'C') && file.oldPath != null ? file.oldPath : path;
            paths.add(Pair.create(status == 'A' ? null : basePath, status == 'D' ? null : path));
        }
        return paths;
    }
}
