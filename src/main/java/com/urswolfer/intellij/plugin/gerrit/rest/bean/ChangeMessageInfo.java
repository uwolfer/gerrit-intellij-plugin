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

import com.google.gson.annotations.SerializedName;

/**
 * @author Urs Wolfer
 */
public class ChangeMessageInfo {
    private String id;
    private AccountInfo author;
    private String message;
    @SerializedName("_revision_number")
    private String revisionNumber;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AccountInfo getAuthor() {
        return author;
    }

    public void setAuthor(AccountInfo author) {
        this.author = author;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(String revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChangeMessageInfo that = (ChangeMessageInfo) o;

        if (!id.equals(that.id)) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (author != null ? !author.equals(that.author) : that.author != null) return false;
        if (revisionNumber != null ? !revisionNumber.equals(that.revisionNumber) : that.revisionNumber != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (author != null ? author.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (revisionNumber != null ? revisionNumber.hashCode() : 0);
        return result;
    }
}
