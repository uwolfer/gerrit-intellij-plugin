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
    private final FulltextFilter fulltextFilter = new FulltextFilter();
    private final List<AbstractChangesFilter> filters;
    private final EventDispatcher<Listener> eventDispatcher = EventDispatcher.create(Listener.class);
    private final String defaultQuery;

    public GerritChangesFilters() {
        filters = List.of(
                fulltextFilter,
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
        defaultQuery = getQuery();
    }

    public void addListener(Listener listener) {
        eventDispatcher.addListener(listener);
    }

    @Override
    public void filterChanged() {
        // any other filter changed: back to the user's own filters, without the generated query
        fulltextFilter.endLookup();
        eventDispatcher.getMulticaster().filtersChanged();
    }

    /**
     * Shows what the query finds, whatever the other filters are set to, until the user changes any filter; only the
     * search field is taken over, and gets back the user's text then. Does not notify the listeners.
     */
    public void showLookup(String query) {
        fulltextFilter.showLookup(query);
    }

    public boolean isShowingLookup() {
        return fulltextFilter.isShowingLookup();
    }

    /**
     * Whether any filter is set to something else than at the start. What a lookup shows does not count: it
     * replaces the filters rather than narrowing them.
     */
    public boolean isNarrowed() {
        return !isShowingLookup() && !getQuery().equals(defaultQuery);
    }

    /**
     * Puts every filter back to what it is set to at the start, and ends a lookup. Does not notify the listeners:
     * the caller loads once, not once per filter.
     */
    public void reset() {
        filters.forEach(AbstractChangesFilter::reset);
    }

    public String getQuery() {
        if (fulltextFilter.isShowingLookup()) {
            return fulltextFilter.getSearchQueryPart();
        }
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
