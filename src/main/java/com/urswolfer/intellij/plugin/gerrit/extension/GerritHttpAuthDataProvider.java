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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.remote.GitHttpAuthDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GerritHttpAuthDataProvider implements GitHttpAuthDataProvider {

    private final GerritSettings gerritSettings = GerritSettings.getInstance();

    @Override
    public @Nullable AuthData getAuthData(@NotNull Project project, @NotNull String url) {
        if (!isGerritUrl(url)) {
            return null;
        }
        String login = gerritSettings.getLogin();
        if (StringUtil.isEmptyOrSpaces(login)) {
            return null;
        }
        gerritSettings.preloadPassword(); // Git asks for the credentials from a background thread
        String password = gerritSettings.getPassword();
        if (StringUtil.isEmptyOrSpaces(password)) {
            return null;
        }
        return new AuthData(login, password);
    }

    @Override
    public void forgetPassword(@NotNull Project project, @NotNull String url, @NotNull AuthData authData) {
        if (isGerritUrl(url)) {
            gerritSettings.forgetPassword();
        }
    }

    /**
     * Git asks for the credentials of a repository url (e.g. "https://gerrit.example.com/my-project"), which is never
     * equal to the configured Gerrit url: the host is what they have in common.
     */
    private boolean isGerritUrl(String url) {
        return hasSameHost(url, gerritSettings.getHost())
            || hasSameHost(url, gerritSettings.getCloneBaseUrl());
    }

    private boolean hasSameHost(String url, String gerritUrl) {
        if (StringUtil.isEmptyOrSpaces(gerritUrl)) {
            return false;
        }
        try {
            return UrlUtils.urlHasSameHost(url, gerritUrl);
        } catch (IllegalArgumentException e) { // not a url, so it cannot point to the Gerrit host
            return false;
        }
    }
}
