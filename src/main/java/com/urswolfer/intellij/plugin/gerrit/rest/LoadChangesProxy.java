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

import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads the changes of one or more queries page by page. Several queries are merged newest first, the order Gerrit
 * lists changes in, so that splitting a long query does not change what the list shows.
 *
 * @author Thomas Forrer
 */
public class LoadChangesProxy {
    private static final int PAGE_SIZE = 25;

    private static final Comparator<ChangeInfo> NEWEST_FIRST = Comparator
        .comparing((ChangeInfo change) -> change.updated, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparingInt(change -> change._number)
        .reversed();

    private final List<Source> sources = new ArrayList<>();
    private final Set<Integer> listed = new HashSet<>();
    private final GerritUtil gerritUtil;
    private final Project project;
    private volatile boolean hasMore = true;
    private final AtomicBoolean loading = new AtomicBoolean(false);

    public LoadChangesProxy(List<Changes.QueryRequest> queryRequests,
                            GerritUtil gerritUtil,
                            Project project) {
        for (Changes.QueryRequest queryRequest : queryRequests) {
            sources.add(new Source(queryRequest));
        }
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
        load(PAGE_SIZE, consumer);
    }

    /**
     * Load the first changes into the provided consumer, as many as a page holds but at least the provided number,
     * so that a reload keeps the changes which were loaded by scrolling down. Later pages continue after them.
     */
    public void getFirstChanges(int minimum, final Consumer<List<ChangeInfo>> consumer) {
        load(Math.max(PAGE_SIZE, minimum), consumer);
    }

    private void load(int limit, final Consumer<List<ChangeInfo>> consumer) {
        if (!hasMore || !loading.compareAndSet(false, true)) {
            return;
        }
        gerritUtil.loadChanges(() -> {
            try {
                return next(limit);
            } catch (RuntimeException e) {
                // the consumer, which would reset it, is not called then
                loading.set(false);
                throw e;
            }
        }, project, new Consumer<List<ChangeInfo>>() {
            @Override
            public void consume(List<ChangeInfo> changeInfos) {
                try {
                    consumer.consume(changeInfos);
                } finally {
                    loading.set(false);
                }
            }
        });
    }

    /**
     * Takes the newest change of all queries until the page is full. A query is asked for more only once the
     * changes it returned so far are taken, so a single query is still one request per page. With several queries,
     * each is asked for at most a page, as most of what a query returns waits for the next page anyway.
     */
    @VisibleForTesting
    List<ChangeInfo> next(int limit) {
        List<ChangeInfo> page = new ArrayList<>();
        while (page.size() < limit) {
            int wanted = limit - page.size();
            int fetchLimit = sources.size() > 1 ? Math.min(wanted, PAGE_SIZE) : wanted;
            Source newest = null;
            for (Source source : sources) {
                ChangeInfo head = source.head(fetchLimit);
                if (source.failed) {
                    // the failure is already reported; stop rather than report it again for each query
                    sources.forEach(Source::stop);
                    newest = null;
                    break;
                }
                if (head != null && (newest == null || NEWEST_FIRST.compare(head, newest.pending.peek()) < 0)) {
                    newest = source;
                }
            }
            if (newest == null) {
                break;
            }
            ChangeInfo change = newest.pending.poll();
            listed.add(change._number);
            page.add(change);
        }
        hasMore = sources.stream().anyMatch(source -> source.hasMore || !source.pending.isEmpty());
        return page;
    }

    @VisibleForTesting
    boolean hasMore() {
        return hasMore;
    }

    private final class Source {
        private final Changes.QueryRequest queryRequest;
        private final Deque<ChangeInfo> pending = new ArrayDeque<>();
        private int fetched;
        private boolean hasMore = true;
        private boolean failed;

        private Source(Changes.QueryRequest queryRequest) {
            this.queryRequest = queryRequest;
        }

        /**
         * The next change of this query which is not listed yet, fetching up to the provided number when none is left.
         */
        @Nullable
        private ChangeInfo head(int limit) {
            while (true) {
                // a change updated while paging moves up, so a later page of its query has it again
                while (!pending.isEmpty() && listed.contains(pending.peek()._number)) {
                    pending.poll();
                }
                if (!pending.isEmpty() || !hasMore) {
                    return pending.peek();
                }
                fetch(limit);
                if (failed) {
                    return null;
                }
            }
        }

        private void fetch(int limit) {
            List<ChangeInfo> changeInfos =
                gerritUtil.queryChanges(queryRequest.withLimit(limit).withStart(fetched), project);
            if (changeInfos == null) {
                failed = true;
                return;
            }
            if (changeInfos.isEmpty()) {
                hasMore = false;
                return;
            }
            ChangeInfo lastChangeInfo = changeInfos.get(changeInfos.size() - 1);
            hasMore = lastChangeInfo._moreChanges != null && lastChangeInfo._moreChanges;
            fetched += changeInfos.size();
            pending.addAll(changeInfos);
        }

        private void stop() {
            hasMore = false;
            pending.clear();
        }
    }
}
