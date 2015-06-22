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

package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import git4idea.GitCommit;
import git4idea.repo.GitRepository;

import java.util.Collection;

/**
 * @author Thomas Forrer
 */
public class ChangesWithCommitMessageProvider implements CommitDiffBuilder.ChangesProvider {

    private final GerritGitUtil gerritGitUtil;

    private final Project project;
    private final ChangeInfo selectedChange;

    public ChangesWithCommitMessageProvider(GerritGitUtil gerritGitUtil, Project project, ChangeInfo changeInfo) {
        this.gerritGitUtil = gerritGitUtil;
        this.project = project;
        selectedChange = changeInfo;
    }

    @Override
    public Collection<Change> provide(GitCommit gitCommit) {
        return getChangesWithCommitMessage(gitCommit);
    }

    private Collection<Change> getChangesWithCommitMessage(GitCommit gitCommit) {
        Collection<Change> changes = gitCommit.getChanges();

        String content = new CommitMessageFormatter(gitCommit).getLongCommitMessage();
        GitRepository repository = gerritGitUtil.getRepositoryForGerritProject(project, selectedChange.project).get();
        VirtualFile root = getRootDirectory(repository.getRoot());
        LocalFilePath commitMsg = new LocalFilePath(root.getPath() + "/COMMIT_MSG", false) {
            @Override
            public FileType getFileType() {
                return PlainTextFileType.INSTANCE;
            }
        };

        changes.add(new Change(null, new SimpleContentRevision(
                content,
                commitMsg,
                gitCommit.getId().asString()
        )));
        return changes;
    }

    private VirtualFile getRootDirectory(VirtualFile file) {
        VirtualFile parent = file.getParent();
        while (parent != null) {
            file = parent;
            parent = file.getParent();
        }
        return file;
    }
}
