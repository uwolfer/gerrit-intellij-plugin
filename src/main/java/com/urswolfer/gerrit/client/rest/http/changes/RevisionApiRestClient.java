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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Urs Wolfer
 */
public class RevisionApiRestClient extends RevisionApi.NotImplemented implements RevisionApi {

    private final ChangeApiRestClient changeApiRestClient;
    private final String revision;

    public RevisionApiRestClient(ChangeApiRestClient changeApiRestClient, String revision) {
        this.changeApiRestClient = changeApiRestClient;
        this.revision = revision;
    }

    @Override
    public void review(ReviewInput reviewInput) throws RestApiException {
        String request = "/changes/" + changeApiRestClient.id() + "/revisions/" + revision + "/review";
        String json = changeApiRestClient.getChangesRestClient().getGerritRestClient().getGson().toJson(reviewInput);
        changeApiRestClient.getChangesRestClient().getGerritRestClient().postRequest(request, json);
    }

    @Override
    public void submit() throws RestApiException {
        submit(new SubmitInput());
    }

    @Override
    public void submit(SubmitInput submitInput) throws RestApiException {
        String request = "/changes/" + changeApiRestClient.id() + "/submit";
        String json = changeApiRestClient.getChangesRestClient().getGerritRestClient().getGson().toJson(submitInput);
        changeApiRestClient.getChangesRestClient().getGerritRestClient().postRequest(request, json);
    }

    @Override
    public void changeReviewed(String filePath, boolean reviewed) throws RestApiException {
        String encodedPath = Url.encode(filePath);
        String request = String.format("/changes/%s/revisions/%s/files/%s/reviewed", changeApiRestClient.id(), revision, encodedPath);

        if (reviewed) {
            changeApiRestClient.getChangesRestClient().getGerritRestClient().putRequest(request);
        } else {
            changeApiRestClient.getChangesRestClient().getGerritRestClient().deleteRequest(request);
        }
    }

    /**
     * Support starting from Gerrit 2.7.
     */
    @Override
    public TreeMap<String, List<ReviewInput.Comment>> getComments() throws RestApiException {
        String request = "/changes/" + changeApiRestClient.id() + "/revisions/" + revision + "/comments/";
        JsonElement jsonElement = changeApiRestClient.getChangesRestClient().getGerritRestClient().getRequest(request);
        return parseCommentInfos(jsonElement);
    }

    private TreeMap<String, List<ReviewInput.Comment>> parseCommentInfos(JsonElement result) {
        TreeMap<String, List<ReviewInput.Comment>> commentInfos = Maps.newTreeMap();
        JsonObject jsonObject = result.getAsJsonObject();

        for (Map.Entry<String, JsonElement> element : jsonObject.entrySet()) {
            List<ReviewInput.Comment> currentCommentInfos = Lists.newArrayList();

            for (JsonElement jsonElement : element.getValue().getAsJsonArray()) {
                currentCommentInfos.add(parseSingleCommentInfos(jsonElement.getAsJsonObject()));
            }

            commentInfos.put(element.getKey(), currentCommentInfos);
        }
        return commentInfos;
    }

    private ReviewInput.Comment parseSingleCommentInfos(JsonObject result) {
        Gson gson = changeApiRestClient.getChangesRestClient().getGerritRestClient().getGson();
        return gson.fromJson(result, ReviewInput.Comment.class);
    }
}
