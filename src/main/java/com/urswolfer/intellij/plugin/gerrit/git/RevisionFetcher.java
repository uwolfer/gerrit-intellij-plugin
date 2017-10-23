/*
 *
 *  * Copyright 2013-2014 Urs Wolfer
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.urswolfer.intellij.plugin.gerrit.git;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.repo.GitRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This class helps to simultaneously fetch multiple revisions from a git repository.
 *
 * @author Thomas Forrer
 */
public class RevisionFetcher {
    private final GerritUtil gerritUtil;
    private final GerritGitUtil gerritGitUtil;
    private final NotificationService notificationService;
    private final Project project;
    private final GitRepository gitRepository;

    private final Map<String, RevisionInfo> revisionInfoList = Maps.newLinkedHashMap();
    private final List<FetchCallback> fetchCallbacks = Lists.newArrayList();

    public RevisionFetcher(GerritUtil gerritUtil,
                           GerritGitUtil gerritGitUtil,
                           NotificationService notificationService,
                           Project project,
                           GitRepository gitRepository) {
        this.gerritUtil = gerritUtil;
        this.gerritGitUtil = gerritGitUtil;
        this.notificationService = notificationService;
        this.project = project;
        this.gitRepository = gitRepository;
    }

    public RevisionFetcher addRevision(String commitHash, RevisionInfo revisionInfo) {
        revisionInfoList.put(commitHash, revisionInfo);
        return this;
    }

    /**
     * Fetch the changes for the provided revisions.
     * @param callback the callback will be executed as soon as all revisions have been fetched successfully
     */
    public void fetch(final Callable<Void> callback) {
        for (Map.Entry<String, RevisionInfo> entry : revisionInfoList.entrySet()) {
            FetchCallback fetchCallback = new FetchCallback(callback);
            fetchCallbacks.add(fetchCallback);
            fetchChange(entry.getKey(), entry.getValue(), fetchCallback);
        }
    }

    private void fetchChange(String commitHash, RevisionInfo revisionInfo, FetchCallback fetchCallback) {
        FetchInfo fetchInfo = gerritUtil.getFirstFetchInfo(revisionInfo);
        if (fetchInfo == null) {
            notifyError();
        } else {
            gerritGitUtil.fetchChange(project, gitRepository, fetchInfo, commitHash, fetchCallback);
        }
    }

    private void notifyError() {
        NotificationBuilder notification = new NotificationBuilder(
                project, "Cannot fetch changes",
                "No fetch information provided. If you are using Gerrit 2.8 or later, " +
                        "you need to install the plugin 'download-commands' in Gerrit."
        );
        notificationService.notifyError(notification);
    }

    private final class FetchCallback implements Callable<Void> {
        private final Callable<Void> callback;
        private boolean returned = false;

        private FetchCallback(Callable<Void> callback) {
            this.callback = callback;
        }

        @Override
        public Void call() throws Exception {
            try {
                return null;
            } finally {
                returned = true;
                if (allFetchCallbacksReturned()) {
                    callback.call();
                }
            }
        }

        private synchronized boolean allFetchCallbacksReturned() {
            return Iterables.all(fetchCallbacks, new Predicate<FetchCallback>() {
                @Override
                public boolean apply(FetchCallback fetchCallback) {
                    return fetchCallback.returned;
                }
            });
        }
    }
}
