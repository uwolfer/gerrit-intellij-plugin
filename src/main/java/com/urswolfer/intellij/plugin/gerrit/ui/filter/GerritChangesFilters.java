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

import com.intellij.util.EventDispatcher;

import java.util.EventListener;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Thomas Forrer
 */
public class GerritChangesFilters implements AbstractChangesFilter.Listener {
    private final List<AbstractChangesFilter> filters;
    private final EventDispatcher<Listener> eventDispatcher = EventDispatcher.create(Listener.class);

    public GerritChangesFilters() {
        filters = List.of(
                new FulltextFilter(),
                new StatusFilter(),
                new BranchFilter(),
                new AssigneeFilter(),
                new ReviewerFilter(),
                new AttentionFilter(),
                new OwnerFilter(),
                new IsStarredFilter(),
                new ShowWIPFilter());
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
        return filters.stream()
                .map(AbstractChangesFilter::getSearchQueryPart)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("+"));
    }

    public Iterable<ChangesFilter> getFilters() {
        return List.<ChangesFilter>copyOf(filters);
    }

    public interface Listener extends EventListener {
        void filtersChanged();
    }
}
