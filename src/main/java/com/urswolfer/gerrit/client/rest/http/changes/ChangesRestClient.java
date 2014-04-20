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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.urswolfer.gerrit.client.rest.http.AbstractRestClient;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Urs Wolfer
 */
public class ChangesRestClient extends AbstractRestClient implements Changes {

    public ChangesRestClient(GerritRestClient gerritRestClient) {
        super(gerritRestClient);
    }

    @Override
    public List<ChangeInfo> list() throws RestApiException {
        return list(null);
    }

    @Override
    public List<ChangeInfo> list(String query) throws RestApiException {
        String endPoint = "changes";
        String url;
        if (Strings.isNullOrEmpty(query)) {
            url = String.format("/%s/", endPoint);
        } else {
            url = String.format("/%s/?%s", endPoint, query);
        }
        JsonElement jsonElement = gerritRestClient.getRequest(url);
        return parseChangeInfos(jsonElement);
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

    private List<ChangeInfo> parseChangeInfos(JsonElement result) throws RestApiException {
        if (!result.isJsonArray()) {
            if (!result.isJsonObject()) {
                throw new RestApiException(String.format("Unexpected JSON result format: %s", result));
            }
            return Collections.singletonList(parseSingleChangeInfos(result.getAsJsonObject()));
        }

        List<ChangeInfo> changeInfoList = new ArrayList<ChangeInfo>();
        for (JsonElement element : result.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new RestApiException(String.format("This element should be a JsonObject: %s%nTotal JSON response: %n%s", element, result));
            }
            changeInfoList.add(parseSingleChangeInfos(element.getAsJsonObject()));
        }
        return changeInfoList;
    }

    protected ChangeInfo parseSingleChangeInfos(JsonObject result) {
        return gson.fromJson(result, ChangeInfo.class);
    }

}
