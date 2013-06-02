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

import java.util.TreeMap;

import com.google.gson.annotations.SerializedName;

/**
 * @author Urs Wolfer
 */
public class RevisionInfo {
    private boolean draft;
    @SerializedName("_number")
    private String number;

    private TreeMap<String, FetchInfo> fetch = new TreeMap<String, FetchInfo>();

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public TreeMap<String, FetchInfo> getFetch() {
        return fetch;
    }

    public void setFetch(TreeMap<String, FetchInfo> fetch) {
        this.fetch = fetch;
    }
}
