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

import com.google.inject.AbstractModule;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import git4idea.commands.Git;

/**
 * Bindings for all dependencies to required OpenIDE service instances.
 *
 * If you want to avoid calls to #getInstance() register the required
 * idea service here and inject it in your own service.
 *
 * @author Thomas Forrer
 */
public class OpenIdeDependenciesModule extends AbstractModule {
    public static final Logger LOG = Logger.getInstance("gerrit");

    @Override
    protected void configure() {
        bind(Logger.class).toInstance(LOG);
        bind(Application.class).toInstance(ApplicationManager.getApplication());

        bind(LocalFileSystem.class).toInstance(LocalFileSystem.getInstance());

        bind(Git.class).toInstance(ServiceManager.getService(Git.class));
        bind(VirtualFileManager.class).toInstance(VirtualFileManager.getInstance());

        bind(ShowSettingsUtil.class).toInstance(ShowSettingsUtil.getInstance());
        bind(DataManager.class).toInstance(DataManager.getInstance());

        bind(JBPopupFactory.class).toInstance(JBPopupFactory.getInstance());
    }
}
