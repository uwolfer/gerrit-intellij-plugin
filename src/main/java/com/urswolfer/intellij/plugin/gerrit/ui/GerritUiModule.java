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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.GerritFilterModule;

/**
 * @author Thomas Forrer
 */
public class GerritUiModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new GerritFilterModule());
        bind(GerritSelectRevisionInfoColumn.class);
        Multibinder<GerritChangeNodeDecorator> decorators = Multibinder.newSetBinder(binder(), GerritChangeNodeDecorator.class);
        decorators.addBinding().to(GerritCommentCountChangeNodeDecorator.class);
        bind(RepositoryChangesBrowserProvider.class);
        bind(SettingsPanel.class);
        bind(GerritSettingsConfigurable.class);
        bind(GerritUpdatesNotificationComponent.class).asEagerSingleton();
        bind(GerritChangeListPanel.class);
    }
}
