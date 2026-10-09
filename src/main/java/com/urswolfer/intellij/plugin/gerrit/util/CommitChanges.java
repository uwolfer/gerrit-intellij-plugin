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

package com.urswolfer.intellij.plugin.gerrit.util;

import com.google.gerrit.extensions.common.ChangeInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the Gerrit changes which belong to commits.
 *
 * @author Urs Wolfer
 */
public final class CommitChanges {

    private static final Pattern COMMIT_HASH = Pattern.compile("[0-9a-f]{40}");

    private CommitChanges() {}

    /**
     * The hash alone finds a commit even after a submit which rebased or cherry-picked it: Gerrit records the commit
     * it merges as a patch set of the change.
     */
    public static String getQuery(Collection<String> hashes) {
        Set<String> terms = new LinkedHashSet<>();
        for (String hash : hashes) {
            terms.add("commit:" + hash);
        }
        return String.join(" OR ", terms);
    }

    /**
     * The revision of a line which is not committed yet is no hash, and neither is none at all.
     */
    public static boolean isCommitHash(@Nullable String revision) {
        return revision != null && COMMIT_HASH.matcher(revision).matches();
    }

    /**
     * The same commit can be a patch set of several changes, such as a cherry-pick to another branch of the project,
     * or of the same commit in another project. Gerrit lists the most recently updated first, which is the one
     * to show unless one belongs to the project of the repository.
     */
    @Nullable
    public static ChangeInfo pickChange(List<ChangeInfo> changes, String projectName) {
        for (ChangeInfo change : changes) {
            if (projectName.equals(change.project)) {
                return change;
            }
        }
        return changes.isEmpty() ? null : changes.get(0);
    }
}
