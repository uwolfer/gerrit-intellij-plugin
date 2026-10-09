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

package com.urswolfer.intellij.plugin.gerrit.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The files of one patch set which the user has reviewed, as far as Gerrit has told. It follows the change and
 * patch set selected in the change list, and refuses what belongs to any other. Not thread safe: used on the event
 * dispatch thread only.
 */
final class ReviewedFiles {
    private String changeId;
    private String revisionId;
    private Set<String> files = new HashSet<>();
    private boolean loading;
    /** What was marked while the files were loading; the answer may be older than the mark. */
    private final Map<String, Boolean> markedWhileLoading = new HashMap<>();

    /**
     * Starts loading the files of a patch set. The ones of the patch set shown so far stay while it is the same one,
     * so that a reload does not take the ticks away.
     */
    void startLoading(String changeId, String revisionId) {
        if (!isSelected(changeId, revisionId)) {
            this.changeId = changeId;
            this.revisionId = revisionId;
            files = new HashSet<>();
        }
        loading = true;
        markedWhileLoading.clear();
    }

    void clear() {
        changeId = null;
        revisionId = null;
        files = new HashSet<>();
        loading = false;
        markedWhileLoading.clear();
    }

    boolean isSelected(String changeId, String revisionId) {
        return changeId != null && revisionId != null
            && Objects.equals(this.changeId, changeId) && Objects.equals(this.revisionId, revisionId);
    }

    void loadFailed(String changeId, String revisionId) {
        if (isSelected(changeId, revisionId)) {
            loading = false;
            markedWhileLoading.clear();
        }
    }

    /**
     * @return whether the files were taken, which they are only for the patch set being loaded
     */
    boolean loaded(String changeId, String revisionId, Set<String> reviewed) {
        if (!isSelected(changeId, revisionId) || !loading) {
            return false;
        }
        files = new HashSet<>(reviewed);
        markedWhileLoading.forEach((path, marked) -> {
            if (marked) {
                files.add(path);
            } else {
                files.remove(path);
            }
        });
        markedWhileLoading.clear();
        loading = false;
        return true;
    }

    /**
     * @return whether the mark was taken, which it is only for the selected patch set
     */
    boolean mark(String changeId, String revisionId, String path, boolean reviewed) {
        if (!isSelected(changeId, revisionId)) {
            return false;
        }
        if (reviewed) {
            files.add(path);
        } else {
            files.remove(path);
        }
        if (loading) {
            markedWhileLoading.put(path, reviewed);
        }
        return true;
    }

    boolean isReviewed(String path) {
        return files.contains(path);
    }

    /**
     * What a toggle does to these files: it marks them reviewed, unless all of them are already, which is when it
     * marks them not reviewed.
     */
    boolean toggleTarget(Collection<String> paths) {
        return !paths.stream().allMatch(this::isReviewed);
    }
}
