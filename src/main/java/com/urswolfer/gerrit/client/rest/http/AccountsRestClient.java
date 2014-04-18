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

package com.urswolfer.gerrit.client.rest.http;

import com.google.gson.JsonElement;
import com.urswolfer.gerrit.client.rest.AccountsClient;
import com.urswolfer.gerrit.client.rest.GerritClientException;
import com.urswolfer.gerrit.client.rest.bean.AccountInfo;

/**
 * @author Urs Wolfer
 */
public class AccountsRestClient extends AbstractRestClient implements AccountsClient {

    private final GerritRestClient gerritRestClient;

    public AccountsRestClient(GerritRestClient gerritRestClient) {
        this.gerritRestClient = gerritRestClient;
    }

    @Override
    public AccountInfo getAccountInfo() throws GerritClientException {
        JsonElement result = gerritRestClient.getRequest("/accounts/self");
        return parseUserInfo(result);
    }

    private AccountInfo parseUserInfo(JsonElement result) throws GerritClientException {
        if (result == null) {
            return null;
        }
        if (!result.isJsonObject()) {
            throw new GerritClientException(String.format("Unexpected JSON result format: %s", result));
        }
        return gson.fromJson(result, AccountInfo.class);
    }

    /**
     * Star-endpoint added in Gerrit 2.8.
     */
    @Override
    public void changeStarredStatus(String changeNr, boolean starred) throws GerritClientException {
        final String request = "/accounts/self/starred.changes/" + changeNr;
        if (starred) {
            gerritRestClient.putRequest(request);
        } else {
            gerritRestClient.deleteRequest(request);
        }
    }
}
