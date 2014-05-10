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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import org.testng.annotations.Test;

import java.util.List;

/**
 * @author Urs Wolfer
 */
public class GerritRestClientTest {
    @Test(enabled = false) // requires running Gerrit instance
    public void testBasicRestCallToLocalhost() throws Exception {
        GerritRestClientFactory gerritRestClientFactory = new GerritRestClientFactory();
        GerritApi gerritClient = gerritRestClientFactory.create(new GerritAuthData.Basic("http://localhost:8080"));
        List<ChangeInfo> changes = gerritClient.changes().query();
        System.out.println(String.format("Got %s changes.", changes.size()));
        System.out.println(changes);
    }

    @Test(enabled = false) // requires running Gerrit instance
    public void testBasicRestCallToLocalhostProjects() throws Exception {
        GerritRestClientFactory gerritRestClientFactory = new GerritRestClientFactory();
        GerritApi gerritClient = gerritRestClientFactory.create(new GerritAuthData.Basic("http://localhost:8080"));
        List<ProjectInfo> projects = gerritClient.projects().list();
        System.out.println(String.format("Got %s projects.", projects.size()));
        System.out.println(projects);
    }

    @Test(enabled = false) // requires running Gerrit instance
    public void testBasicRestCallToLocalhostProjectsQuery() throws Exception {
        GerritRestClientFactory gerritRestClientFactory = new GerritRestClientFactory();
        GerritApi gerritClient = gerritRestClientFactory.create(new GerritAuthData.Basic("http://localhost:8080"));
        List<ProjectInfo> projects = gerritClient.projects().list(new Projects.ListParameter().withLimit(1).withDescription(true));
        System.out.println(String.format("Got %s projects.", projects.size()));
        System.out.println(projects);
    }
}
