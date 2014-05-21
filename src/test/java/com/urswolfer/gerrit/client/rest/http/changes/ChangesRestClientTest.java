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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gson.JsonElement;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;
import org.easymock.EasyMock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Thomas Forrer
 */
public class ChangesRestClientTest {
    private static final JsonElement MOCK_JSON_ELEMENT = EasyMock.createMock(JsonElement.class);

    private static final Function<ChangesQueryTestCase, ChangesQueryTestCase[]> WRAP_IN_ARRAY_FUNCTION =
            new Function<ChangesQueryTestCase, ChangesQueryTestCase[]>() {
                @Override
                public ChangesQueryTestCase[] apply(ChangesQueryTestCase testCase) {
                    return new ChangesQueryTestCase[]{testCase};
                }
            };

    @DataProvider(name = "ChangesQueryTestCases")
    public Iterator<ChangesQueryTestCase[]> getChangesQueryTestCases() {
        return Iterables.transform(Arrays.asList(
                queryParameter(
                        new Changes.QueryParameter().withQuery("is:open")
                ).expectUrl("/changes/?q=is:open"),
                queryParameter(
                        new Changes.QueryParameter("is:open+is:watched")
                ).expectUrl("/changes/?q=is:open+is:watched"),
                queryParameter(
                        new Changes.QueryParameter().withLimit(10)
                ).expectUrl("/changes/?n=10"),
                queryParameter(
                        new Changes.QueryParameter("is:open").withLimit(10)
                ).expectUrl("/changes/?q=is:open&n=10"),
                queryParameter(
                        new Changes.QueryParameter().withOption(ListChangesOption.LABELS)
                ).expectUrl("/changes/?o=LABELS"),
                queryParameter(
                        new Changes.QueryParameter().withStart(50)
                ).expectUrl("/changes/?S=50"),
                queryParameter(
                        new Changes.QueryParameter("is:open")
                                .withLimit(10)
                                .withOption(ListChangesOption.CURRENT_FILES)
                                .withStart(30)
                ).expectUrl("/changes/?q=is:open&n=10&S=30&o=CURRENT_FILES")
        ), WRAP_IN_ARRAY_FUNCTION).iterator();
    }

    @Test(dataProvider = "ChangesQueryTestCases")
    public void testQueryWithParameter(ChangesQueryTestCase testCase) throws Exception {
        GerritRestClient gerritRestClient = setupGerritRestClient(testCase);
        ChangesParser changesParser = setupChangesParser();

        ChangesRestClient changes = new ChangesRestClient(gerritRestClient, changesParser);

        changes.query(testCase.queryParameter);

        EasyMock.verify(gerritRestClient, changesParser);
    }

    @Test
    public void testQuery() throws Exception {
        ChangesQueryTestCase testCase = new ChangesQueryTestCase().expectUrl("/changes/");
        GerritRestClient gerritRestClient = setupGerritRestClient(testCase);
        ChangesParser changesParser = setupChangesParser();

        ChangesRestClient changes = new ChangesRestClient(gerritRestClient, changesParser);

        changes.query();

        EasyMock.verify(gerritRestClient, changesParser);
    }

    private GerritRestClient setupGerritRestClient(ChangesQueryTestCase testCase) throws Exception {
        GerritRestClient gerritRestClient = EasyMock.createMock(GerritRestClient.class);

        // this test does not care about json parsing, just return a mocked json element...
        EasyMock.expect(gerritRestClient.getRequest(testCase.expectedUrl))
                .andReturn(MOCK_JSON_ELEMENT)
                .once();

        EasyMock.replay(gerritRestClient);
        return gerritRestClient;
    }

    private ChangesParser setupChangesParser() throws Exception {
        ChangesParser changesParser = EasyMock.createMock(ChangesParser.class);
        EasyMock.expect(changesParser.parseChangeInfos(MOCK_JSON_ELEMENT))
                .andReturn(Lists.<ChangeInfo>newArrayList())
                .once();
        EasyMock.replay(changesParser);
        return changesParser;
    }


    private static ChangesQueryTestCase queryParameter(Changes.QueryParameter parameter) {
        return new ChangesQueryTestCase().withQueryParameter(parameter);
    }

    private static final class ChangesQueryTestCase {
        private Changes.QueryParameter queryParameter;

        private String expectedUrl;

        private ChangesQueryTestCase withQueryParameter(Changes.QueryParameter queryParameter) {
            this.queryParameter = queryParameter;
            return this;
        }

        private ChangesQueryTestCase expectUrl(String expectedUrl) {
            this.expectedUrl = expectedUrl;
            return this;
        }

        @Override
        public String toString() {
            return expectedUrl;
        }
    }
}
