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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.fetch.GitFetchResult;
import git4idea.fetch.GitFetchSupport;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.easymock.EasyMock;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class GerritGitUtilTest {
    private static final String CHANGE_REF = "refs/changes/34/1234/1";
    private static final FetchInfo FETCH_INFO = new FetchInfo("https://gerrit.example.com/myProject", CHANGE_REF);
    private static final GitRemote SSH_REMOTE = remote("ssh", "ssh://gerrit.example.com:29418/myProject");
    private static final GitRemote HTTP_REMOTE = remote("http", "https://gerrit.example.com/myProject");

    @Test
    public void testParseCommitMessages() {
        String a = "a".repeat(40);
        String b = "b".repeat(40);
        String output = "\u0001" + a + "\nFirst\n\nChange-Id: I1\n\n\u0001" + b + "\nSecond\n";

        List<Pair<String, String>> commits = GerritGitUtil.parseCommitMessages(output);

        Assert.assertEquals(commits.size(), 2);
        Assert.assertEquals(commits.get(0).first, a);
        Assert.assertEquals(commits.get(0).second, "First\n\nChange-Id: I1\n\n");
        Assert.assertEquals(commits.get(1).first, b);
        Assert.assertEquals(commits.get(1).second, "Second\n");
    }

    @Test
    public void testParseCommitMessagesSkipsWhatGpgSaysFirst() {
        String a = "a".repeat(40);
        String output = "gpg: Signature made Thu Oct 8\ngpg: Good signature\n\u0001" + a + "\nFirst\n";

        List<Pair<String, String>> commits = GerritGitUtil.parseCommitMessages(output);

        Assert.assertEquals(commits.size(), 1);
        Assert.assertEquals(commits.get(0).first, a);
    }

    @Test
    public void testParseCommitMessagesOfNone() {
        Assert.assertTrue(GerritGitUtil.parseCommitMessages("").isEmpty());
    }

    @Test
    public void testFilesAfterLeavesOutDeletedFiles() {
        FilePath added = EasyMock.createNiceMock(FilePath.class);
        FilePath modified = EasyMock.createNiceMock(FilePath.class);
        FilePath renamedFrom = EasyMock.createNiceMock(FilePath.class);
        FilePath renamedTo = EasyMock.createNiceMock(FilePath.class);
        FilePath deleted = EasyMock.createNiceMock(FilePath.class);
        EasyMock.replay(added, modified, renamedFrom, renamedTo, deleted);
        List<Change> changes = Arrays.asList(
            change(null, added),
            change(modified, modified),
            change(renamedFrom, renamedTo),
            change(deleted, null));

        Assert.assertEquals(GerritGitUtil.filesAfter(changes), Arrays.asList(added, modified, renamedTo));
    }

    // a Change built outside the IDE fails on looking up its file status
    private static Change change(FilePath before, FilePath after) {
        Change change = EasyMock.createNiceMock(Change.class);
        EasyMock.expect(change.getBeforeRevision()).andStubReturn(revision(before));
        EasyMock.expect(change.getAfterRevision()).andStubReturn(revision(after));
        EasyMock.replay(change);
        return change;
    }

    private static ContentRevision revision(FilePath file) {
        if (file == null) {
            return null;
        }
        ContentRevision revision = EasyMock.createNiceMock(ContentRevision.class);
        EasyMock.expect(revision.getFile()).andStubReturn(file);
        EasyMock.replay(revision);
        return revision;
    }

    @Test
    public void testFetchIfMissingRunsCallbackWithoutFetchingLocalCommit() {
        Project project = EasyMock.createMock(Project.class);
        GitRepository gitRepository = EasyMock.createMock(GitRepository.class);
        EasyMock.replay(project, gitRepository);

        Assert.assertTrue(fetchIfMissingAndCheckCallback(project, gitRepository, true));
        EasyMock.verify(project, gitRepository);
    }

    @Test
    public void testFetchIfMissingRunsCallbackAfterSuccessfulFetch() {
        GitRepository gitRepository = createRepository(null, SSH_REMOTE);
        GitFetchSupport fetchSupport = EasyMock.createMock(GitFetchSupport.class);
        EasyMock.expect(fetchSupport.fetch(gitRepository, SSH_REMOTE, CHANGE_REF)).andReturn(succeededFetch());
        EasyMock.replay(fetchSupport);

        Assert.assertTrue(fetchIfMissingAndCheckCallback(createProject(fetchSupport), gitRepository, false));
        EasyMock.verify(fetchSupport);
    }

    @Test
    public void testFetchIfMissingNotifiesAndDoesNotRunCallbackWhenEveryFetchFails() {
        GitRepository gitRepository = createRepository(null, SSH_REMOTE, HTTP_REMOTE);
        GitFetchResult sshResult = failedFetch(true);
        GitFetchResult httpResult = failedFetch(true);
        GitFetchSupport fetchSupport = EasyMock.createMock(GitFetchSupport.class);
        EasyMock.expect(fetchSupport.fetch(gitRepository, SSH_REMOTE, CHANGE_REF)).andReturn(sshResult);
        EasyMock.expect(fetchSupport.fetch(gitRepository, HTTP_REMOTE, CHANGE_REF)).andReturn(httpResult);
        EasyMock.replay(fetchSupport);

        Assert.assertFalse(fetchIfMissingAndCheckCallback(createProject(fetchSupport), gitRepository, false));
        EasyMock.verify(fetchSupport, sshResult, httpResult);
    }

    @Test
    public void testFetchIfMissingFallsBackToNextRemoteWithoutNotifying() {
        GitRepository gitRepository = createRepository(null, SSH_REMOTE, HTTP_REMOTE);
        GitFetchResult sshResult = failedFetch(false);
        GitFetchSupport fetchSupport = EasyMock.createMock(GitFetchSupport.class);
        EasyMock.expect(fetchSupport.fetch(gitRepository, SSH_REMOTE, CHANGE_REF)).andReturn(sshResult);
        EasyMock.expect(fetchSupport.fetch(gitRepository, HTTP_REMOTE, CHANGE_REF)).andReturn(succeededFetch());
        EasyMock.replay(fetchSupport);

        Assert.assertTrue(fetchIfMissingAndCheckCallback(createProject(fetchSupport), gitRepository, false));
        EasyMock.verify(fetchSupport, sshResult);
    }

    @Test
    public void testGetRemotesForChangeKeepsConfigOrderWithoutTrackedRemote() {
        GitRepository gitRepository = createRepository(null, SSH_REMOTE, HTTP_REMOTE);

        List<GitRemote> remotes = new GerritGitUtil().getRemotesForChange(gitRepository, FETCH_INFO, () -> "");

        Assert.assertEquals(remotes, Arrays.asList(SSH_REMOTE, HTTP_REMOTE));
    }

    @Test
    public void testGetRemotesForChangePutsTrackedRemoteFirst() {
        GitRepository gitRepository = createRepository(HTTP_REMOTE, SSH_REMOTE, HTTP_REMOTE);

        List<GitRemote> remotes = new GerritGitUtil().getRemotesForChange(gitRepository, FETCH_INFO, () -> "");

        Assert.assertEquals(remotes, Arrays.asList(HTTP_REMOTE, SSH_REMOTE));
    }

    @Test
    public void testGetRemotesForChangeSkipsRemoteWhichIsNotAUri() {
        GitRemote archive = remote("archive", "/home/me/repos/myProject [old]");
        GitRepository gitRepository = createRepository(null, archive, HTTP_REMOTE);

        List<GitRemote> remotes = new GerritGitUtil().getRemotesForChange(gitRepository, FETCH_INFO, () -> "");

        Assert.assertEquals(remotes, Collections.singletonList(HTTP_REMOTE));
    }

    private static boolean fetchIfMissingAndCheckCallback(Project project, GitRepository gitRepository, boolean commitIsFetched) {
        AtomicBoolean callbackRan = new AtomicBoolean();
        new GerritGitUtil().fetchIfMissing(project, gitRepository, FETCH_INFO, commitIsFetched, () -> {
            callbackRan.set(true);
            return null;
        });
        return callbackRan.get();
    }

    private static GitRepository createRepository(GitRemote trackedRemote, GitRemote... remotes) {
        GitRepository gitRepository = EasyMock.createMock(GitRepository.class);
        EasyMock.expect(gitRepository.getRemotes()).andReturn(Arrays.asList(remotes)).anyTimes();
        GitLocalBranch currentBranch = new GitLocalBranch("master");
        EasyMock.expect(gitRepository.getCurrentBranch()).andReturn(currentBranch).anyTimes();
        GitBranchTrackInfo trackInfo = trackedRemote == null ? null
            : new GitBranchTrackInfo(currentBranch, new GitStandardRemoteBranch(trackedRemote, "master"), false);
        EasyMock.expect(gitRepository.getBranchTrackInfo("master")).andReturn(trackInfo).anyTimes();
        EasyMock.replay(gitRepository);
        return gitRepository;
    }

    private static Project createProject(GitFetchSupport fetchSupport) {
        Project project = EasyMock.createMock(Project.class);
        EasyMock.expect(project.getService(GitFetchSupport.class)).andReturn(fetchSupport).anyTimes();
        EasyMock.replay(project);
        return project;
    }

    private static GitFetchResult succeededFetch() {
        GitFetchResult result = EasyMock.createMock(GitFetchResult.class);
        result.throwExceptionIfFailed();
        EasyMock.replay(result);
        return result;
    }

    private static GitFetchResult failedFetch(boolean expectNotification) {
        GitFetchResult result = EasyMock.createMock(GitFetchResult.class);
        result.throwExceptionIfFailed();
        // like the platform's implementation, which throws it without declaring it
        EasyMock.expectLastCall().andAnswer(() -> {
            throw new VcsException("Connection refused");
        });
        if (expectNotification) {
            EasyMock.expect(result.showNotificationIfFailed()).andReturn(false);
        }
        EasyMock.replay(result);
        return result;
    }

    @Test
    public void testRepositoryForChangeMatchesRemoteUrl() {
        GitRepository repository = repository("/work/app", "ssh://gerrit.example.com:29418/app.git");

        assertRepository(Collections.singletonList(repository), "/work/app", change("app"), repository);
    }

    @Test
    public void testRepositoryForChangeMatchesRemoteName() {
        GitRepository repository = repository("/work/app", null, null, remote("app", "https://github.com/example/mirror"));

        assertRepository(Collections.singletonList(repository), "/work/app", change("app"), repository);
    }

    @Test
    public void testRepositoryForChangeWithoutMatch() {
        GitRepository repository = repository("/work/app", "https://gerrit.example.com/app");

        Assert.assertFalse(GerritGitUtil.getRepositoryForChange(
            Collections.singletonList(repository), "/work/app", GERRIT_HOST, null, change("other")).isPresent());
    }

    @Test
    public void testRepositoryForChangeKeepsAcceptingSuffixMatch() {
        GitRepository repository = repository("/work/my-app", "https://gerrit.example.com/my-app");

        assertRepository(Collections.singletonList(repository), "/work/my-app", change("app"), repository);
    }

    @Test
    public void testRepositoryForChangeKeepsMatchingWithoutGerritHost() {
        GitRepository repository = repository("/work/app", "https://gerrit.example.com/app");

        Optional<GitRepository> match = GerritGitUtil.getRepositoryForChange(
            Collections.singletonList(repository), "/work/app", "", null, change("app"));
        Assert.assertSame(match.orElse(null), repository);
    }

    @Test
    public void testRepositoryForChangeToleratesRemoteUnparseableAsUri() {
        GitRepository repository = repository("/work/app", "https://gerrit.example.com/{group}/app");

        assertRepository(Collections.singletonList(repository), "/work/app", change("app"), repository);
    }

    @Test
    public void testRepositoryForChangePrefersProjectRootAmongSameRemotes() {
        String url = "https://gerrit.example.com/app";
        GitRepository main = repository("/work/app", url);
        GitRepository submodule = repository("/work/app/modules/lib", url);

        assertRepository(Arrays.asList(submodule, main), "/work/app", change("app"), main);
    }

    @Test
    public void testRepositoryForChangePrefersOutermostAmongSameRemotes() {
        String url = "https://gerrit.example.com/app";
        GitRepository main = repository("/work/app", url);
        GitRepository submodule = repository("/work/app/modules/lib", url);

        assertRepository(Arrays.asList(submodule, main), "/work", change("app"), main);
    }

    @Test
    public void testRepositoryForChangePrefersProjectRootOverEnclosingRepository() {
        String url = "https://gerrit.example.com/app";
        GitRepository enclosing = repository("/work", url);
        GitRepository main = repository("/work/app", url);

        assertRepository(Arrays.asList(enclosing, main), "/work/app", change("app"), main);
    }

    @Test
    public void testRepositoryForChangePrefersRootTrackingChangeBranch() {
        String url = "https://gerrit.example.com/app";
        GitRepository main = trackingRepository("/work/app", url, "master");
        GitRepository submodule = trackingRepository("/work/app/modules/lib", url, "lib");

        assertRepository(Arrays.asList(main, submodule), "/work/app", change("app", "lib"), submodule);
        assertRepository(Arrays.asList(submodule, main), "/work/app", change("app", "master"), main);
        assertRepository(Arrays.asList(submodule, main), "/work/app", change("app", "other"), main);
    }

    @Test
    public void testRepositoryForChangeDoesNotPreferProjectRootOfOtherProject() {
        GitRepository main = repository("/work/app", "https://gerrit.example.com/app");
        GitRepository nested = repository("/work/app/lib", "https://gerrit.example.com/lib");

        assertRepository(Arrays.asList(main, nested), "/work/app", change("lib"), nested);
    }

    @Test
    public void testRepositoryForChangePrefersSegmentOverSuffixMatch() {
        GitRepository main = repository("/work/my-app", "https://gerrit.example.com/my-app");
        GitRepository nested = repository("/work/my-app/app", "gerrit.example.com:app");

        assertRepository(Arrays.asList(main, nested), "/work/my-app", change("app"), nested);
    }

    @Test
    public void testRepositoryForChangeDoesNotTakeNestedProjectNameForExactMatch() {
        GitRepository main = repository("/work/team-app", "https://gerrit.example.com/team/app");
        GitRepository dependency = repository("/work/team-app/deps/app", "https://gerrit.example.com/app");

        assertRepository(Arrays.asList(main, dependency), "/work/team-app", change("app"), dependency);
        assertRepository(Arrays.asList(main, dependency), "/work/team-app", change("team/app"), main);
    }

    @Test
    public void testRepositoryForChangeMatchesAuthenticatedHttpUrlExactly() {
        GitRepository main = repository("/work/team-app", "https://gerrit.example.com/team/app");
        GitRepository dependency = repository("/work/team-app/deps/app", "https://gerrit.example.com/a/app");

        assertRepository(Arrays.asList(main, dependency), "/work/team-app", change("app"), dependency);
    }

    @Test
    public void testRepositoryForChangeMatchesCloneBaseUrlExactly() {
        GitRepository main = repository("/work/team-app", "https://gerrit.example.com/team/app");
        GitRepository dependency = repository("/work/team-app/deps/app", "ssh://git.example.com:29418/app");

        Optional<GitRepository> match = GerritGitUtil.getRepositoryForChange(Arrays.asList(main, dependency),
            "/work/team-app", GERRIT_HOST, "ssh://git.example.com:29418/", change("app"));
        Assert.assertSame(match.orElse(null), dependency);
    }

    @Test
    public void testRepositoryForChangePrefersGerritHostOverMirror() {
        GitRepository mirror = repository("/work/app", "https://github.com/org/app");
        GitRepository gerrit = repository("/work/app-gerrit", "https://gerrit.example.com/app");

        assertRepository(Arrays.asList(mirror, gerrit), "/work/app", change("app"), gerrit);
    }

    @Test
    public void testRepositoryForChangePicksAmongUnrelatedRootsByPath() {
        String url = "https://gerrit.example.com/app";
        GitRepository first = repository("/work/app-1", url);
        GitRepository second = repository("/work/app-2", url);

        assertRepository(Arrays.asList(second, first), "/work", change("app"), first);
        assertRepository(Arrays.asList(first, second), null, change("app"), first);
    }

    @Test
    public void testRepositoryForChangeMatchesScpLikeUrlExactly() {
        GitRepository main = repository("/work/team-app", "git@gerrit.example.com:team/app");
        GitRepository dependency = repository("/work/team-app/deps/app", "git@gerrit.example.com:app");

        assertRepository(Arrays.asList(main, dependency), "/work/team-app", change("app"), dependency);
        assertRepository(Arrays.asList(main, dependency), "/work/team-app", change("team/app"), main);
    }

    @Test
    public void testRepositoryForChangeDemotesOtherProjectOnGerritHost() {
        GitRepository otherProject = repository("/work/app", "https://gerrit.example.com/team/app");
        GitRepository mirror = repository("/work/app-mirror", "https://github.com/org/app");

        assertRepository(Arrays.asList(otherProject, mirror), "/work/app", change("app"), mirror);
        assertRepository(Collections.singletonList(otherProject), "/work/app", change("app"), otherProject);
    }

    @Test
    public void testRepositoryForChangeResolvesRemoteAgainstHostWithContextPath() {
        GitRepository mirror = repository("/work/app", "https://github.com/org/app");
        GitRepository gerrit = repository("/work/app-gerrit", "https://gerrit.example.com/gerrit/app");

        Optional<GitRepository> match = GerritGitUtil.getRepositoryForChange(Arrays.asList(mirror, gerrit), "/work/app",
            "https://gerrit.example.com/gerrit", "ssh://gerrit.example.com:29418/", change("app"));
        Assert.assertSame(match.orElse(null), gerrit);
    }

    @Test
    public void testRepositoryForChangeStripsAuthenticationPrefixOnlyOverHttp() {
        GitRepository prefixed = repository("/work/a-app", "ssh://gerrit.example.com:29418/a/app");
        GitRepository plain = repository("/work/app", "https://gerrit.example.com/app");

        assertRepository(Arrays.asList(prefixed, plain), "/work/a-app", change("app"), plain);
        assertRepository(Arrays.asList(prefixed, plain), "/work/a-app", change("a/app"), prefixed);
    }

    @Test
    public void testRepositoryForChangeIgnoresBranchTrackedOnOtherRemote() {
        GitRemote gerritRemote = remote("origin", "https://gerrit.example.com/app");
        GitRemote mirrorRemote = remote("github", "https://github.com/org/app");
        GitRepository mirrorTracking = repository("/work/app-2", mirrorRemote, "master", gerritRemote, mirrorRemote);
        GitRepository main = repository("/work/app", "https://gerrit.example.com/app");

        assertRepository(Arrays.asList(mirrorTracking, main), "/work/app", change("app", "master"), main);
    }

    private static final String GERRIT_HOST = "https://gerrit.example.com";

    private static void assertRepository(List<GitRepository> repositories, String projectBasePath,
                                         ChangeInfo change, GitRepository expected) {
        Optional<GitRepository> repository =
            GerritGitUtil.getRepositoryForChange(repositories, projectBasePath, GERRIT_HOST, null, change);
        Assert.assertSame(repository.orElse(null), expected);
    }

    private static ChangeInfo change(String project) {
        return change(project, "master");
    }

    private static ChangeInfo change(String project, String branch) {
        ChangeInfo change = new ChangeInfo();
        change.project = project;
        change.branch = branch;
        return change;
    }

    private static GitRemote remote(String name, String url) {
        return new GitRemote(
            name,
            Collections.singletonList(url),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    private static GitRepository repository(String rootPath, String url) {
        return repository(rootPath, null, null, remote("origin", url));
    }

    private static GitRepository trackingRepository(String rootPath, String url, String trackedBranch) {
        GitRemote remote = remote("origin", url);
        return repository(rootPath, remote, trackedBranch, remote);
    }

    private static GitRepository repository(String rootPath, GitRemote trackedRemote, String trackedBranch,
                                            GitRemote... remotes) {
        VirtualFile root = EasyMock.createMock(VirtualFile.class);
        EasyMock.expect(root.getPath()).andReturn(rootPath).anyTimes();
        EasyMock.replay(root);

        // nice, so that a root without a tracked branch answers null like a detached submodule does
        GitRepository repository = EasyMock.createNiceMock(GitRepository.class);
        EasyMock.expect(repository.getRoot()).andReturn(root).anyTimes();
        EasyMock.expect(repository.getRemotes()).andReturn(Arrays.asList(remotes)).anyTimes();
        if (trackedBranch != null) {
            GitLocalBranch localBranch = new GitLocalBranch(trackedBranch);
            EasyMock.expect(repository.getCurrentBranch()).andReturn(localBranch).anyTimes();
            EasyMock.expect(repository.getBranchTrackInfo(trackedBranch)).andReturn(new GitBranchTrackInfo(
                localBranch, new GitStandardRemoteBranch(trackedRemote, trackedBranch), true)).anyTimes();
        }
        EasyMock.replay(repository);
        return repository;
    }
}
