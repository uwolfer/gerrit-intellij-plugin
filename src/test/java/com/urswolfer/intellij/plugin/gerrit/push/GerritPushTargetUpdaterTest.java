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

package com.urswolfer.intellij.plugin.gerrit.push;

import git4idea.repo.GitRemote;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GerritPushTargetUpdaterTest {

    @Test
    public void testRemoteOnGitHub() {
        Assert.assertTrue(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("https://github.com/team/lib.git"), List.of())));
        Assert.assertTrue(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("git@GitHub.com:team/lib.git"), List.of())));
        Assert.assertTrue(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("ssh://git@altssh.gitlab.com:443/team/lib.git"), List.of())));
        Assert.assertTrue(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("https://org.visualstudio.com/team/_git/lib"), List.of())));
    }

    @Test
    public void testRemoteWhichMayBeGerrit() {
        Assert.assertFalse(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("ssh://review.example.com:29418/team/app"), List.of())));
        // an SSH host alias
        Assert.assertFalse(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("review:team/app"), List.of())));
        Assert.assertFalse(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("/srv/git/app.git"), List.of())));
        Assert.assertFalse(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of(), List.of())));
    }

    @Test
    public void testPushUrlDecides() {
        // fetches from a GitHub mirror, pushes to Gerrit
        Assert.assertFalse(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("https://github.com/team/app"), List.of("ssh://review.example.com:29418/team/app"))));
        Assert.assertTrue(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of("https://review.example.com/team/app"), List.of("https://github.com/jdoe/app"))));
        // one of several push URLs may be Gerrit
        Assert.assertFalse(GerritPushTargetUpdater.pushesToNonGerritHost(
            remote(List.of(), List.of("https://github.com/team/app", "ssh://review.example.com:29418/team/app"))));
    }

    private static GitRemote remote(List<String> urls, List<String> pushUrls) {
        return new GitRemote("origin", urls, pushUrls, List.of(), List.of());
    }
}
