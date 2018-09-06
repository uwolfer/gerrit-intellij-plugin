/*
 *
 *  * Copyright 2013-2014 Urs Wolfer
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;
import git4idea.GitCommit;
import git4idea.changes.GitChangeUtils;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * This class diffs commits based in IntelliJ git4idea code and adds support for diffing commit msg.
 *
 * @author Thomas Forrer
 */
public class CommitDiffBuilder {

    private static final Predicate<Change> COMMIT_MSG_CHANGE_PREDICATE = change -> {
        String commitMsgFile = "/COMMIT_MSG";
        ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null) {
            return commitMsgFile.equals(PathUtils.ensureSlashSeparators(afterRevision.getFile().getPath()));
        }
        ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null) {
            return commitMsgFile.equals(PathUtils.ensureSlashSeparators(beforeRevision.getFile().getPath()));
        }
        throw new IllegalStateException("Change should have at least one ContentRevision set.");
    };

    private final Project project;
    private final VirtualFile gitRepositoryRoot;
    private final GitCommit base;
    private final GitCommit commit;
    private ChangesProvider changesProvider = new SimpleChangesProvider();

    public CommitDiffBuilder(Project project, VirtualFile gitRepositoryRoot, GitCommit base, GitCommit commit) {
        this.project = project;
        this.gitRepositoryRoot = gitRepositoryRoot;
        this.base = base;
        this.commit = commit;
    }

    public CommitDiffBuilder withChangesProvider(ChangesProvider changesProvider) {
        this.changesProvider = changesProvider;
        return this;
    }

    public Collection<Change> getDiff() throws VcsException {
        String baseHash = base.getId().asString();
        String hash = commit.getId().asString();
        Collection<Change> result = GitChangeUtils.getDiff(project, gitRepositoryRoot, baseHash, hash, null);
        result.add(buildCommitMsgChange());
        return result;
    }

    private Change buildCommitMsgChange() {
        Change baseChange = changesProvider.provide(base).stream().filter(COMMIT_MSG_CHANGE_PREDICATE).findFirst().get();
        ContentRevision baseRevision = baseChange.getAfterRevision();
        Change change = changesProvider.provide(commit).stream().filter(COMMIT_MSG_CHANGE_PREDICATE).findFirst().get();
        ContentRevision revision = change.getAfterRevision();
        return new Change(baseRevision, revision);
    }

    public interface ChangesProvider {
        Collection<Change> provide(GitCommit gitCommit);
    }

    private static final class SimpleChangesProvider implements ChangesProvider {
        @Override
        public Collection<Change> provide(GitCommit gitCommit) {
            return gitCommit.getChanges();
        }
    }
}
