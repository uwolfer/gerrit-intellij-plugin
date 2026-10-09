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

import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * What the filters differ from their defaults by; a filter at its default adds nothing.
     */
    @NotNull
    public Map<String, String> saveState() {
        Map<String, String> state = new HashMap<>();
        for (AbstractChangesFilter filter : filters) {
            filter.saveState(state);
        }
        return state;
    }

    /**
     * Takes over a state saved by {@link #saveState}; a filter whose value is missing or does not fit any more is at
     * its default. Does not notify the listeners, as the list is not loaded yet when this is called.
     */
    public void restoreState(@NotNull Map<String, String> state, @NotNull FilterEnvironment environment) {
        for (AbstractChangesFilter filter : filters) {
            filter.restoreState(state, environment);
        }
    }

    /**
     * Takes over what was saved for the project, if the filters are not shown yet.
     */
    public void restore(@NotNull Project project) {
        restoreState(GerritProjectSettings.getInstance(project).getFilters(), environmentOf(project));
    }

    public void save(@NotNull Project project) {
        GerritProjectSettings.getInstance(project).setFilters(saveState());
    }

    private static FilterEnvironment environmentOf(Project project) {
        return new FilterEnvironment() {
            @Override
            public boolean isLoggedIn() {
                GerritAccount account = GerritProjectAccount.getInstance(project).get();
                return account != null && !account.login.isEmpty();
            }

            @NotNull
            @Override
            public Map<String, Set<String>> getRemoteBranches() {
                return BranchFilter.getRemoteBranches(project);
            }
        };
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
