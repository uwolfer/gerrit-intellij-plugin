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

import com.google.gerrit.extensions.common.FetchInfo;
import com.intellij.openapi.project.Project;
import git4idea.fetch.GitFetchResult;
import git4idea.fetch.GitFetchSupport;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.easymock.EasyMock;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;

public class GerritGitUtilTest {
    private static final String GERRIT_URL = "https://gerrit.example.com/myProject";
    private static final String CHANGE_REF = "refs/changes/34/1234/1";

    @Test
    public void testFetchIfMissingDoesNotFetchLocalCommit() {
        Project project = EasyMock.createMock(Project.class);
        GitRepository gitRepository = EasyMock.createMock(GitRepository.class);
        EasyMock.replay(project, gitRepository);

        boolean available = new GerritGitUtil().fetchIfMissing(project, gitRepository, null, true);

        Assert.assertTrue(available);
        EasyMock.verify(project, gitRepository);
    }

    @Test
    public void testFetchIfMissingFetchesChangeRefFromMatchingRemote() {
        GitRemote origin = createRemote(GERRIT_URL);
        GitRepository gitRepository = createRepository(origin);
        GitFetchResult fetchResult = EasyMock.createMock(GitFetchResult.class);
        EasyMock.expect(fetchResult.showNotificationIfFailed()).andReturn(true);
        GitFetchSupport fetchSupport = EasyMock.createMock(GitFetchSupport.class);
        EasyMock.expect(fetchSupport.fetch(gitRepository, origin, CHANGE_REF)).andReturn(fetchResult);
        Project project = createProject(fetchSupport);
        EasyMock.replay(fetchResult, fetchSupport, project);

        boolean available = new GerritGitUtil().fetchIfMissing(project, gitRepository, new FetchInfo(GERRIT_URL, CHANGE_REF), false);

        Assert.assertTrue(available);
        EasyMock.verify(fetchResult, fetchSupport);
    }

    @Test
    public void testFetchIfMissingReportsFailedFetch() {
        GitRemote origin = createRemote(GERRIT_URL);
        GitRepository gitRepository = createRepository(origin);
        GitFetchResult fetchResult = EasyMock.createMock(GitFetchResult.class);
        EasyMock.expect(fetchResult.showNotificationIfFailed()).andReturn(false);
        GitFetchSupport fetchSupport = EasyMock.createMock(GitFetchSupport.class);
        EasyMock.expect(fetchSupport.fetch(gitRepository, origin, CHANGE_REF)).andReturn(fetchResult);
        Project project = createProject(fetchSupport);
        EasyMock.replay(fetchResult, fetchSupport, project);

        boolean available = new GerritGitUtil().fetchIfMissing(project, gitRepository, new FetchInfo(GERRIT_URL, CHANGE_REF), false);

        Assert.assertFalse(available);
        EasyMock.verify(fetchResult, fetchSupport);
    }

    private static GitRemote createRemote(String url) {
        return new GitRemote(
            "origin",
            Collections.singletonList(url),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    private static GitRepository createRepository(GitRemote remote) {
        GitRepository gitRepository = EasyMock.createMock(GitRepository.class);
        EasyMock.expect(gitRepository.getRemotes()).andReturn(Collections.singletonList(remote)).anyTimes();
        EasyMock.replay(gitRepository);
        return gitRepository;
    }

    private static Project createProject(GitFetchSupport fetchSupport) {
        Project project = EasyMock.createMock(Project.class);
        EasyMock.expect(project.getService(GitFetchSupport.class)).andReturn(fetchSupport).anyTimes();
        return project;
    }
}
