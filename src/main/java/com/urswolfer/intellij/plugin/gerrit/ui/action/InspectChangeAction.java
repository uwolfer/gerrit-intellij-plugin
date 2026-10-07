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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.repo.GitRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class InspectChangeAction extends AbstractChangeAction {
    private static final String TITLE = "Inspect Change";
    // the platform's own Inspect Code offers the files of its data context as a scope in its dialog, next to the
    // inspection profile; 2020.3 and 2026.2 register it under this id alike
    private static final String INSPECT_CODE_ACTION = "InspectCode";

    private final GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
    private final FetchAction fetchAction = new FetchAction();
    private final CheckoutAction checkoutAction = new CheckoutAction();
    private final NotificationService notificationService = NotificationService.getInstance();

    public InspectChangeAction() {
        super(AllIcons.General.InspectionsEye);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        final Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        final Project project = anActionEvent.getProject();
        if (!selectedChange.isPresent() || project == null) {
            return;
        }
        // the files on disk are compared with the patch set, and Inspect Code would save the editors anyway
        FileDocumentManager.getInstance().saveAllDocuments();
        getChangeDetail(selectedChange.get(), project, changeDetails ->
            fetchAction.fetchChange(selectedChange.get(), project,
                (repository, commitHash) -> collectFiles(project, changeDetails, repository, commitHash)));
    }

    private void collectFiles(Project project, ChangeInfo changeDetails, GitRepository repository, String commitHash) {
        final List<FilePath> files;
        try {
            // a submodule which the change moves is a directory, which a checkout does not update and which would
            // bring in all its files
            files = gerritGitUtil.getFilesOfRevision(project, repository, commitHash).stream()
                .filter(file -> !file.getIOFile().isDirectory())
                .collect(Collectors.toList());
        } catch (VcsException e) {
            notificationService.notifyError(new NotificationBuilder(project, "Cannot inspect change", e.getMessage()));
            return;
        }
        if (files.isEmpty()) {
            notificationService.notifyInformation(new NotificationBuilder(project, TITLE,
                "Change " + changeDetails._number + " leaves no files to inspect."));
            return;
        }
        compareWithDisk(project, changeDetails, repository, commitHash, files);
    }

    private void compareWithDisk(Project project, ChangeInfo changeDetails, GitRepository repository, String commitHash,
                                 List<FilePath> files) {
        if (project.isDisposed()) {
            return;
        }
        final int differing;
        try {
            differing = gerritGitUtil.countDifferingFromRevision(repository, commitHash, files);
        } catch (VcsException e) {
            notificationService.notifyError(new NotificationBuilder(project, "Cannot inspect change", e.getMessage()));
            return;
        }
        // a checkout would keep the local changes, so it is offered only when another commit is checked out
        final boolean checkedOut = commitHash.equals(repository.getCurrentRevision());
        ApplicationManager.getApplication().invokeLater(() -> {
            if (differing == 0) {
                inspect(project, files);
                return;
            }
            // the inspections read the files on disk, so a patch set which is not checked out would quietly be
            // judged by other code
            String patchSet = patchSetName(changeDetails, commitHash);
            if (checkedOut) {
                String message = String.format("%s is checked out, but %d of its %d files have local changes.",
                    patchSet, differing, files.size());
                if (Messages.showOkCancelDialog(project, message, TITLE, "Inspect Files as They Are",
                        Messages.getCancelButton(), Messages.getQuestionIcon()) == Messages.OK) {
                    inspect(project, files);
                }
                return;
            }
            String message = String.format("%s is not checked out, and %d of its %d files differ from it on disk.",
                patchSet, differing, files.size());
            int answer = Messages.showYesNoCancelDialog(project, message, TITLE,
                "Check Out and Inspect", "Inspect Files as They Are", Messages.getCancelButton(),
                Messages.getQuestionIcon());
            if (answer == Messages.YES) {
                // CheckoutAction runs this from the background task of the fetch; afterwards the files are compared
                // again, as a checkout keeps local changes
                ApplicationManager.getApplication().executeOnPooledThread(() -> checkoutAction.checkout(
                    project, changeDetails, repository, commitHash,
                    () -> ApplicationManager.getApplication().executeOnPooledThread(
                        () -> compareWithDisk(project, changeDetails, repository, commitHash, files))));
            } else if (answer == Messages.NO) {
                inspect(project, files);
            }
        }, project.getDisposed());
    }

    private static String patchSetName(ChangeInfo changeDetails, String commitHash) {
        RevisionInfo revision = changeDetails.revisions == null ? null : changeDetails.revisions.get(commitHash);
        return revision == null
            ? "Change " + changeDetails._number
            : "Patch set " + revision._number + " of change " + changeDetails._number;
    }

    private void inspect(Project project, List<FilePath> files) {
        // the files were compared on disk, where the VFS may not have seen a change made outside the IDE yet, such
        // as a checkout in a terminal; the refreshes are synchronous, which a large change must not do on the event
        // dispatch thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            LocalFileSystem fileSystem = LocalFileSystem.getInstance();
            List<VirtualFile> virtualFiles = files.stream()
                .map(file -> {
                    VirtualFile known = fileSystem.findFileByIoFile(file.getIOFile());
                    return known != null ? known : fileSystem.refreshAndFindFileByIoFile(file.getIOFile());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            VfsUtil.markDirtyAndRefresh(false, false, false, virtualFiles.toArray(VirtualFile.EMPTY_ARRAY));
            if (!project.isDisposed()) {
                inspectInProject(project, virtualFiles);
            }
        });
    }

    private void inspectInProject(Project project, List<VirtualFile> virtualFiles) {
        // Inspect Code is disabled while indexing
        DumbService.getInstance(project).smartInvokeLater(() -> {
            ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
            VirtualFile[] inProject = virtualFiles.stream()
                .filter(file -> file.isValid() && !file.isDirectory() && fileIndex.isInContent(file))
                .toArray(VirtualFile[]::new);
            if (inProject.length == 0) {
                notificationService.notifyInformation(new NotificationBuilder(project, TITLE,
                    "None of the files of the change are on disk and part of the project, so there is nothing to inspect."));
                return;
            }
            AnAction inspectCode = ActionManager.getInstance().getAction(INSPECT_CODE_ACTION);
            if (inspectCode == null) {
                notificationService.notifyError(new NotificationBuilder(project, TITLE,
                    "This IDE has no Inspect Code action."));
                return;
            }
            // no PSI_FILE or PROJECT_CONTEXT, which Inspect Code would take over the files
            Map<String, Object> data = new HashMap<String, Object>();
            data.put(CommonDataKeys.PROJECT.getName(), project);
            data.put(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName(), inProject);
            com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(inspectCode,
                SimpleDataContext.getSimpleContext(data, null), ActionPlaces.UNKNOWN, null, null);
        });
    }
}
