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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.intellij.dvcs.push.CustomPushOptionsPanelFactory;
import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.Disposable;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Adds the Gerrit push settings to the push dialog.
 *
 * @author Urs Wolfer
 */
public class GerritPushOptionsPanelFactory implements CustomPushOptionsPanelFactory {

    @NotNull
    @Override
    public String getId() {
        return "com.urswolfer.intellij.plugin.gerrit.push";
    }

    @Nullable
    @Override
    public VcsPushOptionsPanel createOptionsPanel(@NotNull Disposable parentDisposable,
                                                  @NotNull Collection<? extends Repository> repos) {
        for (Repository repository : repos) {
            if (repository instanceof GitRepository) {
                return GerritPushPanelHolder.getInstance(repository.getProject()).getPanel();
            }
        }
        return null;
    }
}
