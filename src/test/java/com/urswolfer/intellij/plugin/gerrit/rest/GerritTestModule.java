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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.OpenIdeDependenciesModule;
import git4idea.commands.Git;
import org.easymock.EasyMock;

/**
 * @author Urs Wolfer
 */
public class GerritTestModule extends GerritModule {

    protected static final Supplier<Injector> injector = Suppliers.memoize(new Supplier<Injector>() {
        @Override
        public Injector get() {
            return Guice.createInjector(new GerritTestModule());
        }
    });

    public static <T> T getInstance(Class<T> type) {
        return injector.get().getInstance(type);
    }

    @Override
    protected void installOpenIdeDependenciesModule() {
        bind(Logger.class).toInstance(OpenIdeDependenciesModule.LOG);
        bindMock(Application.class);

        bindMock(LocalFileSystem.class);

        bindMock(Git.class);
        bindMock(VirtualFileManager.class);

        bindMock(ShowSettingsUtil.class);
        bindMock(DataManager.class);

        bindMock(JBPopupFactory.class);
    }

    @SuppressWarnings("unchecked")
    protected void bindMock(Class toMock) {
        bind(toMock).toInstance(EasyMock.createNiceMock(toMock));
    }

    @Override
    protected void setupSettingsProvider() {
        bindMock(GerritSettings.class);
        bindMock(GerritAuthData.class);
    }
}
