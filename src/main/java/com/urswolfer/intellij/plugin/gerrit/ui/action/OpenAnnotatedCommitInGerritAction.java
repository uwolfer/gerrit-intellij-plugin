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
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcsUtil.VcsUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.CommitChanges;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitUtil;
import git4idea.annotate.GitFileAnnotation;
import git4idea.repo.GitRepository;
import icons.MyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Opens the Gerrit change of the commit which last changed a line, from the context menu of the annotations.
 *
 * The lookup runs in the background; the action itself only reads what the annotation has in memory.
 *
 * TODO once the minimum IDE has ShowAnnotateOperationsPopup.getAnnotationLineNumber(DataContext) (2020.3 has not,
 * 2026.2 has): read the line from the event, and drop {@link UpToDateLineNumberListener} (deprecated by now) and
 * {@link #line}.
 *
 * @author Urs Wolfer
 */
public class OpenAnnotatedCommitInGerritAction extends AnAction implements UpToDateLineNumberListener, DumbAware {

    private final FileAnnotation annotation;
    /**
     * The platform hands the clicked line over just before it shows the menu.
     */
    private volatile int line = -1;

    public OpenAnnotatedCommitInGerritAction(@NotNull FileAnnotation annotation) {
        super(GerritBundle.message("action.Gerrit.OpenAnnotatedCommitInGerrit.text"),
            GerritBundle.message("action.Gerrit.OpenAnnotatedCommitInGerrit.description"), MyIcons.Gerrit);
        this.annotation = annotation;
    }

    @Override
    public void consume(Integer line) {
        this.line = line;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = annotation.getProject();
        e.getPresentation().setEnabledAndVisible(project != null && getTarget(project) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = annotation.getProject();
        Target target = project == null ? null : getTarget(project);
        if (target == null) {
            return;
        }
        GerritUtil.getInstance().getChangesByCommit(target.hash, project, changes -> show(project, target, changes));
    }

    private static void show(Project project, Target target, List<ChangeInfo> changes) {
        ChangeInfo change = CommitChanges.pickChange(changes, target.projectName);
        if (change == null) {
            NotificationService.getInstance().notifyInformation(new NotificationBuilder(project,
                GerritBundle.message("action.Gerrit.OpenAnnotatedCommitInGerrit.notFound.title"),
                GerritBundle.message("action.Gerrit.OpenAnnotatedCommitInGerrit.notFound", target.hash.substring(0, 8))));
            return;
        }
        BrowserUtil.browse(OpenInBrowserAction.getUrl(GerritProjectAccount.getInstance(project).getHost(), change));
    }

    /**
     * @return the commit of the clicked line, and the Gerrit project it was made in, or {@code null} when the line has
     *         no commit yet or the file is in no repository of a Gerrit project
     */
    @Nullable
    private Target getTarget(Project project) {
        if (!(annotation instanceof GitFileAnnotation) || line < 0 || !GerritProjectSettings.isEnabled(project)
            || GerritProjectAccount.getInstance(project).getHost().isEmpty()) {
            return null;
        }
        VcsRevisionNumber revision = annotation.getLineRevisionNumber(line);
        String hash = revision == null ? null : revision.asString();
        if (!CommitChanges.isCommitHash(hash)) {
            return null;
        }
        GitRepository repository = GitUtil.getRepositoryManager(project)
            .getRepositoryForFileQuick(VcsUtil.getFilePath(annotation.getFile()));
        GerritRemotes.GerritProject gerritProject = repository == null ? null : GerritRemotes.getGerritProject(repository, null);
        return gerritProject == null ? null : new Target(hash, gerritProject.name);
    }

    private static final class Target {
        final String hash;
        final String projectName;

        Target(String hash, String projectName) {
            this.hash = hash;
            this.projectName = projectName;
        }
    }
}
