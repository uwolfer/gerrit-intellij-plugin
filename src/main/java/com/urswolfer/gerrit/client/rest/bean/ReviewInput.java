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

package com.urswolfer.gerrit.client.rest.bean;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Urs Wolfer
 */
public class ReviewInput {
    private String message;
    private Map<String, Integer> labels = new TreeMap<String, Integer>();
    private Map<String, List<CommentInput>> comments = new TreeMap<String, List<CommentInput>>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Integer> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, Integer> labels) {
        this.labels = labels;
    }

    public void addLabel(String key, Integer label) {
        labels.put(key, label);
    }

    public Map<String, List<CommentInput>> getComments() {
        return comments;
    }

    public void setComments(Map<String, List<CommentInput>> comments) {
        this.comments = comments;
    }

    public void addComment(String key, CommentInput comment) {
        List<CommentInput> commentInputs;
        if (comments.containsKey(key)) {
            commentInputs = comments.get(key);
        } else {
            commentInputs = Lists.newArrayList();
            comments.put(key, commentInputs);
        }
        commentInputs.add(comment);
    }
}
