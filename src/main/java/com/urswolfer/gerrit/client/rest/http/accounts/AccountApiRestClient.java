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

package com.urswolfer.gerrit.client.rest.http.accounts;

import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.JsonElement;

/**
 * @author Urs Wolfer
 */
public class AccountApiRestClient implements AccountApi {

    private final AccountsRestClient accountsRestClient;
    private final String name;

    public AccountApiRestClient(AccountsRestClient accountsRestClient, String name) {
        this.accountsRestClient = accountsRestClient;
        this.name = name;
    }

    @Override
    public AccountInfo get() throws RestApiException {
        JsonElement result = accountsRestClient.getGerritRestClient().getRequest("/accounts/" + name);
        return parseUserInfo(result);
    }

    private AccountInfo parseUserInfo(JsonElement result) throws RestApiException {
        if (result == null) {
            return null;
        }
        if (!result.isJsonObject()) {
            throw new RestApiException(String.format("Unexpected JSON result format: %s", result));
        }
        return accountsRestClient.getGson().fromJson(result, AccountInfo.class);
    }

    /**
     * Star-endpoint added in Gerrit 2.8.
     */
    @Override
    public void changeStarredStatus(String changeNr, boolean starred) throws RestApiException {
        final String request = "/accounts/" + name + "/starred.changes/" + changeNr;
        if (starred) {
            accountsRestClient.getGerritRestClient().putRequest(request);
        } else {
            accountsRestClient.getGerritRestClient().deleteRequest(request);
        }
    }
}
