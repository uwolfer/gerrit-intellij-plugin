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
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gson.*;
import com.urswolfer.gerrit.client.rest.gson.DateDeserializer;
import com.urswolfer.gerrit.client.rest.http.common.AccountInfoBuilder;
import com.urswolfer.gerrit.client.rest.http.common.ChangeInfoBuilder;
import com.urswolfer.gerrit.client.rest.http.common.GerritAssert;
import com.urswolfer.gerrit.client.rest.http.common.LabelInfoBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * @author Thomas Forrer
 */
public class ChangesParserTest {
    private static final List<ChangeInfo> CHANGE_INFOS = Lists.newArrayList();

    static {
        AccountInfo uw = new AccountInfoBuilder().withName("Urs Wolfer").get();
        AccountInfo tf = new AccountInfoBuilder().withName("Thomas Forrer").get();
        CHANGE_INFOS.add(new ChangeInfoBuilder()
                .withId("packages%2Ftest~master~Ieabd72e73f3da0df90fd6e8cba8f6c5dd7d120df")
                .withProject("packages/test")
                .withBranch("master")
                .withChangeId("Ieabd72e73f3da0df90fd6e8cba8f6c5dd7d120df")
                .withSubject("test")
                .withStatus(ChangeStatus.NEW)
                .withCreated("2013-07-21 14:23:59.207")
                .withUpdated("2013-07-29 06:55:14.214")
                .withMergeable(false)
                .withNumber(9)
                .withOwner(uw)
                .withLabel("Code-Review", new LabelInfoBuilder().withApproved(uw).get())
                .get());
        CHANGE_INFOS.add(new ChangeInfoBuilder()
                .withId("packages%2Ftest~master~Id786826bdec0ae196737a0137ee2a67a1a294286")
                .withProject("packages/test")
                .withBranch("master")
                .withChangeId("Id786826bdec0ae196737a0137ee2a67a1a294286")
                .withSubject("test 2")
                .withStatus(ChangeStatus.NEW)
                .withCreated("2013-10-12 12:25:22.324")
                .withUpdated("2013-10-19 12:30:46.143")
                .withMergeable(false)
                .withNumber(10)
                .withOwner(uw)
                .withLabel("Code-Review", new LabelInfoBuilder().get())
                .get());
        CHANGE_INFOS.add(new ChangeInfoBuilder()
                .withId("packages%2Ftest~master~I71460f2975fef513a7fb485bef92dada88b88f3a")
                .withProject("packages/test")
                .withBranch("master")
                .withChangeId("I71460f2975fef513a7fb485bef92dada88b88f3a")
                .withSubject("blubber")
                .withStatus(ChangeStatus.NEW)
                .withCreated("2013-10-12 13:29:49.521")
                .withUpdated("2013-10-12 13:29:49.521")
                .withMergeable(false)
                .withNumber(11)
                .withOwner(tf)
                .get());
    }

    private final ChangesParser changesParser = new ChangesParser(getGson());

    @Test
    public void testParseChangeInfos() throws Exception {
        JsonElement jsonElement = getJsonElement("changes.json");

        List<ChangeInfo> changeInfos = changesParser.parseChangeInfos(jsonElement);
        Assert.assertEquals(changeInfos.size(), 3);

        for (int i = 0; i < changeInfos.size(); i++) {
            ChangeInfo actual = changeInfos.get(i);
            ChangeInfo expected = CHANGE_INFOS.get(i);
            GerritAssert.assertEquals(actual, expected);
        }
    }

    private JsonElement getJsonElement(String resourceName) throws Exception {
        URL url = this.getClass().getResource(resourceName);
        File file = new File(url.toURI());
        return new JsonParser().parse(new FileReader(file));
    }

    private Gson getGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new DateDeserializer());
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        return builder.create();
    }
}
