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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Names the local branch a change is checked out to: {@code review/<owner>/[<topic>-]<change>/<patch set>}.
 */
final class ReviewBranchName {
    // stop after 100 tries because most probably something went wrong
    private static final int MAX_SUFFIX = 100;

    // characters git check-ref-format rejects, plus '/' so a topic cannot open nested directories
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\x00-\\x20\\x7f~^:?*\\[\\\\/{]");
    private static final Pattern DOT_RUN = Pattern.compile("\\.{2,}");

    private ReviewBranchName() {
    }

    static String build(ChangeInfo change, int patchSet) {
        String name = change.topic == null || change.topic.trim().isEmpty()
                ? Integer.toString(change._number)
                : sanitize(change.topic) + '-' + change._number;
        return "review/" + sanitize(ownerName(change.owner).toLowerCase(Locale.ROOT)) + '/' + name + '/' + patchSet;
    }

    /**
     * Picks the branch to check out: {@code base} or the first {@code base_<n>} that either already points at
     * {@code commitHash} or does not exist yet. A branch at another commit may carry local work, so it is skipped.
     *
     * @param headOf the commit hash of an existing local branch, {@code null} if there is no such branch
     * @return {@code null} if no name is usable
     */
    @Nullable
    static Target resolve(String base, String commitHash,
                          Function<String, String> headOf, Predicate<String> canCreate) {
        for (int i = 0; i < MAX_SUFFIX; i++) {
            String name = i == 0 ? base : base + '_' + i;
            String head = headOf.apply(name);
            if (head != null) {
                if (head.equals(commitHash)) {
                    return new Target(name, true);
                }
            } else if (canCreate.test(name)) {
                return new Target(name, false);
            }
        }
        return null;
    }

    /**
     * Branches created before the patch set became a path segment were named {@code review/<owner>/<change>}.
     * Git cannot keep such a branch next to {@code review/<owner>/<change>/<patch set>}.
     */
    @Nullable
    static String blockingBranch(String base, Predicate<String> exists) {
        String parent = base;
        int separator;
        while ((separator = parent.lastIndexOf('/')) > 0) {
            parent = parent.substring(0, separator);
            if (exists.test(parent)) {
                return parent;
            }
        }
        return null;
    }

    private static String ownerName(@Nullable AccountInfo owner) {
        if (owner == null) {
            return "unknown";
        }
        if (owner.name != null && !owner.name.trim().isEmpty()) {
            return owner.name;
        }
        if (owner.username != null && !owner.username.trim().isEmpty()) {
            return owner.username;
        }
        return String.valueOf(owner._accountId);
    }

    private static String sanitize(String segment) {
        String result = INVALID_CHARS.matcher(segment.trim()).replaceAll("_");
        result = DOT_RUN.matcher(result).replaceAll("_");
        if (result.startsWith(".")) {
            result = '_' + result.substring(1);
        }
        if (result.endsWith(".lock")) {
            result = result.substring(0, result.length() - ".lock".length()) + "_lock";
        }
        if (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1) + '_';
        }
        return result.isEmpty() ? "_" : result;
    }

    static final class Target {
        final String name;
        final boolean exists;

        Target(String name, boolean exists) {
            this.name = name;
            this.exists = exists;
        }
    }
}
