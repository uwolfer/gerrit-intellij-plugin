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

import com.google.gerrit.extensions.common.AccountInfo;

/**
 * @author Thomas Forrer
 */
public final class AccountInfoBuilder {
    private final AccountInfo accountInfo = new AccountInfo();

    public AccountInfo get() {
        return accountInfo;
    }

    public AccountInfoBuilder withName(String name) {
        accountInfo.name = name;
        return this;
    }

    public AccountInfoBuilder withEmail(String email) {
        accountInfo.email = email;
        return this;
    }

    public AccountInfoBuilder withUsername(String username) {
        accountInfo.username = username;
        return this;
    }
}
