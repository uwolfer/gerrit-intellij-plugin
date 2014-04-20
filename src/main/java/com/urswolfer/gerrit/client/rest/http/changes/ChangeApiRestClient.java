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

import com.google.gerrit.extensions.api.changes.*;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.JsonElement;
import com.urswolfer.gerrit.client.rest.NotImplementedException;
import com.urswolfer.gerrit.client.rest.http.HttpStatusException;

import java.util.EnumSet;

/**
 * @author Urs Wolfer
 */
public class ChangeApiRestClient implements ChangeApi {

    private final ChangesRestClient changesRestClient;
    private final String id;

    public ChangeApiRestClient(ChangesRestClient changesRestClient, String triplet) {
        this.changesRestClient = changesRestClient;
        this.id = triplet;
    }

    public ChangeApiRestClient(ChangesRestClient changesRestClient, int id) {
        this.changesRestClient = changesRestClient;
        this.id = "" + id;
    }

    public ChangesRestClient getChangesRestClient() {
        return changesRestClient;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public RevisionApi current() throws RestApiException {
        return new RevisionApiRestClient(this, "current");
    }

    @Override
    public RevisionApi revision(int id) throws RestApiException {
        return new RevisionApiRestClient(this, "" + id);
    }

    @Override
    public RevisionApi revision(String id) throws RestApiException {
        return new RevisionApiRestClient(this, id);
    }

    @Override
    public void abandon() throws RestApiException {
        abandon(new AbandonInput());
    }

    @Override
    public void abandon(AbandonInput abandonInput) throws RestApiException {
        String request = "/changes/" + id + "/abandon";
        String json = changesRestClient.getGson().toJson(abandonInput);
        changesRestClient.getGerritRestClient().postRequest(request, json);
    }

    @Override
    public void restore() throws RestApiException {
        restore(new RestoreInput());
    }

    @Override
    public void restore(RestoreInput in) throws RestApiException {
        throw new NotImplementedException();
    }

    @Override
    public ChangeApi revert() throws RestApiException {
        return revert(new RevertInput());
    }

    @Override
    public ChangeApi revert(RevertInput in) throws RestApiException {
        throw new NotImplementedException();
    }

    @Override
    public void addReviewer(AddReviewerInput in) throws RestApiException {
        throw new NotImplementedException();
    }

    @Override
    public void addReviewer(String in) throws RestApiException {
        throw new NotImplementedException();
    }

    @Override
    public ChangeInfo get(EnumSet<ListChangesOption> options) throws RestApiException {
        return null;
    }

    @Override
    public ChangeInfo get() throws RestApiException {
        String request = "/changes/?q=" + id + "&o=CURRENT_REVISION&o=MESSAGES&o=LABELS&o=DETAILED_LABELS";
        JsonElement jsonElement;
        try {
            jsonElement = changesRestClient.getGerritRestClient().getRequest(request);
        } catch (HttpStatusException e) {
            // remove special handling (-> just notify error) once we drop Gerrit < 2.7 support
            if (e.getStatusCode() == 400) {
                jsonElement = changesRestClient.getGerritRestClient().getRequest(request.replace("&o=MESSAGES", ""));
            } else {
                throw e;
            }
        }
        return changesRestClient.parseSingleChangeInfos(jsonElement.getAsJsonArray().get(0).getAsJsonObject());
    }

    @Override
    public ChangeInfo info() throws RestApiException {
        return get(); // TODO: impl: see api doc
    }
}
