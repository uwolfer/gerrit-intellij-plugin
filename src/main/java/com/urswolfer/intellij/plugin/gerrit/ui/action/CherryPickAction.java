/*
 * Copyright 2013 Urs Wolfer
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

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import icons.DvcsImplIcons;

import java.util.concurrent.Callable;

/**
 * @author Urs Wolfer
 */
public class CherryPickAction extends AbstractChangeAction {
    private final GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
    private final FetchAction fetchAction = new FetchAction();

    public CherryPickAction() {
        super("Cherry-Pick (No Commit)", "Cherry-Pick change into active changelist without committing", DvcsImplIcons.CherryPick);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        final Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        final Project project = anActionEvent.getProject();

        getChangeDetail(selectedChange.get(), project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(final ChangeInfo changeInfo) {
                Callable<Void> fetchCallback = new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                gerritGitUtil.cherryPickChange(project, changeInfo,
                                    SelectedRevisions.getInstance(project).get(changeInfo));
                            }
                        });
                        return null;
                    }
                };
                fetchAction.fetchChange(selectedChange.get(), project, fetchCallback);
            }
        });
    }

}
