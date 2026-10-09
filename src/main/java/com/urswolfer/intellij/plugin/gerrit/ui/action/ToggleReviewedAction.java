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
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.ListedChangeFiles;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewedFilesService;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Marks the selected files of a change as reviewed, or as not reviewed when they all are: from the popup of the
 * changes tree, and of the editors of a diff. A diff opened marks its file reviewed, as Gerrit's web UI does; this
 * is how it is taken back, and a file marked not reviewed stays so until its diff is opened again.
 * <p>
 * TODO once the minimum IDE has AnAction.getActionUpdateThread (2020.3 has not, 2026.2 has): return
 *  ActionUpdateThread.EDT, which 2026.2 only infers from update() being overridden without UpdateInBackground.
 */
public class ToggleReviewedAction extends AnAction implements DumbAware {
    public static final String ID = "Gerrit.ToggleReviewed";

    /** Set on the editors of a diff of a Gerrit change, to say which file of which patch set they show. */
    public static final Key<Target> TARGET = Key.create("gerrit.ReviewedTarget");

    public static final class Target {
        final ChangeInfo change;
        final String revisionId;
        final String path;

        public Target(ChangeInfo change, String revisionId, String path) {
            this.change = change;
            this.revisionId = revisionId;
            this.path = path;
        }
    }

    private static final class Selection {
        final ChangeInfo change;
        final String revisionId;
        final List<String> paths;

        Selection(ChangeInfo change, String revisionId, List<String> paths) {
            this.change = change;
            this.revisionId = revisionId;
            this.paths = paths;
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        Selection selection = project == null ? null : findSelection(e, project);
        if (selection != null) {
            ReviewedFilesService.getInstance(project).toggle(selection.change, selection.revisionId, selection.paths);
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Selection selection = project == null ? null : findSelection(e, project);
        boolean enabled = selection != null
            && GerritProjectAccount.getInstance(project).isLoginAndPasswordAvailable();
        e.getPresentation().setEnabledAndVisible(enabled);
        if (enabled) {
            boolean mark = ReviewedFilesService.getInstance(project).toggleTarget(selection.change, selection.revisionId, selection.paths);
            e.getPresentation().setText(mark ? GerritBundle.message("reviewed.mark") : GerritBundle.message("reviewed.unmark"));
        }
    }

    @Nullable
    private static Selection findSelection(AnActionEvent e, Project project) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            editor = e.getData(DiffDataKeys.CURRENT_EDITOR);
        }
        Target target = editor != null ? editor.getUserData(TARGET) : null;
        if (target != null) {
            return new Selection(target.change, target.revisionId, List.of(target.path));
        }
        ChangesBrowserBase browser = e.getData(ChangesBrowserBase.DATA_KEY);
        if (!(browser instanceof ListedChangeFiles)) {
            return null;
        }
        ListedChangeFiles listed = (ListedChangeFiles) browser;
        ChangeInfo change = listed.getListedChange();
        String revisionId = listed.getListedRevision();
        // the reviewed files are those of the selected patch set, which the list may not show yet
        if (change == null || revisionId == null
            || !ReviewedFilesService.getInstance(project).isSelected(change, revisionId)) {
            return null;
        }
        Optional<GitRepository> repository = GerritGitUtil.getInstance().getRepositoryForChange(project, change);
        List<String> paths = new ArrayList<>();
        for (Change selected : listed.getSelectedChanges()) {
            paths.add(PathUtils.ensureSlashSeparators(
                PathUtils.getRelativeOrAbsolutePath(repository, ChangesUtil.getFilePath(selected).getPath())));
        }
        return paths.isEmpty() ? null : new Selection(change, revisionId, paths);
    }
}
