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

package com.urswolfer.gerrit.client.rest.http.changes;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.JsonElement;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;
import com.urswolfer.gerrit.client.rest.http.util.UrlUtils;

import java.util.List;

/**
 * @author Urs Wolfer
 */
public class ChangesRestClient extends Changes.NotImplemented implements Changes {

    private final GerritRestClient gerritRestClient;
    private final ChangesParser changesParser;

    public ChangesRestClient(GerritRestClient gerritRestClient,
                             ChangesParser changesParser) {
        this.gerritRestClient = gerritRestClient;
        this.changesParser = changesParser;
    }

    @Override
    public QueryRequest query() {
        return new QueryRequest() {
            @Override
            public List<ChangeInfo> get() throws RestApiException {
                return ChangesRestClient.this.get(this);
            }
        };
    }

    @Override
    public QueryRequest query(String query)  {
        return query().withQuery(query);
    }

    private List<ChangeInfo> get(QueryRequest queryRequest) throws RestApiException {
        String query = "";

        if (!Strings.isNullOrEmpty(queryRequest.getQuery())) {
            query = UrlUtils.appendToUrlQuery(query, "q=" + queryRequest.getQuery());
        }
        if (queryRequest.getLimit() > 0) {
            query = UrlUtils.appendToUrlQuery(query, "n=" + queryRequest.getLimit());
        }
        if (queryRequest.getStart() > 0) {
            query = UrlUtils.appendToUrlQuery(query, "S=" + queryRequest.getStart());
        }
        for (ListChangesOption option : queryRequest.getOptions()) {
            query = UrlUtils.appendToUrlQuery(query, "o=" + option);
        }

        String url = "/changes/";
        if (!Strings.isNullOrEmpty(query)) {
            url += '?' + query;
        }

        JsonElement jsonElement = gerritRestClient.getRequest(url);
        return changesParser.parseChangeInfos(jsonElement);
    }

    @Override
    public ChangeApi id(int id) throws RestApiException {
        return new ChangeApiRestClient(this, id);
    }

    @Override
    public ChangeApi id(String triplet) throws RestApiException {
        return new ChangeApiRestClient(this, triplet);
    }

    @Override
    public ChangeApi id(String project, String branch, String id) throws RestApiException {
        return new ChangeApiRestClient(this, String.format("%s~%s~%s", project, branch, id));
    }

    protected GerritRestClient getGerritRestClient() {
        return gerritRestClient;
    }
}
