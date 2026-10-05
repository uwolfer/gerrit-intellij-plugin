/*
 * Copyright 2013 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * @author Urs Wolfer
 */
public class GerritUtilTest {

    @Test
    public void testProjectNames() {
        // Default set - test trailing / behaviour
        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server",
                        "",
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/",
                        "",
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server",
                        "",
                        "http://gerrit.server/project/"
                ));

        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/",
                        "",
                        "http://gerrit.server/project/"
                ));

        // Subdirectory set - test trailing / behaviour
        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/r/",
                        "",
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project/"
                ));

        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/r/",
                        "",
                        "http://gerrit.server/r/project/"
                ));

        // Default set - test named .git
        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server",
                        "",
                        "http://gerrit.server/project.git"
                ));

        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/",
                        "",
                        "http://gerrit.server/project.git"
                ));


        // Subdirectory set - test named .git
        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project.git"
                ));

        Assert.assertEquals("project",
                GerritUtil.getProjectName(
                        "http://gerrit.server/r/",
                        "",
                        "http://gerrit.server/r/project.git"
                ));

        // Test some project names with / in them
        Assert.assertEquals("project/blah/test",
                GerritUtil.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project/blah/test"
                ));

        Assert.assertEquals("project/blah/test",
                GerritUtil.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project/blah/test.git"
                ));

        // specific case where gerrit URL is provided via HTTP but git is configured to use ssh
        Assert.assertEquals("project/blah",
                GerritUtil.getProjectName(
                        "http://gerrit.server/gerrit",
                        "",
                        "ssh://git@gerrit.server:29418/project/blah"
                ));

        // should not fail with an StringIndexOutOfBoundsException
        Assert.assertEquals("",
                GerritUtil.getProjectName(
                        "http://gerrit.server",
                        "",
                        "http://gerrit.server"
                ));
    }

    @Test
    public void testProjectNameOfScpLikeUrl() {
        Assert.assertEquals(GerritUtil.getProjectName("https://gerrit.server", "", "git@gerrit.server:tools/gerrit"),
                "tools/gerrit");
    }

    @Test
    public void testFetchInfoFromGerrit() {
        RevisionInfo revisionInfo = new RevisionInfo("refs/changes/34/1234/2");
        FetchInfo ssh = new FetchInfo("ssh://gerrit.server:29418/project", "refs/changes/34/1234/2");
        revisionInfo.fetch = new LinkedHashMap<>();
        revisionInfo.fetch.put("ssh", ssh);
        revisionInfo.fetch.put("http", new FetchInfo("https://gerrit.server/project", "refs/changes/34/1234/2"));

        Assert.assertSame(GerritUtil.getFirstFetchInfo(revisionInfo, () -> {
            throw new AssertionError("the fetch information of Gerrit is used as is");
        }), ssh);
    }

    @Test
    public void testFetchInfoWithoutDownloadSchemes() {
        RevisionInfo revisionInfo = new RevisionInfo("refs/changes/34/1234/2");
        revisionInfo.fetch = Collections.emptyMap();

        FetchInfo fetchInfo = GerritUtil.getFirstFetchInfo(revisionInfo, () -> "https://gerrit.server");

        Assert.assertEquals(fetchInfo.url, "https://gerrit.server");
        Assert.assertEquals(fetchInfo.ref, "refs/changes/34/1234/2");

        revisionInfo.fetch = null;
        Assert.assertEquals(GerritUtil.getFirstFetchInfo(revisionInfo, () -> "https://gerrit.server").ref,
                "refs/changes/34/1234/2");
    }

    @Test
    public void testFetchInfoWithoutRef() {
        RevisionInfo revisionInfo = new RevisionInfo();

        Assert.assertNull(GerritUtil.getFirstFetchInfo(revisionInfo, () -> "https://gerrit.server"));
        Assert.assertNull(GerritUtil.getFirstFetchInfo(null, () -> "https://gerrit.server"));
    }
}
