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

import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.urswolfer.gerrit.client.rest.gson.DateDeserializer;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;
import org.easymock.EasyMock;
import org.testng.annotations.Test;

import java.util.Date;

/**
 * @author Thomas Forrer
 */
public class ChangeApiRestClientTest {
    @Test
    public void testAddReviewer() throws Exception {
        GerritRestClient gerritRestClient = getGerritRestClient(
                "/changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers",
                "{\"reviewer\":\"jdoe\",\"confirmed\":true}");
        ChangesRestClient changesRestClient = getChangesRestClient();

        ChangeApiRestClient changeApiRestClient = new ChangeApiRestClient(
                gerritRestClient, changesRestClient,
                "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940");

        AddReviewerInput input = new AddReviewerInput();
        input.reviewer = "jdoe";
        input.confirmed = true;

        changeApiRestClient.addReviewer(input);

        EasyMock.verify(gerritRestClient);
    }

    @Test
    public void testAddReviewerWithStringParam() throws Exception {
        GerritRestClient gerritRestClient = getGerritRestClient(
                "/changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers",
                "{\"reviewer\":\"jdoe\"}");
        ChangesRestClient changesRestClient = getChangesRestClient();

        ChangeApiRestClient changeApiRestClient = new ChangeApiRestClient(
                gerritRestClient, changesRestClient,
                "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940");

        changeApiRestClient.addReviewer("jdoe");

        EasyMock.verify(gerritRestClient);
    }

    private GerritRestClient getGerritRestClient(String expectedRequest, String expectedJson) throws Exception {
        GerritRestClient gerritRestClient = EasyMock.createMock(GerritRestClient.class);
        EasyMock.expect(gerritRestClient.getGson()).andStubReturn(getGson());
        EasyMock.expect(gerritRestClient.postRequest(expectedRequest, expectedJson))
                .andReturn(EasyMock.createMock(JsonElement.class)).once();
        EasyMock.replay(gerritRestClient);
        return gerritRestClient;
    }

    private ChangesRestClient getChangesRestClient() {
        ChangesRestClient changesRestClient = EasyMock.createMock(ChangesRestClient.class);
        EasyMock.replay(changesRestClient);
        return changesRestClient;
    }

    private Gson getGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new DateDeserializer());
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        return builder.create();
    }

}
