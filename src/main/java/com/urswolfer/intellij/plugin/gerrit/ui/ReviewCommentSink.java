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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This is a holder for new comments which got added locally. They get removed again once they are submitted.
 *
 * @author Urs Wolfer
 */
public class ReviewCommentSink {

    private Map<String, List<CommentInput>> comments = Maps.newHashMap();

    public Map<String, List<CommentInput>> getComments() {
        return comments;
    }

    public void addComment(String changeId, CommentInput comment) {
        List<CommentInput> commentInputs;
        if (comments.containsKey(changeId)) {
            commentInputs = comments.get(changeId);
        } else {
            commentInputs = Lists.newArrayList();
            comments.put(changeId, commentInputs);
        }
        commentInputs.add(comment);
    }

    public List<CommentInput> getCommentsForChange(String changeId) {
        if (comments.containsKey(changeId)) {
            return comments.get(changeId);
        } else {
            return Collections.emptyList();
        }
    }
}
