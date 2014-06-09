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
import com.google.gerrit.extensions.common.Comment;
import com.urswolfer.intellij.plugin.gerrit.util.CommentHelper;

import java.util.Collection;
import java.util.Observable;

/**
 * This is a holder for new comments which got added locally. They get removed again once they are submitted.
 *
 * @author Urs Wolfer
 */
public class ReviewCommentSink extends Observable {
    private Multimap<String, CommentHelper> comments = ArrayListMultimap.create();

    public void addComment(String changeId, ReviewInput.CommentInput comment) {
        comments.put(changeId, new CommentHelper(comment));
        update();
    }

    public Iterable<ReviewInput.CommentInput> getCommentsForChange(String changeId) {
        Collection<CommentHelper> comments = this.comments.get(changeId);
        return Iterables.transform(comments, new Function<CommentHelper, ReviewInput.CommentInput>() {
            @Override
            public ReviewInput.CommentInput apply(CommentHelper commentHelper) {
                return (ReviewInput.CommentInput) commentHelper.getComment();
            }
        });
    }

    public void removeCommentForChange(String changeId, Comment commentInput) {
        boolean removed = comments.get(changeId).remove(new CommentHelper(commentInput));
        if (removed) {
            update();
        }
    }

    public void removeCommentsForChange(String changeId) {
        comments.removeAll(changeId);
    }

    private void update() {
        setChanged();
        notifyObservers();
    }
}
