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

package com.urswolfer.gerrit.client.rest.bean;

import java.util.List;

/**
 * @author Urs Wolfer
 */
public class LabelInfo {
    private AccountInfo approved;
    private AccountInfo rejected;
    private AccountInfo recommended;
    private AccountInfo disliked;
    private List<ApprovalInfo> all;
    private String value;

    public AccountInfo getApproved() {
        return approved;
    }

    public void setApproved(AccountInfo approved) {
        this.approved = approved;
    }

    public AccountInfo getRejected() {
        return rejected;
    }

    public void setRejected(AccountInfo rejected) {
        this.rejected = rejected;
    }

    public AccountInfo getRecommended() {
        return recommended;
    }

    public void setRecommended(AccountInfo recommended) {
        this.recommended = recommended;
    }

    public AccountInfo getDisliked() {
        return disliked;
    }

    public void setDisliked(AccountInfo disliked) {
        this.disliked = disliked;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public List<ApprovalInfo> getAll() {
        return all;
    }

    public void setAll(List<ApprovalInfo> all) {
        this.all = all;
    }

    @Override
    public String toString() {
        return "LabelInfo{" +
                "approved=" + approved +
                ", rejected=" + rejected +
                ", recommended=" + recommended +
                ", disliked=" + disliked +
                ", value='" + value + '\'' +
                '}';
    }
}
