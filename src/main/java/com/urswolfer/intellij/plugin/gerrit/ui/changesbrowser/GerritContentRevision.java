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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RemoteFilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * A file of a patch set, or of its parent, which Gerrit is asked for once a diff shows it: the change's project has no
 * local repository. Its path is the one in the Gerrit project.
 */
final class GerritContentRevision implements ByteBackedContentRevision {
    private final Project project;
    private final int changeNr;
    private final String revisionId;
    @Nullable
    private final Integer parent;
    private final FilePath file;
    private final VcsRevisionNumber revisionNumber;
    private volatile byte[] content;

    private GerritContentRevision(Project project, int changeNr, String revisionId, @Nullable Integer parent,
                                  String path, String commit) {
        this.project = project;
        this.changeNr = changeNr;
        this.revisionId = revisionId;
        this.parent = parent;
        this.file = new RemoteFilePath(path, false);
        this.revisionNumber = new TextRevisionNumber(commit);
    }

    /**
     * @param parent the 1-based number of the parent of the patch set to read the file from, or null for the patch set
     * @param path   its path in the Gerrit project, or one of Gerrit's own files, such as /COMMIT_MSG
     * @param commit the commit the file is read from, which the diff shows as its revision
     */
    static ContentRevision create(Project project, int changeNr, String revisionId, @Nullable Integer parent,
                                  String path, String commit) {
        // Gerrit's own files are only ever added, so never read from a parent
        return path.startsWith("/")
            ? new GerritFileRevision(project, changeNr, revisionId, path, commit)
            : new GerritContentRevision(project, changeNr, revisionId, parent, path, commit);
    }

    @NotNull
    @Override
    public byte[] getContentAsBytes() throws VcsException {
        byte[] bytes = content;
        if (bytes == null) {
            try {
                bytes = GerritUtil.getInstance().getFileContent(changeNr, revisionId, file.getPath(), parent, project);
            } catch (RestApiException | IOException e) {
                throw new VcsException(e);
            }
            content = bytes;
        }
        return bytes;
    }

    @Nullable
    @Override
    public String getContent() throws VcsException {
        return ContentRevisionCache.getAsString(getContentAsBytes(), file, null);
    }

    @NotNull
    @Override
    public FilePath getFile() {
        return file;
    }

    @NotNull
    @Override
    public VcsRevisionNumber getRevisionNumber() {
        return revisionNumber;
    }

    /**
     * One of Gerrit's own files, such as /COMMIT_MSG, as Gerrit's diff shows it: comments on the commit message count
     * their lines after a header Gerrit adds, which its content leaves out. Text rather than bytes, which the diff would
     * decode with the charset of the project.
     */
    private static final class GerritFileRevision implements ContentRevision {
        private final Project project;
        private final int changeNr;
        private final String revisionId;
        private final FilePath file;
        private final VcsRevisionNumber revisionNumber;
        private volatile String content;

        GerritFileRevision(Project project, int changeNr, String revisionId, String path, String commit) {
            this.project = project;
            this.changeNr = changeNr;
            this.revisionId = revisionId;
            this.file = ChangesWithCommitMessageProvider.gerritFilePath(path);
            this.revisionNumber = new TextRevisionNumber(commit);
        }

        @NotNull
        @Override
        public String getContent() throws VcsException {
            String text = content;
            if (text == null) {
                try {
                    text = GerritUtil.getInstance().getDiffText(changeNr, revisionId, file.getPath(), project);
                } catch (RestApiException e) {
                    throw new VcsException(e);
                }
                content = text;
            }
            return text;
        }

        @NotNull
        @Override
        public FilePath getFile() {
            return file;
        }

        @NotNull
        @Override
        public VcsRevisionNumber getRevisionNumber() {
            return revisionNumber;
        }
    }
}
