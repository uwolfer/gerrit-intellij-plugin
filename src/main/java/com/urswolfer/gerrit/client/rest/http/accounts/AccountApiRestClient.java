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
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;

/**
 * @author Urs Wolfer
 */
public class AccountApiRestClient extends AccountApi.NotImplemented implements AccountApi {

    private final AccountsParser accountsParser;

    private final GerritRestClient gerritRestClient;
    private final String name;

    public AccountApiRestClient(GerritRestClient gerritRestClient,
                                AccountsParser accountsParser,
                                String name) {
        this.gerritRestClient = gerritRestClient;
        this.accountsParser = accountsParser;
        this.name = name;
    }

    @Override
    public AccountInfo get() throws RestApiException {
        JsonElement result = gerritRestClient.getRequest("/accounts/" + name);
        return accountsParser.parseUserInfo(result);
    }

    @Override
    public void starChange(String id) throws RestApiException {
        gerritRestClient.putRequest(createStarUrl(id));
    }

    @Override
    public void unstarChange(String id) throws RestApiException {
        gerritRestClient.deleteRequest(createStarUrl(id));
    }

    /**
     * Star-endpoint added in Gerrit 2.8.
     */
    private String createStarUrl(String id) throws RestApiException {
        return "/accounts/" + name + "/starred.changes/" + id;
    }
}
