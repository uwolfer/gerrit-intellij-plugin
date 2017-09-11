/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.google.inject.Inject;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import git4idea.remote.GitHttpAuthDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parts based on org.jetbrains.plugins.github.extensions.GithubHttpAuthDataProvider
 *
 * @author Urs Wolfer
 * @author Kirill Likhodedov
 */
public class GerritHttpAuthDataProvider implements GitHttpAuthDataProvider {

    @Inject
    private GerritSettings gerritSettings;

    @Nullable
    @Override
    public AuthData getAuthData(@NotNull String url) {
        if (!gerritSettings.getHost().equalsIgnoreCase(url)) {
            return null;
        }
        String password = gerritSettings.getPassword();
        if (StringUtil.isEmptyOrSpaces(gerritSettings.getLogin()) || StringUtil.isEmptyOrSpaces(password)) {
            return null;
        }
        return new AuthData(gerritSettings.getLogin(), password);
    }

    @Override
    public void forgetPassword(@NotNull String url) {
        if (gerritSettings.getHost().equalsIgnoreCase(url)) {
            gerritSettings.forgetPassword();
        }
    }

    public static final class Proxy implements GitHttpAuthDataProvider {
        private final GitHttpAuthDataProvider delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(GerritHttpAuthDataProvider.class);
        }

        @Nullable
        @Override
        public AuthData getAuthData(@NotNull String url) {
            return delegate.getAuthData(url);
        }

        @Override
        public void forgetPassword(@NotNull String url) {
            delegate.forgetPassword(url);
        }
    }
}
