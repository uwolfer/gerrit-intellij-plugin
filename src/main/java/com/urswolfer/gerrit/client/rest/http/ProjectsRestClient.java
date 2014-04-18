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
import com.google.gson.JsonObject;
import com.urswolfer.gerrit.client.rest.GerritClientException;
import com.urswolfer.gerrit.client.rest.ProjectsClient;
import com.urswolfer.gerrit.client.rest.bean.ProjectInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Urs Wolfer
 */
public class ProjectsRestClient extends AbstractRestClient implements ProjectsClient {
    private final GerritRestClient gerritRestClient;

    public ProjectsRestClient(GerritRestClient gerritRestClient) {
        this.gerritRestClient = gerritRestClient;
    }

    @Override
    public List<ProjectInfo> getProjects() throws GerritClientException {
        String request = "/projects/";
        JsonElement result = gerritRestClient.getRequest(request);
        if (result == null) {
            return Collections.emptyList();
        }
        return parseProjectInfos(result);
    }

    private List<ProjectInfo> parseProjectInfos(JsonElement result) throws GerritClientException {
        List<ProjectInfo> repositories = new ArrayList<ProjectInfo>();
        final JsonObject jsonObject = result.getAsJsonObject();
        for (Map.Entry<String, JsonElement> element : jsonObject.entrySet()) {
            if (!element.getValue().isJsonObject()) {
                throw new GerritClientException(String.format("This element should be a JsonObject: %s%nTotal JSON response: %n%s", element, result));
            }
            repositories.add(parseSingleRepositoryInfo(element.getValue().getAsJsonObject()));

        }
        return repositories;
    }

    private ProjectInfo parseSingleRepositoryInfo(JsonObject result) {
        return gson.fromJson(result, ProjectInfo.class);
    }
}
