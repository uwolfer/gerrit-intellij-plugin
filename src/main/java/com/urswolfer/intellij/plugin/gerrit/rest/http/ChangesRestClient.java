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

package com.urswolfer.intellij.plugin.gerrit.rest.http;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.urswolfer.intellij.plugin.gerrit.rest.ChangesClient;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritClientException;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.*;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

/**
 * @author Urs Wolfer
 */
public class ChangesRestClient extends AbstractRestClient implements ChangesClient {

    private final GerritRestClient gerritRestClient;

    public ChangesRestClient(GerritRestClient gerritRestClient) {
        this.gerritRestClient = gerritRestClient;
    }

    @Override
    public List<ChangeInfo> getChanges() throws GerritClientException {
        return getChanges(null);
    }

    @Override
    public List<ChangeInfo> getChanges(String query) throws GerritClientException {
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
    public ChangeInfo getChangeDetails(String changeId) throws GerritClientException {
        String request = "/changes/?q=" + changeId + "&o=CURRENT_REVISION&o=MESSAGES&o=LABELS&o=DETAILED_LABELS";
        JsonElement jsonElement;
        try {
            jsonElement = gerritRestClient.getRequest(request);
        } catch (HttpStatusException e) {
            // remove special handling (-> just notify error) once we drop Gerrit < 2.7 support
            if (e.getStatusCode() == 400) {
                jsonElement = gerritRestClient.getRequest(request.replace("&o=MESSAGES", ""));
            } else {
                throw e;
            }
        }
        return parseSingleChangeInfos(jsonElement.getAsJsonArray().get(0).getAsJsonObject());

    }

    private List<ChangeInfo> parseChangeInfos(@NotNull JsonElement result) throws GerritClientException {
        if (!result.isJsonArray()) {
            if (!result.isJsonObject()) {
                throw new GerritClientException(String.format("Unexpected JSON result format: %s", result));
            }
            return Collections.singletonList(parseSingleChangeInfos(result.getAsJsonObject()));
        }

        List<ChangeInfo> changeInfoList = new ArrayList<ChangeInfo>();
        for (JsonElement element : result.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new GerritClientException(String.format("This element should be a JsonObject: %s%nTotal JSON response: %n%s", element, result));
            }
            changeInfoList.add(parseSingleChangeInfos(element.getAsJsonObject()));
        }
        return changeInfoList;
    }

    @NotNull
    private ChangeInfo parseSingleChangeInfos(JsonObject result) {
        return gson.fromJson(result, ChangeInfo.class);
    }

    /**
     * Support starting from Gerrit 2.7.
     */
    public TreeMap<String, List<CommentInfo>> getComments(String changeId, String revision) throws GerritClientException {
        String request = "/changes/" + changeId + "/revisions/" + revision + "/comments/";
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
        return gson.fromJson(result, CommentInfo.class);
    }

    @Override
    public void postReview(String changeId, String revision, ReviewInput reviewInput) throws GerritClientException {
        String request = "/changes/" + changeId + "/revisions/" + revision + "/review";
        String json = gson.toJson(reviewInput);
        gerritRestClient.postRequest(request, json);
    }

    @Override
    public void postSubmit(String changeId, SubmitInput submitInput) throws GerritClientException {
        String request = "/changes/" + changeId + "/submit";
        String json = gson.toJson(submitInput);
        gerritRestClient.postRequest(request, json);
    }

    @Override
    public void postAbandon(String changeId, AbandonInput abandonInput) throws GerritClientException {
        String request = "/changes/" + changeId + "/abandon";
        String json = gson.toJson(abandonInput);
        gerritRestClient.postRequest(request, json);
    }

    @Override
    public void changeReviewed(String changeId, String revision, String filePath, boolean reviewed) throws GerritClientException {
        String encodedPath;
        try {
            encodedPath = URLEncoder.encode(filePath, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw Throwables.propagate(e);
        }
        String request = String.format("/changes/%s/revisions/%s/files/%s/reviewed", changeId, revision, encodedPath);

        if (reviewed) {
            gerritRestClient.putRequest(request);
        } else {
            gerritRestClient.deleteRequest(request);
        }
    }
}
