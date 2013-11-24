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
