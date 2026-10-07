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

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A draft comment of the patch set a review publishes, with its path: Gerrit leaves the path out of the comments it
 * returns keyed by path.
 */
public final class DraftComment {
    static final String PATCHSET_LEVEL = "/PATCHSET_LEVEL";
    static final String COMMIT_MSG = "/COMMIT_MSG";
    static final String MERGE_LIST = "/MERGE_LIST";

    private static final Comparator<DraftComment> ORDER = Comparator
        .comparingInt((DraftComment draft) -> pathRank(draft.path))
        .thenComparing(draft -> draft.path)
        .thenComparing(draft -> draft.comment.line, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(draft -> draft.comment.side == Side.PARENT ? 0 : 1);

    private final String path;
    private final CommentInfo comment;

    DraftComment(String path, CommentInfo comment) {
        this.path = path;
        this.comment = comment;
    }

    /**
     * In the order Gerrit's reply dialog lists them: the comments on the patch set and on its magic files first, then
     * the files by path, each from its top.
     */
    public static List<DraftComment> sorted(Map<String, List<CommentInfo>> drafts) {
        List<DraftComment> result = new ArrayList<>();
        for (Map.Entry<String, List<CommentInfo>> entry : drafts.entrySet()) {
            for (CommentInfo comment : entry.getValue()) {
                result.add(new DraftComment(entry.getKey(), comment));
            }
        }
        result.sort(ORDER);
        return result;
    }

    private static int pathRank(String path) {
        switch (path) {
            case PATCHSET_LEVEL:
                return 0;
            case COMMIT_MSG:
                return 1;
            case MERGE_LIST:
                return 2;
            default:
                return 3;
        }
    }

    public String getPath() {
        return path;
    }

    public CommentInfo getComment() {
        return comment;
    }

    public DraftComment withComment(CommentInfo updated) {
        return new DraftComment(path, updated);
    }

    /**
     * Everything that places the comment, which an update without it would move: Gerrit replaces the draft with the
     * input rather than merging the two.
     */
    public DraftInput toDraftInput(String message) {
        DraftInput input = new DraftInput();
        input.id = comment.id;
        input.path = path;
        input.side = comment.side;
        input.parent = comment.parent;
        input.line = comment.line;
        input.range = comment.range;
        input.inReplyTo = comment.inReplyTo;
        input.unresolved = comment.unresolved;
        input.message = message;
        return input;
    }
}
