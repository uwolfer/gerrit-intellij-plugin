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

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Between two patch sets of which one was rebased, the plain diff also lists every file the new parent changed.
 * Gerrit leaves out the files which differ only by what the rebase brought in. Its rules for that changed between
 * releases, from the files either patch set touches to the single edits of a file, and leave out merges; rather
 * than copying them, the diff keeps the files Gerrit lists, so that it shows what Gerrit's own UI shows.
 *
 * @author Urs Wolfer
 */
public final class RebaseFilter {

    private RebaseFilter() {
    }

    /**
     * @param listedPaths the paths Gerrit lists, relative to the repository and the old ones of renames included
     * @param repositoryRoot the path of the repository the changes are in
     */
    public static Predicate<Change> keepListed(Set<String> listedPaths, String repositoryRoot) {
        String prefix = repositoryRoot.endsWith("/") ? repositoryRoot : repositoryRoot + "/";
        return change -> isListed(change.getBeforeRevision(), listedPaths, prefix)
            || isListed(change.getAfterRevision(), listedPaths, prefix);
    }

    private static boolean isListed(ContentRevision revision, Set<String> listedPaths, String prefix) {
        if (revision == null) {
            return false;
        }
        String path = revision.getFile().getPath();
        // one which cannot be placed under the root cannot be looked up either, and is better shown than hidden
        return !path.startsWith(prefix) || listedPaths.contains(path.substring(prefix.length()));
    }
}
