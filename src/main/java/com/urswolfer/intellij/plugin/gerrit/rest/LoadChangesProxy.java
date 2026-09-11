/*
 * Copyright 2013-2014 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Thomas Forrer
 */
public class LoadChangesProxy {
    private static final int PAGE_SIZE = 25;

    private final Changes.QueryRequest queryRequest;
    private final GerritUtil gerritUtil;
    private final Project project;
    private String sortkey;
    private boolean hasMore = true;
    private final List<ChangeInfo> changes = Lists.newArrayList();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    public LoadChangesProxy(Changes.QueryRequest queryRequest,
                            GerritUtil gerritUtil,
                            Project project) {
        this.queryRequest = queryRequest;
        this.gerritUtil = gerritUtil;
        this.project = project;
    }

    /**
     * Load the next page of changes into the provided consumer.
     *
     * Loading is asynchronous and this is called from the event dispatch thread (scrolling the change
     * list), so a load which is already running must never be waited for: the result is handled on the
     * event dispatch thread as well, which would deadlock. Such a call is skipped instead.
     */
    public void getNextPage(final Consumer<List<ChangeInfo>> consumer) {
        if (!hasMore || !loading.compareAndSet(false, true)) {
            return;
        }
        Changes.QueryRequest myRequest = queryRequest.withLimit(PAGE_SIZE).withStart(changes.size());
        // remove sortkey handling once we drop Gerrit < 2.9 support
        if (sortkey != null) {
            myRequest.withSortkey(sortkey);
        }
        Consumer<List<ChangeInfo>> myConsumer = new Consumer<List<ChangeInfo>>() {
            @Override
            public void consume(List<ChangeInfo> changeInfos) {
                try {
                    if (changeInfos != null && !changeInfos.isEmpty()) {
                        ChangeInfo lastChangeInfo = Iterables.getLast(changeInfos);
                        hasMore = lastChangeInfo._moreChanges != null && lastChangeInfo._moreChanges;
                        sortkey = lastChangeInfo._sortkey;
                        changes.addAll(changeInfos);
                    } else {
                        hasMore = false;
                    }
                    consumer.consume(changeInfos);
                } finally {
                    loading.set(false);
                }
            }
        };
        gerritUtil.getChanges(myRequest, project, myConsumer);
    }
}
