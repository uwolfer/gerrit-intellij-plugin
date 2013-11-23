package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * @author Thomas Forrer
 */
public class GerritFilterModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<AbstractChangesFilter> filters = Multibinder.newSetBinder(binder(), AbstractChangesFilter.class);
        filters.addBinding().to(FulltextFilter.class);
        filters.addBinding().to(StatusFilter.class);
        filters.addBinding().to(BranchFilter.class);
        filters.addBinding().to(ReviewerFilter.class);
        filters.addBinding().to(OwnerFilter.class);
        filters.addBinding().to(IsStarredFilter.class);

        bind(GerritChangesFilters.class);
    }
}
