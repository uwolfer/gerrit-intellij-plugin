/*
 * Copyright 2026 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit;

import com.urswolfer.gerrit.client.rest.GerritAuthData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The credentials of one account, as the REST client wants them.
 *
 * It holds the account id rather than the account, and looks it up per call: the client keeps this object for its
 * lifetime and drops its cached login and cookies as soon as it sees the host, login or password change, which only
 * works while it is reading the account the user is editing rather than a copy of it.
 *
 * @author Urs Wolfer
 */
public class GerritAccountAuthData implements GerritAuthData {

    private final String accountId;

    public GerritAccountAuthData(String accountId) {
        this.accountId = accountId;
    }

    @Nullable
    private GerritAccount account() {
        return GerritAccounts.getInstance().findById(accountId);
    }

    @Override
    public String getHost() {
        GerritAccount account = account();
        return account != null ? account.host : "";
    }

    @Override
    public String getLogin() {
        GerritAccount account = account();
        return account != null ? account.login : "";
    }

    @Override
    @NotNull
    public String getPassword() {
        return GerritAccounts.getInstance().getPassword(account());
    }

    @Override
    public boolean isHttpPassword() {
        return false;
    }

    @Override
    public boolean isLoginAndPasswordAvailable() {
        return !getLogin().isEmpty();
    }
}
