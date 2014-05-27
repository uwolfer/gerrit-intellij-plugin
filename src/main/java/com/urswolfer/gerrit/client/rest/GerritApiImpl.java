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

package com.urswolfer.gerrit.client.rest;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.api.tools.Tools;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import com.urswolfer.gerrit.client.rest.http.HttpRequestExecutor;
import com.urswolfer.gerrit.client.rest.http.accounts.AccountsRestClient;
import com.urswolfer.gerrit.client.rest.http.changes.ChangesParser;
import com.urswolfer.gerrit.client.rest.http.changes.ChangesRestClient;
import com.urswolfer.gerrit.client.rest.http.projects.ProjectsParser;
import com.urswolfer.gerrit.client.rest.http.projects.ProjectsRestClient;
import com.urswolfer.gerrit.client.rest.http.tools.ToolsRestClient;

public class GerritApiImpl extends GerritApi.NotImplemented implements GerritApi {
    private final AccountsRestClient accountsRestClient;
    private final ChangesRestClient changesRestClient;
    private final ProjectsRestClient projectsRestClient;
    private final ToolsRestClient toolsRestClient;

    public GerritApiImpl(GerritAuthData authData,
                         HttpRequestExecutor httpRequestExecutor,
                         HttpClientBuilderExtension... httpClientBuilderExtensions) {
        GerritRestClient gerritRestClient = new GerritRestClient(authData, httpRequestExecutor, httpClientBuilderExtensions);
        changesRestClient = new ChangesRestClient(gerritRestClient, new ChangesParser(gerritRestClient.getGson()));
        accountsRestClient = new AccountsRestClient(gerritRestClient);
        projectsRestClient = new ProjectsRestClient(gerritRestClient, new ProjectsParser(gerritRestClient.getGson()));
        toolsRestClient = new ToolsRestClient(gerritRestClient);
    }

    @Override
    public Accounts accounts() {
        return accountsRestClient;
    }

    @Override
    public Changes changes() {
        return changesRestClient;
    }

    @Override
    public Projects projects() {
        return projectsRestClient;
    }

    @Override
    public Tools tools() {
        return toolsRestClient;
    }
}
