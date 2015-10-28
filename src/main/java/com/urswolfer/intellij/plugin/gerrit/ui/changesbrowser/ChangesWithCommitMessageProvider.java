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

package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import git4idea.history.browser.GitCommit;

import java.util.List;

/**
 * @author Thomas Forrer
 */
public class ChangesWithCommitMessageProvider implements CommitDiffBuilder.ChangesProvider {

    @Override
    public List<Change> provide(GitCommit gitCommit) {
        return getChangesWithCommitMessage(gitCommit);
    }

    private List<Change> getChangesWithCommitMessage(GitCommit gitCommit) {
        List<Change> changes = gitCommit.getChanges();

        String content = new CommitMessageFormatter(gitCommit).getLongCommitMessage();
        VirtualFile root = VirtualFileManager.getInstance().findFileByUrl("file:///");
        assert root != null;
        FilePathImpl commitMsg = new FilePathImpl(root, "COMMIT_MSG", false) {
            @Override
            public FileType getFileType() {
                return PlainTextFileType.INSTANCE;
            }
        };

        changes.add(new Change(null, new SimpleContentRevision(
                content,
                commitMsg,
                gitCommit.getHash().getValue()
        )));
        return changes;
    }
}
