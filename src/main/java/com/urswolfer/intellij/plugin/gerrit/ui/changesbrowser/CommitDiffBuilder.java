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

import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import git4idea.GitCommit;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This class helps to get a list of {@link com.intellij.openapi.vcs.changes.Change}s between two
 * {@link git4idea.GitCommit}s.
 *
 * @author Thomas Forrer
 */
public class CommitDiffBuilder {
    private static final Function<Change, String> GET_CHANGED_FILE_PATH = new Function<Change, String>() {
        @Override
        public String apply(Change change) {
            ContentRevision afterRevision = change.getAfterRevision();
            if (afterRevision != null) {
                return afterRevision.getFile().getPath();
            }
            ContentRevision beforeRevision = change.getBeforeRevision();
            if (beforeRevision != null) {
                return beforeRevision.getFile().getPath();
            }
            throw new IllegalStateException("Change should have at least one ContentRevision set.");
        }
    };

    private static final Predicate<Change> CONTAINS_NO_CHANGE = new Predicate<Change>() {
        @Override
        public boolean apply(Change change) {
            ContentRevision base = change.getBeforeRevision();
            ContentRevision contentRevision = change.getAfterRevision();
            if (base == null && contentRevision == null) {
                return true;
            }
            if (base == null) return false;
            if (contentRevision == null) return false;
            try {
                String baseContent = Strings.nullToEmpty(base.getContent());
                return baseContent.equals(Strings.nullToEmpty(contentRevision.getContent()));
            } catch (VcsException e) {
                throw Throwables.propagate(e);
            }
        }
    };

    private final String baseHash;
    private final String hash;
    private final GitCommit base;
    private final GitCommit commit;
    private Map<String, Change> baseChanges;
    private Map<String, Change> changes;
    private final List<Change> diff = Lists.newArrayList();
    private ChangesProvider changesProvider = new SimpleChangesProvider();

    public CommitDiffBuilder(GitCommit base, GitCommit commit) {
        this.base = base;
        this.commit = commit;
        baseHash = base.getId().asString();
        hash = commit.getId().asString();
    }

    public CommitDiffBuilder withChangesProvider(ChangesProvider changesProvider) {
        this.changesProvider = changesProvider;
        return this;
    }

    public List<Change> getDiff() throws VcsException {
        baseChanges = Maps.uniqueIndex(changesProvider.provide(base), GET_CHANGED_FILE_PATH);
        changes = Maps.uniqueIndex(changesProvider.provide(commit), GET_CHANGED_FILE_PATH);

        addedFiles();
        changedFiles();
        removedFiles();
        return Lists.newArrayList(Iterables.filter(diff, Predicates.not(CONTAINS_NO_CHANGE)));
    }

    private void addedFiles() throws VcsException {
        Sets.SetView<String> addedFiles = Sets.difference(changes.keySet(), baseChanges.keySet());
        for (String addedFile : addedFiles) {
            Change change = changes.get(addedFile);
            ContentRevision beforeRevision = null;
            if (change.getType().equals(Change.Type.MODIFICATION)) {
                ContentRevision changeBeforeRevision = change.getBeforeRevision();
                assert changeBeforeRevision != null;
                beforeRevision = new SimpleContentRevision(
                        changeBeforeRevision.getContent(),
                        changeBeforeRevision.getFile(),
                        baseHash);
            }
            diff.add(new Change(beforeRevision, change.getAfterRevision()));
        }
    }

    private void changedFiles() {
        Sets.SetView<String> changedFiles = Sets.intersection(baseChanges.keySet(), changes.keySet());
        for (String changedFile : changedFiles) {
            Change baseChange = baseChanges.get(changedFile);
            ContentRevision baseRevision = baseChange.getAfterRevision();
            Change change = changes.get(changedFile);
            ContentRevision revision = change.getAfterRevision();
            if (baseRevision != null || revision != null) {
                diff.add(new Change(baseRevision, revision));
            }
        }
    }

    private void removedFiles() throws VcsException {
        Sets.SetView<String> removedFiles = Sets.difference(baseChanges.keySet(), changes.keySet());
        for (String removedFile : removedFiles) {
            Change baseChange = baseChanges.get(removedFile);
            ContentRevision afterRevision = null;
            if (baseChange.getType().equals(Change.Type.MODIFICATION)) {
                ContentRevision baseChangeBeforeRevision = baseChange.getBeforeRevision();
                assert baseChangeBeforeRevision != null;
                afterRevision = new SimpleContentRevision(
                        baseChangeBeforeRevision.getContent(),
                        baseChangeBeforeRevision.getFile(),
                        hash
                );
            }
            diff.add(new Change(baseChange.getAfterRevision(), afterRevision));
        }
    }

    public static interface ChangesProvider {
        Collection<Change> provide(GitCommit gitCommit);
    }

    private static final class SimpleChangesProvider implements ChangesProvider {
        @Override
        public Collection<Change> provide(GitCommit gitCommit) {
            return gitCommit.getChanges();
        }
    }
}
