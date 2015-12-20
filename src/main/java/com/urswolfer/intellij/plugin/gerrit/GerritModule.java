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

package com.urswolfer.intellij.plugin.gerrit;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.intellij.openapi.components.ServiceManager;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.extension.GerritCheckoutProvider;
import com.urswolfer.intellij.plugin.gerrit.extension.GerritHttpAuthDataProvider;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtension;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritRestModule;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindow;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritUiModule;
import com.urswolfer.intellij.plugin.gerrit.ui.action.GerritActionsModule;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.GerritDiffModule;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import com.urswolfer.intellij.plugin.gerrit.util.UtilsModule;

/**
 * @author Thomas Forrer
 */
public class GerritModule extends AbstractModule {

    protected static final Supplier<Injector> injector = Suppliers.memoize(new Supplier<Injector>() {
        @Override
        public Injector get() {
            return Guice.createInjector(new GerritModule());
        }
    });

    public static <T> T getInstance(Class<T> type) {
        return injector.get().getInstance(type);
    }

    protected GerritModule() {}

    @Override
    protected void configure() {
        installOpenIdeDependenciesModule();

        setupSettingsProvider();

        bind(NotificationService.class);
        bind(SelectedRevisions.class).toInstance(new SelectedRevisions());

        bind(GerritGitUtil.class);
        bind(GerritUtil.class);

        bind(GerritToolWindow.class);
        bind(GerritCheckoutProvider.class);
        bind(GerritHttpAuthDataProvider.class);
        bind(GerritPushExtension.class);

        install(new UtilsModule());
        install(new GerritActionsModule());
        install(new GerritDiffModule());
        install(new GerritRestModule());
        install(new GerritUiModule());
    }

    protected void setupSettingsProvider() {
        Provider<GerritSettings> settingsProvider = new Provider<GerritSettings>() {
            @Override
            public GerritSettings get() {
                // GerritSettings instance needs to be retrieved from ServiceManager, need to inject the Logger manually...
                GerritSettings gerritSettings = ServiceManager.getService(GerritSettings.class);
                gerritSettings.setLog(OpenIdeDependenciesModule.LOG);
                return gerritSettings;
            }
        };
        bind(GerritSettings.class).toProvider(settingsProvider).in(Singleton.class);
        bind(GerritAuthData.class).toProvider(settingsProvider).in(Singleton.class);
    }

    protected void installOpenIdeDependenciesModule() {
        install(new OpenIdeDependenciesModule());
    }
}
