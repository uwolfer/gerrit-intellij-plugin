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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Where Gerrit keeps the comments of one side of a diff: a file in a patch set, on the patch set itself or on its
 * parent.
 */
final class CommentSide {
    final String filePath;
    final String revisionId;
    final Side side;
    @Nullable
    final Integer parent;

    private CommentSide(String filePath, String revisionId, Side side, @Nullable Integer parent) {
        this.filePath = filePath;
        this.revisionId = revisionId;
        this.side = side;
        this.parent = parent;
    }

    static CommentSide onRevision(String filePath, String revisionId) {
        return new CommentSide(filePath, revisionId, Side.REVISION, null);
    }

    static CommentSide onParent(String filePath, String revisionId, @Nullable Integer parent) {
        return new CommentSide(filePath, revisionId, Side.PARENT, parent);
    }

    /**
     * Whether a comment which Gerrit lists for this file of the revision belongs here. On the parent side of a merge
     * commit, there is a comment on each parent and on the auto-merge, of which only those on the parent shown fit
     * its lines.
     */
    boolean shows(Comment comment) {
        return sideOf(comment.side) == side && (side == Side.REVISION || Objects.equals(comment.parent, parent));
    }

    /**
     * Whether the other is the same side in another diff, such as the one the diff window builds when it steps to
     * another file of the change and back.
     */
    boolean isSameAs(CommentSide other) {
        return revisionId.equals(other.revisionId) && filePath.equals(other.filePath) && side == other.side
            && Objects.equals(parent, other.parent);
    }

    static Side sideOf(@Nullable Side side) {
        return side != null ? side : Side.REVISION; // Gerrit leaves the side out of a comment on the revision
    }
}
