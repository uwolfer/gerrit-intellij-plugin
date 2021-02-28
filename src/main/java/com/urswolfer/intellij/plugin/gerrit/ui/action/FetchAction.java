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
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.inject.Inject;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.repo.GitRepository;

import java.util.concurrent.Callable;

/**
 * @author Urs Wolfer
 */
public class FetchAction {
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private GerritGitUtil gerritGitUtil;
    @Inject
    private NotificationService notificationService;
    @Inject
    private SelectedRevisions selectedRevisions;

    public void fetchChange(ChangeInfo selectedChange, final Project project, final Callable<Void> fetchCallback) {
        gerritUtil.getChangeDetails(selectedChange._number, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeDetails) {

                Optional<GitRepository> gitRepository = gerritGitUtil.getRepositoryForGerritProject(project, changeDetails.project);
                if (!gitRepository.isPresent()) {
                    NotificationBuilder notification = new NotificationBuilder(project, "Error",
                        String.format("No repository found for Gerrit project: '%s'.", changeDetails.project));
                    notificationService.notifyError(notification);
                    return;
                }

                String commitHash = selectedRevisions.get(changeDetails);

                FetchInfo firstFetchInfo = gerritUtil.getFirstFetchInfo(changeDetails);
                if (firstFetchInfo == null) {
                    return;
                }
                gerritGitUtil.fetchChange(project, gitRepository.get(), firstFetchInfo, commitHash, fetchCallback);
            }
        });
    }

}
