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

package com.urswolfer.intellij.plugin.gerrit.extension;

import com.intellij.util.AuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import git4idea.jgit.GitHttpAuthDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Thomas Forrer
 */
public class GerritHttpAuthDataProviderProxy implements GitHttpAuthDataProvider {
    private final GerritHttpAuthDataProvider delegate;

    public GerritHttpAuthDataProviderProxy() {
        delegate = GerritModule.getInstance(GerritHttpAuthDataProvider.class);
    }

    @Nullable
    @Override
    public AuthData getAuthData(@NotNull String url) {
        return delegate.getAuthData(url);
    }
}
