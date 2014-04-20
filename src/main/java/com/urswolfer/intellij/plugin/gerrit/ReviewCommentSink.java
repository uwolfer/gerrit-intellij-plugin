/*
 * Copyright 2013 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.urswolfer.intellij.plugin.gerrit.util.CommentHelper;

import java.util.Collection;

/**
 * This is a holder for new comments which got added locally. They get removed again once they are submitted.
 *
 * @author Urs Wolfer
 */
public class ReviewCommentSink {

    private Multimap<String, CommentHelper> comments = ArrayListMultimap.create();

    public void addComment(String changeId, ReviewInput.Comment comment) {
        comments.put(changeId, new CommentHelper(comment));
    }

    public Iterable<ReviewInput.Comment> getCommentsForChange(String changeId) {
        Collection<CommentHelper> comments = this.comments.get(changeId);
        return Iterables.transform(comments, new Function<CommentHelper, ReviewInput.Comment>() {
            @Override
            public ReviewInput.Comment apply(CommentHelper commentHelper) {
                return commentHelper.getComment();
            }
        });
    }

    public void removeCommentForChange(String changeId, ReviewInput.Comment commentInput) {
        comments.get(changeId).remove(new CommentHelper(commentInput));
    }

    public void removeCommentsForChange(String changeId) {
        comments.removeAll(changeId);
    }
}
