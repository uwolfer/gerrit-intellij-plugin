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
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Urs Wolfer
 */
public class RevisionApiRestClient extends RevisionApi.NotImplemented implements RevisionApi {

    private final GerritRestClient gerritRestClient;
    private final ChangeApiRestClient changeApiRestClient;
    private final String revision;

    public RevisionApiRestClient(GerritRestClient gerritRestClient,
                                 ChangeApiRestClient changeApiRestClient,
                                 String revision) {
        this.gerritRestClient = gerritRestClient;
        this.changeApiRestClient = changeApiRestClient;
        this.revision = revision;
    }

    @Override
    public void review(ReviewInput reviewInput) throws RestApiException {
        String request = "/changes/" + changeApiRestClient.id() + "/revisions/" + revision + "/review";
        String json = gerritRestClient.getGson().toJson(reviewInput);
        gerritRestClient.postRequest(request, json);
    }

    @Override
    public void submit() throws RestApiException {
        submit(new SubmitInput());
    }

    @Override
    public void submit(SubmitInput submitInput) throws RestApiException {
        String request = "/changes/" + changeApiRestClient.id() + "/submit";
        String json = gerritRestClient.getGson().toJson(submitInput);
        gerritRestClient.postRequest(request, json);
    }

    @Override
    public void setReviewed(String unencodedFilePath) throws RestApiException {
        String url = createReviewedUrl(unencodedFilePath);
        gerritRestClient.putRequest(url);
    }

    @Override
    public void deleteReviewed(String unencodedFilePath) throws RestApiException {
        String url = createReviewedUrl(unencodedFilePath);
        gerritRestClient.deleteRequest(url);
    }

    public String createReviewedUrl(String unencodedFilePath) {
        String encodedPath = Url.encode(unencodedFilePath);
        return String.format("/changes/%s/revisions/%s/files/%s/reviewed", changeApiRestClient.id(), revision, encodedPath);
    }

    /**
     * Support starting from Gerrit 2.7.
     */
    @Override
    public TreeMap<String, List<CommentInfo>> getComments() throws RestApiException {
        String request = "/changes/" + changeApiRestClient.id() + "/revisions/" + revision + "/comments/";
        JsonElement jsonElement = gerritRestClient.getRequest(request);
        return parseCommentInfos(jsonElement);
    }

    private TreeMap<String, List<CommentInfo>> parseCommentInfos(JsonElement result) {
        TreeMap<String, List<CommentInfo>> commentInfos = Maps.newTreeMap();
        JsonObject jsonObject = result.getAsJsonObject();

        for (Map.Entry<String, JsonElement> element : jsonObject.entrySet()) {
            List<CommentInfo> currentCommentInfos = Lists.newArrayList();

            for (JsonElement jsonElement : element.getValue().getAsJsonArray()) {
                currentCommentInfos.add(parseSingleCommentInfos(jsonElement.getAsJsonObject()));
            }

            commentInfos.put(element.getKey(), currentCommentInfos);
        }
        return commentInfos;
    }

    private CommentInfo parseSingleCommentInfos(JsonObject result) {
        Gson gson = gerritRestClient.getGson();
        return gson.fromJson(result, CommentInfo.class);
    }
}
