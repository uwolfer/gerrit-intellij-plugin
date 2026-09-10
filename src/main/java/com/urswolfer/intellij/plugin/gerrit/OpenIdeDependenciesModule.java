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
import com.intellij.openapi.diagnostic.Logger;

/**
 * Binds the plugin's shared logger, which is the only OpenIDE instance still routed through Guice.
 *
 * Do not add IDE services or components here: they expose a #getInstance() of their own, so call that
 * where you need one.
 *
 * @author Thomas Forrer
 */
public class OpenIdeDependenciesModule extends AbstractModule {
    public static final Logger LOG = Logger.getInstance("gerrit");

    @Override
    protected void configure() {
        bind(Logger.class).toInstance(LOG);
    }
}
