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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes.GerritProject;
import com.urswolfer.intellij.plugin.gerrit.util.GitilesUrls;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitUtil;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.MyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Opens a file, a revision of a file or a commit of a Gerrit project in Gitiles, wherever the IDE offers one.
 *
 * Not {@link com.intellij.openapi.actionSystem.UpdateInBackground}: the selection of the log is read straight from
 * its Swing table.
 *
 * @author Urs Wolfer
 */
public class OpenInGitilesAction extends AnAction implements DumbAware {

    public OpenInGitilesAction() {
        super(MyIcons.Gerrit);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(getTarget(e) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Target target = getTarget(e);
        if (project == null || target == null) {
            return;
        }
        if (target.revision != null) {
            BrowserUtil.browse(target.getUrl(target.revision, target.line));
            return;
        }
        new Task.Backgroundable(project, "Resolving Gitiles link") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    String url = resolveWorkingCopyUrl(project, target);
                    if (url != null) {
                        BrowserUtil.browse(url);
                    }
                } catch (VcsException ex) {
                    NotificationService.getInstance().notifyError(
                        new NotificationBuilder(project, "Cannot Open in Gitiles", ex.getMessage()));
                }
            }
        }.queue();
    }

    @Nullable
    private static Target getTarget(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return null;
        }
        String baseUrl = GerritSettings.getInstance().getGitilesUrlOrDefault();
        if (baseUrl.isEmpty()) {
            return null;
        }
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);

        // the file history also carries the log of its commits, but the file is what was selected there
        VcsFileRevision fileRevision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
        if (fileRevision != null) {
            return getFileRevisionTarget(baseUrl, repositoryManager, fileRevision);
        }

        VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
        if (log != null) {
            return getCommitTarget(baseUrl, repositoryManager, log);
        }

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file != null) {
            return getWorkingCopyTarget(baseUrl, project, repositoryManager, file, e.getData(CommonDataKeys.EDITOR));
        }
        return null;
    }

    @Nullable
    private static Target getFileRevisionTarget(String baseUrl,
                                                GitRepositoryManager repositoryManager,
                                                VcsFileRevision fileRevision) {
        // the local version a history lists is not committed; the others know the path the file had back then,
        // which is not the current one after a rename
        if (!(fileRevision instanceof VcsFileRevisionEx) || ((VcsFileRevisionEx) fileRevision).isDeleted()) {
            return null;
        }
        FilePath path = ((VcsFileRevisionEx) fileRevision).getPath();
        GitRepository repository = repositoryManager.getRepositoryForFileQuick(path);
        if (repository == null) {
            return null;
        }
        GerritProject gerritProject = GerritRemotes.getGerritProject(repository, baseUrl);
        String relativePath = getRelativePath(repository.getRoot(), path.getPath());
        if (gerritProject == null || relativePath == null) {
            return null;
        }
        return new Target(baseUrl, repository, gerritProject, fileRevision.getRevisionNumber().asString(),
            relativePath, null);
    }

    @Nullable
    private static Target getCommitTarget(String baseUrl, GitRepositoryManager repositoryManager, VcsLog log) {
        List<CommitId> commits = log.getSelectedCommits();
        if (commits.size() != 1) {
            return null;
        }
        CommitId commit = commits.get(0);
        GitRepository repository = repositoryManager.getRepositoryForRootQuick(commit.getRoot());
        if (repository == null) {
            return null;
        }
        GerritProject gerritProject = GerritRemotes.getGerritProject(repository, baseUrl);
        if (gerritProject == null) {
            return null;
        }
        return new Target(baseUrl, repository, gerritProject, commit.getHash().asString(), null, null);
    }

    @Nullable
    private static Target getWorkingCopyTarget(String baseUrl,
                                               Project project,
                                               GitRepositoryManager repositoryManager,
                                               VirtualFile file,
                                               @Nullable Editor editor) {
        GitRepository repository = repositoryManager.getRepositoryForFileQuick(file);
        if (repository == null) {
            return null;
        }
        // Gitiles cannot show what has never been committed; for a directory only git can tell, once it runs
        FileStatus status = ChangeListManager.getInstance(project).getStatus(file);
        if (status == FileStatus.UNKNOWN || status == FileStatus.ADDED || status == FileStatus.IGNORED) {
            return null;
        }
        if (repository.getCurrentRevision() == null) { // nothing committed yet
            return null;
        }
        GerritProject gerritProject = GerritRemotes.getGerritProject(repository, baseUrl);
        String relativePath = getRelativePath(repository.getRoot(), file.getPath());
        if (gerritProject == null || relativePath == null) {
            return null;
        }
        Integer line = null;
        if (editor != null) {
            Document document = editor.getDocument();
            FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
            // git only gets to compare what is on disk
            if (file.equals(fileDocumentManager.getFile(document)) && !fileDocumentManager.isDocumentUnsaved(document)) {
                line = document.getLineNumber(editor.getSelectionModel().getSelectionStart()) + 1;
            }
        }
        return new Target(baseUrl, repository, gerritProject, null, relativePath, line);
    }

    @Nullable
    private static String resolveWorkingCopyUrl(Project project, Target target) throws VcsException {
        GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
        String revision = getRevisionOnGerrit(target.repository, target.gerritProject.remote);
        if (revision == null) {
            return null;
        }
        if (!target.path.isEmpty() && !gerritGitUtil.existsInRevision(target.repository, revision, target.path)) {
            NotificationService.getInstance().notifyWarning(new NotificationBuilder(project, "Cannot Open in Gitiles",
                String.format("'%s' is not part of commit %s yet.", target.path, revision)));
            return null;
        }
        Integer line = target.line;
        // a line of the file on disk is only the same line in the revision as long as the file is the same in both
        if (line != null && gerritGitUtil.differsFromRevision(target.repository, revision, target.path)) {
            line = null;
        }
        return target.getUrl(revision, line);
    }

    /**
     * Gerrit has only what was pushed, which HEAD often is not yet. What the current branch has in common with the
     * branch it tracks on Gerrit is; without such a branch, HEAD is the best guess there is.
     */
    @Nullable
    private static String getRevisionOnGerrit(GitRepository repository, GitRemote gerritRemote) throws VcsException {
        GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
        // a tracked branch which was never fetched is not there to compare with
        if (trackInfo != null && trackInfo.getRemote().equals(gerritRemote)
            && repository.getBranches().getHash(trackInfo.getRemoteBranch()) != null) {
            String mergeBase = GerritGitUtil.getInstance().getMergeBase(
                repository, trackInfo.getRemoteBranch().getNameForLocalOperations());
            if (mergeBase != null) {
                return mergeBase;
            }
        }
        return repository.getCurrentRevision();
    }

    /**
     * @return the path relative to the root, empty for the root itself, or {@code null} if it is not below the root
     */
    @Nullable
    private static String getRelativePath(VirtualFile root, String path) {
        String rootPath = root.getPath();
        if (!FileUtil.isAncestor(rootPath, path, false)) {
            return null;
        }
        String relativePath = FileUtil.getRelativePath(rootPath, path, '/');
        if (relativePath == null) {
            return null;
        }
        return ".".equals(relativePath) ? "" : relativePath;
    }

    private static final class Target {
        final String baseUrl;
        final GitRepository repository;
        final GerritProject gerritProject;
        /** {@code null} for the working copy, whose revision on Gerrit takes git to find */
        @Nullable final String revision;
        /** {@code null} for the commit itself */
        @Nullable final String path;
        @Nullable final Integer line;

        Target(String baseUrl, GitRepository repository, GerritProject gerritProject, @Nullable String revision,
               @Nullable String path, @Nullable Integer line) {
            this.baseUrl = baseUrl;
            this.repository = repository;
            this.gerritProject = gerritProject;
            this.revision = revision;
            this.path = path;
            this.line = line;
        }

        String getUrl(String revision, @Nullable Integer line) {
            return path == null
                ? GitilesUrls.getCommitUrl(baseUrl, gerritProject.name, revision)
                : GitilesUrls.getFileUrl(baseUrl, gerritProject.name, revision, path, line);
        }
    }
}
