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

package com.urswolfer.intellij.plugin.gerrit.rest.bean;

import java.util.Date;
import java.util.TreeMap;

import com.google.gson.annotations.SerializedName;

/**
 * @author Urs Wolfer
 */
public class ChangeInfo {
    private String kind;
    private String id;
    private String project;
    private String branch;
    @SerializedName("change_id")
    private String changeId;
    private String subject;
    private String status;
    private Date created;
    private Date updated;
    private boolean mergeable;
    @SerializedName("_sortkey")
    private String sortKey;
    @SerializedName("_number")
    private String number;
    private AccountInfo owner;

    @SerializedName("current_revision")
    private String currentRevision;

    private TreeMap<String, RevisionInfo> revisions = new TreeMap<String, RevisionInfo>();

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public boolean isMergeable() {
        return mergeable;
    }

    public void setMergeable(boolean mergeable) {
        this.mergeable = mergeable;
    }

    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public AccountInfo getOwner() {
        return owner;
    }

    public void setOwner(AccountInfo owner) {
        this.owner = owner;
    }

    public String getCurrentRevision() {
        return currentRevision;
    }

    public void setCurrentRevision(String currentRevision) {
        this.currentRevision = currentRevision;
    }

    public TreeMap<String, RevisionInfo> getRevisions() {
        return revisions;
    }

    public void setRevisions(TreeMap<String, RevisionInfo> revisions) {
        this.revisions = revisions;
    }

    @Override
    public String toString() {
        return "ChangeInfo{" +
                "kind='" + kind + '\'' +
                ", id='" + id + '\'' +
                ", project='" + project + '\'' +
                ", branch='" + branch + '\'' +
                ", changeId='" + changeId + '\'' +
                ", subject='" + subject + '\'' +
                ", status='" + status + '\'' +
                ", created='" + created + '\'' +
                ", updated='" + updated + '\'' +
                ", mergeable=" + mergeable +
                ", sortKey='" + sortKey + '\'' +
                ", number='" + number + '\'' +
                ", owner=" + owner +
                '}';
    }
}
