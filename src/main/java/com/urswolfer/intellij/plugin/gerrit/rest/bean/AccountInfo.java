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

package com.urswolfer.intellij.plugin.gerrit.rest.bean;

import com.google.gson.annotations.SerializedName;

/**
 * @author Urs Wolfer
 */
public class AccountInfo {
    @SerializedName("_account_id")
    private String accountId;
    private String name;
    private String email;
    private String username;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "AccountInfo{" +
                "accountId='" + accountId + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", username='" + username + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountInfo)) return false;

        AccountInfo that = (AccountInfo) o;

        if (accountId != null && that.accountId != null) {
            if (!accountId.equals(that.accountId)) return false;
        }
        if (name != null && that.name != null) {
            if (!name.equals(that.name)) return false;
        }
        if (email != null && that.email != null) {
            if (!email.equals(that.email)) return false;
        }
        if (username != null && that.username != null) {
            if (!username.equals(that.username)) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        return result;
    }
}
