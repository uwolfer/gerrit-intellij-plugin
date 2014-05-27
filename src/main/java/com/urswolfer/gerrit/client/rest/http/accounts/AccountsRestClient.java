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
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;

/**
 * @author Urs Wolfer
 */
public class AccountsRestClient extends AccountApi.NotImplemented implements Accounts {

    private final GerritRestClient gerritRestClient;
    private final AccountsParser accountsParser;

    public AccountsRestClient(GerritRestClient gerritRestClient, AccountsParser accountsParser) {
        this.gerritRestClient = gerritRestClient;
        this.accountsParser = accountsParser;
    }

    @Override
    public AccountApi id(String id) throws RestApiException {
        return new AccountApiRestClient(this, accountsParser, id);
    }

    @Override
    public AccountApi self() throws RestApiException {
        return id("self");
    }

    protected GerritRestClient getGerritRestClient() {
        return gerritRestClient;
    }
}
