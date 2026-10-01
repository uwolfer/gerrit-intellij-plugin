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
import java.util.concurrent.atomic.AtomicBoolean;

public class GerritGitUtilTest {
    private static final String GERRIT_URL = "https://gerrit.example.com/myProject";
    private static final String CHANGE_REF = "refs/changes/34/1234/1";

    @Test
    public void testFetchIfMissingRunsCallbackWithoutFetchingLocalCommit() {
        Project project = EasyMock.createMock(Project.class);
        GitRepository gitRepository = EasyMock.createMock(GitRepository.class);
        EasyMock.replay(project, gitRepository);

        AtomicBoolean callbackRan = new AtomicBoolean();
        new GerritGitUtil().fetchIfMissing(project, gitRepository, new FetchInfo(GERRIT_URL, CHANGE_REF), true, () -> {
            callbackRan.set(true);
            return null;
        });

        Assert.assertTrue(callbackRan.get());
        EasyMock.verify(project, gitRepository);
    }

    @Test
    public void testFetchIfMissingRunsCallbackAfterSuccessfulFetch() {
        Assert.assertTrue(fetchMissingCommitAndCheckCallback(true));
    }

    @Test
    public void testFetchIfMissingDoesNotRunCallbackAfterFailedFetch() {
        Assert.assertFalse(fetchMissingCommitAndCheckCallback(false));
    }

    private static boolean fetchMissingCommitAndCheckCallback(boolean fetchSucceeds) {
        GitRemote origin = new GitRemote(
            "origin",
            Collections.singletonList(GERRIT_URL),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList()
        );
        GitRepository gitRepository = EasyMock.createMock(GitRepository.class);
        EasyMock.expect(gitRepository.getRemotes()).andReturn(Collections.singletonList(origin)).anyTimes();
        GitFetchResult fetchResult = EasyMock.createMock(GitFetchResult.class);
        EasyMock.expect(fetchResult.showNotificationIfFailed()).andReturn(fetchSucceeds);
        GitFetchSupport fetchSupport = EasyMock.createMock(GitFetchSupport.class);
        EasyMock.expect(fetchSupport.fetch(gitRepository, origin, CHANGE_REF)).andReturn(fetchResult);
        Project project = EasyMock.createMock(Project.class);
        EasyMock.expect(project.getService(GitFetchSupport.class)).andReturn(fetchSupport).anyTimes();
        EasyMock.replay(gitRepository, fetchResult, fetchSupport, project);

        AtomicBoolean callbackRan = new AtomicBoolean();
        new GerritGitUtil().fetchIfMissing(project, gitRepository, new FetchInfo(GERRIT_URL, CHANGE_REF), false, () -> {
            callbackRan.set(true);
            return null;
        });

        EasyMock.verify(fetchResult, fetchSupport);
        return callbackRan.get();
    }
}
