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

package com.urswolfer.gerrit.client.rest.http.common;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.*;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author Thomas Forrer
 */
public class ChangeInfoBuilder {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final ChangeInfo changeInfo = new ChangeInfo();

    private static Timestamp timestamp(String timestamp) {
        try {
            Date parse = DATE_FORMAT.parse(timestamp);
            return new Timestamp(parse.getTime());
        } catch (ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    public ChangeInfo get() {
        return changeInfo;
    }

    public ChangeInfoBuilder withId(String id) {
        changeInfo.id = id;
        return this;
    }

    public ChangeInfoBuilder withProject(String project) {
        changeInfo.project = project;
        return this;
    }

    public ChangeInfoBuilder withBranch(String branch) {
        changeInfo.branch = branch;
        return this;
    }

    public ChangeInfoBuilder withTopic(String topic) {
        changeInfo.topic = topic;
        return this;
    }

    public ChangeInfoBuilder withChangeId(String changeId) {
        changeInfo.changeId = changeId;
        return this;
    }

    public ChangeInfoBuilder withSubject(String subject) {
        changeInfo.subject = subject;
        return this;
    }

    public ChangeInfoBuilder withStatus(ChangeStatus status) {
        changeInfo.status = status;
        return this;
    }

    public ChangeInfoBuilder withCreated(Timestamp created) {
        changeInfo.created = created;
        return this;
    }

    public ChangeInfoBuilder withCreated(String created) {
        changeInfo.created = timestamp(created);
        return this;
    }

    public ChangeInfoBuilder withUpdated(Timestamp updated) {
        changeInfo.updated = updated;
        return this;
    }

    public ChangeInfoBuilder withUpdated(String updated) {
        changeInfo.updated = timestamp(updated);
        return this;
    }

    public ChangeInfoBuilder withStarred(Boolean starred) {
        changeInfo.starred = starred;
        return this;
    }

    public ChangeInfoBuilder withReviewed(Boolean reviewed) {
        changeInfo.reviewed = reviewed;
        return this;
    }

    public ChangeInfoBuilder withMergeable(Boolean mergeable) {
        changeInfo.mergeable = mergeable;
        return this;
    }

    public ChangeInfoBuilder withInsertions(Integer insertions) {
        changeInfo.insertions = insertions;
        return this;
    }

    public ChangeInfoBuilder withDeletions(Integer deletions) {
        changeInfo.deletions = deletions;
        return this;
    }

    public ChangeInfoBuilder withOwner(AccountInfo owner) {
        changeInfo.owner = owner;
        return this;
    }

    public ChangeInfoBuilder withCurrentRevision(String currentRevision) {
        changeInfo.currentRevision = currentRevision;
        return this;
    }

    public ChangeInfoBuilder withActions(Map<String, ActionInfo> actions) {
        changeInfo.actions = actions;
        return this;
    }

    public ChangeInfoBuilder withLabel(String key, LabelInfo label) {
        if (changeInfo.labels == null) {
            changeInfo.labels = Maps.newLinkedHashMap();
        }
        changeInfo.labels.put(key, label);
        return this;
    }

    public ChangeInfoBuilder withMessages(Collection<ChangeMessageInfo> messages) {
        changeInfo.messages = messages;
        return this;
    }

    public ChangeInfoBuilder withRevisions(Map<String, RevisionInfo> revisions) {
        changeInfo.revisions = revisions;
        return this;
    }

    public ChangeInfoBuilder withNumber(int number) {
        changeInfo._number = number;
        return this;
    }
}
