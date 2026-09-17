/*
 * Copyright 2026 Urs Wolfer
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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfigurableProvider;
import org.jetbrains.annotations.Nullable;

/**
 * The settings page is shown under the version control settings of a project, so it gets one configurable per
 * project, the way the platform builds its own version control configurables. The extension itself is a single
 * application-level instance and must not be that configurable: it would share its panel between all open projects.
 *
 * @author Urs Wolfer
 */
public class GerritSettingsConfigurableProvider implements VcsConfigurableProvider {

    @Nullable
    @Override
    public Configurable getConfigurable(Project project) {
        return new GerritSettingsConfigurable(project);
    }
}
