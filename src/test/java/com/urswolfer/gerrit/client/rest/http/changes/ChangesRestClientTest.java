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
import java.util.EnumSet;
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
                        new TestQueryRequest().withQuery("is:open")
                ).expectUrl("/changes/?q=is:open"),
                queryParameter(
                        new TestQueryRequest().withQuery("is:open+is:watched")
                ).expectUrl("/changes/?q=is:open+is:watched"),
                queryParameter(
                        new TestQueryRequest().withLimit(10)
                ).expectUrl("/changes/?n=10"),
                queryParameter(
                        new TestQueryRequest().withQuery("is:open").withLimit(10)
                ).expectUrl("/changes/?q=is:open&n=10"),
                queryParameter(
                        new TestQueryRequest().withOption(ListChangesOption.LABELS)
                ).expectUrl("/changes/?o=LABELS"),
                queryParameter(
                        new TestQueryRequest().withStart(50)
                ).expectUrl("/changes/?S=50"),
                queryParameter(
                        new TestQueryRequest().withQuery("is:open")
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

        Changes.QueryRequest queryRequest = changes.query();
        testCase.queryParameter.apply(queryRequest).get();

        EasyMock.verify(gerritRestClient, changesParser);
    }

    @Test
    public void testQuery() throws Exception {
        ChangesQueryTestCase testCase = new ChangesQueryTestCase().expectUrl("/changes/");
        GerritRestClient gerritRestClient = setupGerritRestClient(testCase);
        ChangesParser changesParser = setupChangesParser();

        ChangesRestClient changes = new ChangesRestClient(gerritRestClient, changesParser);

        changes.query().get();

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


    private static ChangesQueryTestCase queryParameter(TestQueryRequest parameter) {
        return new ChangesQueryTestCase().withQueryParameter(parameter);
    }

    private static final class ChangesQueryTestCase {
        private TestQueryRequest queryParameter;

        private String expectedUrl;

        private ChangesQueryTestCase withQueryParameter(TestQueryRequest queryParameter) {
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

    private static final class TestQueryRequest {
        private String query = null;
        private Integer limit = null;
        private Integer start = null;
        private EnumSet<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);

        public TestQueryRequest withQuery(String query) {
            this.query = query;
            return this;
        }

        public TestQueryRequest withLimit(int limit) {
            this.limit = limit;
            return this;
        }

        public TestQueryRequest withStart(int start) {
            this.start = start;
            return this;
        }

        public TestQueryRequest withOption(ListChangesOption options) {
            this.options.add(options);
            return this;
        }

        public Changes.QueryRequest apply(Changes.QueryRequest queryRequest) {
            if (query != null) {
                queryRequest.withQuery(query);
            }
            if (limit != null) {
                queryRequest.withLimit(limit);
            }
            if (start != null) {
                queryRequest.withStart(start);
            }
            if (!options.isEmpty()) {
                queryRequest.withOptions(options);
            }
            return queryRequest;
        }
    }
}
