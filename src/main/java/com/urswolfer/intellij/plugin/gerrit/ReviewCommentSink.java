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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;

/**
 * This is a holder for new comments which got added locally. They get removed again once they are submitted.
 *
 * @author Urs Wolfer
 */
public class ReviewCommentSink {

    private Multimap<String, CommentInput> comments = ArrayListMultimap.create();

    public void addComment(String changeId, CommentInput comment) {
        comments.put(changeId, comment);
    }

    public Iterable<CommentInput> getCommentsForChange(String changeId) {
        return comments.get(changeId);
    }

    public void removeCommentForChange(String changeId, CommentInput commentInput) {
        comments.get(changeId).remove(commentInput);
    }

    public void removeCommentsForChange(String changeId) {
        comments.removeAll(changeId);
    }
}
