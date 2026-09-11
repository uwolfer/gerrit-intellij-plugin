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

package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.intellij.util.EventDispatcher;

import java.util.EventListener;
import java.util.List;

/**
 * @author Thomas Forrer
 */
public class GerritChangesFilters implements AbstractChangesFilter.Listener {
    private final List<AbstractChangesFilter> filters;
    private final EventDispatcher<Listener> eventDispatcher = EventDispatcher.create(Listener.class);

    @Inject
    public GerritChangesFilters(FulltextFilter fulltextFilter,
                                StatusFilter statusFilter,
                                BranchFilter branchFilter,
                                AssigneeFilter assigneeFilter,
                                ReviewerFilter reviewerFilter,
                                AttentionFilter attentionFilter,
                                OwnerFilter ownerFilter,
                                IsStarredFilter isStarredFilter,
                                ShowWIPFilter showWipFilter) {
        filters = ImmutableList.<AbstractChangesFilter>of(
                fulltextFilter,
                statusFilter,
                branchFilter,
                assigneeFilter,
                reviewerFilter,
                attentionFilter,
                ownerFilter,
                isStarredFilter,
                showWipFilter);
        for (AbstractChangesFilter filter : filters) {
            filter.addListener(this);
        }
    }

    public void addListener(Listener listener) {
        eventDispatcher.addListener(listener);
    }

    @Override
    public void filterChanged() {
        eventDispatcher.getMulticaster().filtersChanged();
    }

    public String getQuery() {
        return Joiner.on("+").skipNulls()
                .join(Iterables.transform(filters, new Function<AbstractChangesFilter, String>() {
            @Override
            public String apply(AbstractChangesFilter abstractChangesFilter) {
                return abstractChangesFilter.getSearchQueryPart();
            }
        }));
    }

    public Iterable<ChangesFilter> getFilters() {
        return ImmutableList.<ChangesFilter>copyOf(filters);
    }

    public interface Listener extends EventListener {
        void filtersChanged();
    }
}
