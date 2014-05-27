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

package com.urswolfer.gerrit.client.rest.http.projects;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.common.ProjectInfo;
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
public class ProjectsRestClientTest {
    @Test
    public void testListProjects() throws Exception {
        ProjectListTestCase testCase = testCase().expectUrl("/projects/");
        testCase.execute().verify();
    }

    @Test(dataProvider = "ListProjectTestCases")
    public void testListProjectsWithParameter(ProjectListTestCase testCase) throws Exception {
        testCase.execute().verify();
    }

    @DataProvider(name = "ListProjectTestCases")
    public Iterator<ProjectListTestCase[]> listProjectTestCases() throws Exception {
        return Iterables.transform(Arrays.asList(
                testCase().withListParameter(
                        new TestListRequest().withDescription(true)
                ).expectUrl("/projects/?d"),
                testCase().withListParameter(
                        new TestListRequest().withDescription(false)
                ).expectUrl("/projects/"),
                testCase().withListParameter(
                        new TestListRequest().withLimit(10)
                ).expectUrl("/projects/?n=10"),
                testCase().withListParameter(
                        new TestListRequest().withPrefix("test")
                ).expectUrl("/projects/?p=test"),
                testCase().withListParameter(
                        new TestListRequest().withStart(5)
                ).expectUrl("/projects/?S=5"),
                testCase().withListParameter(
                        new TestListRequest()
                                .withDescription(true)
                                .withLimit(15)
                                .withStart(10)
                                .withPrefix("master")
                ).expectUrl("/projects/?d&p=master&n=15&S=10")
        ), new Function<ProjectListTestCase, ProjectListTestCase[]>() {
            @Override
            public ProjectListTestCase[] apply(ProjectListTestCase testCase) {
                return new ProjectListTestCase[]{testCase};
            }
        }).iterator();
    }

    private static ProjectListTestCase testCase() {
        return new ProjectListTestCase();
    }

    private static final class ProjectListTestCase {
        private TestListRequest listParameter = new TestListRequest();
        private String expectedUrl;
        private JsonElement mockJsonElement = EasyMock.createMock(JsonElement.class);
        private GerritRestClient gerritRestClient;
        private ProjectsParser projectsParser;

        public ProjectListTestCase withListParameter(TestListRequest listParameter) {
            this.listParameter = listParameter;
            return this;
        }

        public ProjectListTestCase expectUrl(String expectedUrl) {
            this.expectedUrl = expectedUrl;
            return this;
        }

        public ProjectListTestCase execute() throws Exception {
            ProjectsRestClient projectsRestClient = getProjectsRestClient();
            Projects.ListRequest list = projectsRestClient.list();
            listParameter.apply(list).get();
            return this;
        }

        public void verify() {
            EasyMock.verify(gerritRestClient, projectsParser);
        }

        public ProjectsRestClient getProjectsRestClient() throws Exception {
            return new ProjectsRestClient(
                    setupGerritRestClient(),
                    setupProjectsParser()
            );
        }

        public GerritRestClient setupGerritRestClient() throws Exception {
            gerritRestClient = EasyMock.createMock(GerritRestClient.class);
            EasyMock.expect(gerritRestClient.getRequest(expectedUrl))
                    .andReturn(mockJsonElement)
                    .once();
            EasyMock.replay(gerritRestClient);
            return gerritRestClient;
        }

        public ProjectsParser setupProjectsParser() throws Exception {
            projectsParser = EasyMock.createMock(ProjectsParser.class);
            EasyMock.expect(projectsParser.parseProjectInfos(mockJsonElement))
                    .andReturn(Lists.<ProjectInfo>newArrayList())
                    .once();
            EasyMock.replay(projectsParser);
            return projectsParser;
        }

        @Override
        public String toString() {
            return expectedUrl;
        }
    }

    private final static class TestListRequest {
        private Boolean description = null;
        private String prefix = null;
        private Integer limit = null;
        private Integer start = null;

        public TestListRequest withDescription(boolean description) {
            this.description = description;
            return this;
        }

        public TestListRequest withPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public TestListRequest withLimit(int limit) {
            this.limit = limit;
            return this;
        }

        public TestListRequest withStart(int start) {
            this.start = start;
            return this;
        }

        public Projects.ListRequest apply(Projects.ListRequest target) {
            if (description != null) {
                target.withDescription(description);
            }
            if (prefix != null) {
                target.withPrefix(prefix);
            }
            if (limit != null) {
                target.withLimit(limit);
            }
            if (start != null) {
                target.withStart(start);
            }
            return target;
        }
    }
}
