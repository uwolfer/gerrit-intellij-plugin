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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.remote.GitHttpAuthDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * Parts based on org.jetbrains.plugins.github.extensions.GithubHttpAuthDataProvider
 *
 * @author Urs Wolfer
 * @author Kirill Likhodedov
 */
public class GerritHttpAuthDataProvider implements GitHttpAuthDataProvider {

    @Override
    public @Nullable AuthData getAuthData(@NotNull Project project, @NotNull String url) {
        GerritAccount account = accountFor(project, url);
        if (account == null || StringUtil.isEmptyOrSpaces(account.login)) {
            return null;
        }
        String password = GerritAccounts.getInstance().getPassword(account);
        if (StringUtil.isEmptyOrSpaces(password)) {
            return null;
        }
        return new AuthData(account.login, password);
    }

    @Override
    public void forgetPassword(@NotNull Project project, @NotNull String url, @NotNull AuthData authData) {
        GerritAccount account = accountFor(project, url);
        if (account != null) {
            GerritAccounts.getInstance().forgetPassword(account);
        }
    }

    /**
     * The url says which instance git talks to, and it need not be the one of the project: a submodule can live on
     * another, and a project with several accounts to choose between may not have chosen yet. The account of the
     * project goes first; another one only when it is the only one on that instance, as two logins there leave no
     * way to tell whose credentials git wants.
     */
    @Nullable
    private GerritAccount accountFor(Project project, String url) {
        GerritAccount own = GerritProjectAccount.getInstance(project).get();
        if (own != null && isGerritUrl(own, url)) {
            return own;
        }
        GerritAccount match = null;
        for (GerritAccount account : GerritAccounts.getInstance().getAccounts()) {
            if (isGerritUrl(account, url)) {
                if (match != null) {
                    return null;
                }
                match = account;
            }
        }
        return match;
    }

    /**
     * Git asks for the credentials of a repository url (e.g. "https://gerrit.example.com/my-project"), which is never
     * equal to the configured Gerrit url: they have the origin in common.
     */
    private boolean isGerritUrl(GerritAccount account, String url) {
        return hasSameOrigin(url, account.host)
            || hasSameOrigin(url, account.cloneBaseUrl);
    }

    /**
     * The password is only handed out for the Gerrit instance itself: the scheme and the port have to match as well,
     * so that it does not end up at another service on the same host, or on an unencrypted connection.
     */
    private boolean hasSameOrigin(String url, String gerritUrl) {
        if (StringUtil.isEmptyOrSpaces(gerritUrl)) {
            return false;
        }
        try {
            if (!UrlUtils.urlHasSameHost(url, gerritUrl)) {
                return false;
            }
            URI repositoryUri = UrlUtils.createUriFromGitConfigString(url);
            URI gerritUri = URI.create(gerritUrl);
            return repositoryUri.getScheme() != null
                && repositoryUri.getScheme().equalsIgnoreCase(gerritUri.getScheme())
                && port(repositoryUri) == port(gerritUri);
        } catch (IllegalArgumentException e) { // not a url, so it cannot point to the Gerrit instance
            return false;
        }
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
