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
import git4idea.repo.GitRemote;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
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

    @Test
    public void testProjectQueryForEachProjectOnce() {
        Assert.assertEquals(
            GerritUtil.appendProjectQueryParts("is:open", Arrays.asList("a", "b/c", "a"), 4000),
            Collections.singletonList("is:open+(project:a+OR+project:b%2Fc)"));
    }

    @Test
    public void testProjectQueryWithoutProjects() {
        Assert.assertEquals(GerritUtil.appendProjectQueryParts("is:open", Collections.emptyList(), 4000),
            Collections.singletonList("is:open"));
        Assert.assertEquals(GerritUtil.appendProjectQueryParts("", Collections.singletonList("a"), 4000),
            Collections.singletonList("(project:a)"));
    }

    @Test
    public void testLongProjectQueryIsSplit() {
        // "is:open+(project:a+OR+project:b)" is 32 characters
        Assert.assertEquals(
            GerritUtil.appendProjectQueryParts("is:open", Arrays.asList("a", "b", "c", "d", "e"), 32),
            Arrays.asList(
                "is:open+(project:a+OR+project:b)",
                "is:open+(project:c+OR+project:d)",
                "is:open+(project:e)"));
    }

    @Test
    public void testRemoteOnAnotherHostIsIgnored() {
        Assert.assertEquals(GerritUtil.getProjectNames(Arrays.asList(
                remote("origin", "git@github.com:example/demo.git"),
                remote("gerrit", "ssh://user@gerrit.server:29418/demo")),
                "https://gerrit.server", "ssh://gerrit.server:29418"),
            Collections.singletonList("demo"));
    }

    @Test
    public void testRemoteOnTheCloneBaseHostCounts() {
        Assert.assertEquals(GerritUtil.getProjectNames(Arrays.asList(
                remote("mirror", "https://mirror.example/demo"),
                remote("origin", "ssh://git.example:29418/demo")),
                "https://review.example", "ssh://git.example:29418"),
            Collections.singletonList("demo"));
    }

    @Test
    public void testRemotesElsewhereCountWithoutOneOnTheGerritHost() {
        // an SSH alias from ~/.ssh/config does not name the Gerrit host
        Assert.assertEquals(GerritUtil.getProjectNames(Collections.singletonList(
                remote("origin", "review:demo")),
                "https://gerrit.server", null),
            Collections.singletonList("demo"));
    }

    @Test
    public void testRemotePushingToGerritCounts() {
        GitRemote origin = new GitRemote("origin", Collections.singletonList("https://mirror.example/mirrors/demo"),
            Collections.singletonList("ssh://gerrit.server:29418/demo"), Collections.emptyList(), Collections.emptyList());
        Assert.assertEquals(GerritUtil.getProjectNames(Arrays.asList(
                remote("github", "git@github.com:example/demo.git"), origin),
                "https://gerrit.server", "ssh://gerrit.server:29418"),
            Collections.singletonList("demo"));
    }

    @Test
    public void testMirrorUrlOfARemoteOnGerritIsIgnored() {
        GitRemote origin = new GitRemote("origin",
            Arrays.asList("ssh://gerrit.server:29418/demo", "git@github.com:example/demo-mirror.git"),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        Assert.assertEquals(GerritUtil.getProjectNames(Collections.singletonList(origin),
                "https://gerrit.server", "ssh://gerrit.server:29418"),
            Collections.singletonList("demo"));
    }

    private static GitRemote remote(String name, String url) {
        return new GitRemote(name, Collections.singletonList(url), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList());
    }
}
