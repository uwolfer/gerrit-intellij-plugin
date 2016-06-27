/*
 * Copyright 2013-2016 Urs Wolfer
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

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;

/**
 * @author Urs Wolfer
 */
public final class Version {

    private static final String PLUGIN_VERSION = PluginManager.getPlugin(PluginId.getId("com.urswolfer.intellij.plugin.gerrit")).getVersion();

    private Version() {}
    public static String get() {
        return PLUGIN_VERSION;
    }

}
