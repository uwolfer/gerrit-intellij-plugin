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

import java.util.Observable;
import java.util.Observer;
import java.util.Set;

/**
 * @author Thomas Forrer
 */
public class GerritChangesFilters extends Observable implements Observer {
    private final Set<AbstractChangesFilter> filters;

    @Inject
    public GerritChangesFilters(Set<AbstractChangesFilter> filters) {
        this.filters = filters;
        for (AbstractChangesFilter filter : this.filters) {
            filter.addObserver(this);
        }
    }

    @Override
    public void update(Observable observable, Object o) {
        setChanged();
        notifyObservers();
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
}
