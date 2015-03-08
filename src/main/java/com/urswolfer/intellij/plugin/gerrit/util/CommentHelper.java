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

package com.urswolfer.intellij.plugin.gerrit.util;

import com.google.gerrit.extensions.client.Comment;

/**
 * CommentInfo and ReviewInput.CommentInput do not provide equals and hashCode as required for map handling.
 *
 * @author Urs Wolfer
 */
public class CommentHelper {

    private final Comment comment;

    public CommentHelper(Comment comment) {
        this.comment = comment;
    }

    public Comment getComment() {
        return comment;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CommentHelper)) {
            return false;
        }
        return equals(comment, ((CommentHelper) obj).getComment());
    }

    @Override
    public int hashCode() {
        return hashCode(comment);
    }

    public static boolean equals(Comment comment1, Comment comment2) {
        if (comment1 == comment2) return true;
        if (comment2 == null || comment1.getClass() != comment2.getClass()) return false;

        Comment that = comment2;

        if (comment1.line != that.line) return false;
        if (comment1.id != null ? !comment1.id.equals(that.id) : that.id != null) return false;
        if (comment1.inReplyTo != null ? !comment1.inReplyTo.equals(that.inReplyTo) : that.inReplyTo != null) return false;
        if (comment1.message != null ? !comment1.message.equals(that.message) : that.message != null) return false;
        if (comment1.path != null ? !comment1.path.equals(that.path) : that.path != null) return false;
        if (comment1.side != null ? !comment1.side.equals(that.side) : that.side != null) return false;
        if (comment1.updated != null ? !comment1.updated.equals(that.updated) : that.updated != null) return false;

        return true;
    }

    public static int hashCode(Comment comment) {
        int result = 0;
        result = 31 * result + comment.id.hashCode();
        result = 31 * result + (comment.path != null ? comment.path.hashCode() : 0);
        result = 31 * result + (comment.side != null ? comment.side.hashCode() : 0);
        result = 31 * result + comment.line;
        result = 31 * result + (comment.inReplyTo != null ? comment.inReplyTo.hashCode() : 0);
        result = 31 * result + (comment.message != null ? comment.message.hashCode() : 0);
        result = 31 * result + (comment.updated != null ? comment.updated.hashCode() : 0);
        return result;
    }
}
