/*
 * Copyright 2026 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.util;

import com.google.gerrit.extensions.common.DownloadSchemeInfo;
import git4idea.repo.GitRemote;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Urs Wolfer
 */
public class GerritRemotesTest {

    @Test
    public void testRemoteBelowTheGerritPathIgnoresAnSshCloneBase() {
        Assert.assertEquals(GerritRemotes.getProjectName("https://host/r/project",
            Arrays.asList("https://host/r", "ssh://host:29418", "https://host/r/plugins/gitiles")), "project");
    }

    @Test
    public void testRemoteBelowTheCloneBasePath() {
        Assert.assertEquals(GerritRemotes.getProjectName("https://host/git/team/app.git",
            Arrays.asList("https://host", "https://host/git")), "team/app");
    }

    @Test
    public void testRemoteWithTrailingSlash() {
        Assert.assertEquals(GerritRemotes.getProjectName("https://host/team/app/",
            Collections.singletonList("https://host")), "team/app");
    }

    @Test
    public void testSshRemote() {
        Assert.assertEquals(GerritRemotes.getProjectName("ssh://jdoe@host:29418/team/app",
            Arrays.asList("https://host/r", "ssh://host:29418")), "team/app");
        Assert.assertEquals(GerritRemotes.getProjectName("jdoe@host:team/app",
            Collections.singletonList("https://host")), "team/app");
    }

    @Test
    public void testAuthenticatedHttpRemote() {
        Assert.assertEquals(GerritRemotes.getProjectName("https://host/r/a/team/app",
            Collections.singletonList("https://host/r")), "team/app");
    }

    @Test
    public void testRemoteOnAnotherHost() {
        Assert.assertNull(GerritRemotes.getProjectName("https://github.com/team/app",
            Collections.singletonList("https://host")));
    }

    @Test
    public void testProjectNames() {
        // Default set - test trailing / behaviour
        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server",
                        "",
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/",
                        "",
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server",
                        "",
                        "http://gerrit.server/project/"
                ));

        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/",
                        "",
                        "http://gerrit.server/project/"
                ));

        // Subdirectory set - test trailing / behaviour
        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/r/",
                        "",
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project/"
                ));

        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/r/",
                        "",
                        "http://gerrit.server/r/project/"
                ));

        // Default set - test named .git
        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server",
                        "",
                        "http://gerrit.server/project.git"
                ));

        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/",
                        "",
                        "http://gerrit.server/project.git"
                ));


        // Subdirectory set - test named .git
        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project.git"
                ));

        Assert.assertEquals("project",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/r/",
                        "",
                        "http://gerrit.server/r/project.git"
                ));

        // Test some project names with / in them
        Assert.assertEquals("project/blah/test",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project/blah/test"
                ));

        Assert.assertEquals("project/blah/test",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/r",
                        "",
                        "http://gerrit.server/r/project/blah/test.git"
                ));

        // specific case where gerrit URL is provided via HTTP but git is configured to use ssh
        Assert.assertEquals("project/blah",
                GerritRemotes.getProjectName(
                        "http://gerrit.server/gerrit",
                        "",
                        "ssh://git@gerrit.server:29418/project/blah"
                ));

        // should not fail with an StringIndexOutOfBoundsException
        Assert.assertEquals("",
                GerritRemotes.getProjectName(
                        "http://gerrit.server",
                        "",
                        "http://gerrit.server"
                ));
    }

    @Test
    public void testProjectNameOfScpLikeUrl() {
        Assert.assertEquals(GerritRemotes.getProjectName("https://gerrit.server", "", "git@gerrit.server:tools/gerrit"),
                "tools/gerrit");
    }

    private static DownloadSchemeInfo scheme(String url, boolean authRequired) {
        DownloadSchemeInfo scheme = new DownloadSchemeInfo();
        scheme.url = url;
        scheme.isAuthRequired = authRequired;
        return scheme;
    }

    @Test
    public void testCloneBaseUrlPrefersTheSchemeWithoutLogin() {
        Map<String, DownloadSchemeInfo> schemes = new LinkedHashMap<>();
        schemes.put("ssh", scheme("ssh://user@host:29418/${project}", true));
        schemes.put("http", scheme("https://host/gerrit/a/${project}", true));
        schemes.put("anonymous http", scheme("https://host/gerrit/${project}", false));
        Assert.assertEquals(GerritRemotes.getCloneBaseUrl(schemes), "https://host/gerrit");
    }

    @Test
    public void testCloneBaseUrlWithoutThePrefixOfTheAuthenticatedEndpoints() {
        Map<String, DownloadSchemeInfo> schemes = new LinkedHashMap<>();
        schemes.put("http", scheme("https://host/a/${project}", true));
        Assert.assertEquals(GerritRemotes.getCloneBaseUrl(schemes), "https://host");
    }

    @Test
    public void testCloneBaseUrlWithoutTheUserOfTheRequest() {
        Map<String, DownloadSchemeInfo> schemes = new LinkedHashMap<>();
        schemes.put("http", scheme("https://jane@example.com@host/gerrit/a/${project}", true));
        Assert.assertEquals(GerritRemotes.getCloneBaseUrl(schemes), "https://host/gerrit");
    }

    @Test
    public void testCloneBaseUrlWithoutHttpScheme() {
        Map<String, DownloadSchemeInfo> schemes = new LinkedHashMap<>();
        schemes.put("ssh", scheme("ssh://user@host:29418/${project}", true));
        schemes.put("broken", scheme("https://host/no-placeholder", false));
        Assert.assertNull(GerritRemotes.getCloneBaseUrl(schemes));
        Assert.assertNull(GerritRemotes.getCloneBaseUrl(null));
    }

    @Test
    public void testRemoteOnAnotherHostIsIgnored() {
        Assert.assertEquals(GerritRemotes.getProjectNames(Arrays.asList(
                remote("origin", "git@github.com:example/demo.git"),
                remote("gerrit", "ssh://user@gerrit.server:29418/demo")),
                "https://gerrit.server", "ssh://gerrit.server:29418"),
            Collections.singletonList("demo"));
    }

    @Test
    public void testRemoteBelowTheGerritPathIsNotResolvedAgainstTheCloneBase() {
        Assert.assertEquals(GerritRemotes.getProjectNames(Collections.singletonList(
                remote("origin", "https://host/r/demo")),
                "https://host/r", "ssh://host:29418"),
            Collections.singletonList("demo"));
    }

    @Test
    public void testRemoteWhichIsNotAUriIsIgnored() {
        Assert.assertEquals(GerritRemotes.getProjectNames(Arrays.asList(
                remote("archive", "/home/me/repos/demo [old]"),
                remote("origin", "https://gerrit.server/demo")),
                "https://gerrit.server", null),
            Collections.singletonList("demo"));
    }

    @Test
    public void testRemoteOnTheCloneBaseHostCounts() {
        Assert.assertEquals(GerritRemotes.getProjectNames(Arrays.asList(
                remote("mirror", "https://mirror.example/demo"),
                remote("origin", "ssh://git.example:29418/demo")),
                "https://review.example", "ssh://git.example:29418"),
            Collections.singletonList("demo"));
    }

    @Test
    public void testRemotesElsewhereCountWithoutOneOnTheGerritHost() {
        // an SSH alias from ~/.ssh/config does not name the Gerrit host
        Assert.assertEquals(GerritRemotes.getProjectNames(Collections.singletonList(
                remote("origin", "review:demo")),
                "https://gerrit.server", null),
            Collections.singletonList("demo"));
    }

    @Test
    public void testRemotePushingToGerritCounts() {
        GitRemote origin = new GitRemote("origin", Collections.singletonList("https://mirror.example/mirrors/demo"),
            Collections.singletonList("ssh://gerrit.server:29418/demo"), Collections.emptyList(), Collections.emptyList());
        Assert.assertEquals(GerritRemotes.getProjectNames(Arrays.asList(
                remote("github", "git@github.com:example/demo.git"), origin),
                "https://gerrit.server", "ssh://gerrit.server:29418"),
            Collections.singletonList("demo"));
    }

    @Test
    public void testMirrorUrlOfARemoteOnGerritIsIgnored() {
        GitRemote origin = new GitRemote("origin",
            Arrays.asList("ssh://gerrit.server:29418/demo", "git@github.com:example/demo-mirror.git"),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        Assert.assertEquals(GerritRemotes.getProjectNames(Collections.singletonList(origin),
                "https://gerrit.server", "ssh://gerrit.server:29418"),
            Collections.singletonList("demo"));
    }

    private static GitRemote remote(String name, String url) {
        return new GitRemote(name, Collections.singletonList(url), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList());
    }
}
