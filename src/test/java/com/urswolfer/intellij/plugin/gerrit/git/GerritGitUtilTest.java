/*
 * Copyright 2013-2015 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.git;

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.FetchInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.easymock.EasyMock;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;

public class GerritGitUtilTest {

    @Test
    public void testDetermineFetchTargetUsesSelfPathWhenCommitAlreadyFetched() {
        GerritGitUtil gerritGitUtil = new GerritGitUtil();

        GitRepository gitRepository = EasyMock.createMock(GitRepository.class);
        EasyMock.replay(gitRepository);

        Optional<Pair<GitRemote, String>> fetchTarget = gerritGitUtil.determineFetchTarget(
            null,
            gitRepository,
            null,
            "abcd1234",
            true
        );

        Assert.assertTrue(fetchTarget.isPresent());
        Pair<GitRemote, String> target = fetchTarget.get();
        Assert.assertEquals(target.first.getName(), ".");
        Assert.assertEquals(target.second, "abcd1234");

        EasyMock.verify(gitRepository);
    }

    @Test
    public void testDetermineFetchTargetResolvesRemoteWhenCommitNotYetFetched() {
        GerritGitUtil gerritGitUtil = new GerritGitUtil();

        String gerritUrl = "https://gerrit.example.com/myProject";
        GitRemote origin = new GitRemote(
            "origin",
            Collections.singletonList(gerritUrl),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList()
        );

        GitRepository gitRepository = EasyMock.createMock(GitRepository.class);
        EasyMock.expect(gitRepository.getRemotes()).andReturn(Collections.singletonList(origin)).anyTimes();
        EasyMock.replay(gitRepository);
        FetchInfo fetchInfo = new FetchInfo(gerritUrl, "refs/changes/34/1234/1");
        Project project = EasyMock.createMock(Project.class);

        Optional<Pair<GitRemote, String>> fetchTarget = gerritGitUtil.determineFetchTarget(
            project,
            gitRepository,
            fetchInfo,
            "abcd1234",
            false
        );

        Assert.assertTrue(fetchTarget.isPresent());
        Pair<GitRemote, String> target = fetchTarget.get();
        Assert.assertSame(target.first, origin);
        Assert.assertEquals(target.second, "refs/changes/34/1234/1");
    }
}
