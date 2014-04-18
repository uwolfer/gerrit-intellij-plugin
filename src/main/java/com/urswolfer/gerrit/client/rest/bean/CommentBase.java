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

import com.google.gson.annotations.SerializedName;

import java.util.Date;

/**
 * @author Urs Wolfer
 */
public abstract class CommentBase {

    public static enum CommentSide {
        PARENT, REVISION
    }

    protected String kind = "gerritcodereview#comment";
    protected String id;
    protected String path;
    protected CommentSide side = CommentSide.REVISION; // rest api only sends PARENT; REVISION is default
    protected int line;
    @SerializedName("in_reply_to")
    protected String inReplyTo;
    protected String message;
    protected Date updated;

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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public CommentSide getSide() {
        return side;
    }

    public void setSide(CommentSide side) {
        this.side = side;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getInReplyTo() {
        return inReplyTo;
    }

    public void setInReplyTo(String inReplyTo) {
        this.inReplyTo = inReplyTo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommentBase)) return false;

        CommentBase that = (CommentBase) o;

        if (line != that.line) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (inReplyTo != null ? !inReplyTo.equals(that.inReplyTo) : that.inReplyTo != null) return false;
        if (kind != null ? !kind.equals(that.kind) : that.kind != null) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (path != null ? !path.equals(that.path) : that.path != null) return false;
        if (side != null ? !side.equals(that.side) : that.side != null) return false;
        if (updated != null ? !updated.equals(that.updated) : that.updated != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = kind != null ? kind.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (side != null ? side.hashCode() : 0);
        result = 31 * result + line;
        result = 31 * result + (inReplyTo != null ? inReplyTo.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (updated != null ? updated.hashCode() : 0);
        return result;
    }
}
