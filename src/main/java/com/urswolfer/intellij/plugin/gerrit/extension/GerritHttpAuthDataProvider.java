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

import com.intellij.openapi.application.ApplicationInfo;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Parts based on org.jetbrains.plugins.github.extensions.GithubHttpAuthDataProvider
 *
 * @author Urs Wolfer
 * @author Kirill Likhodedov
 */
public class GerritHttpAuthDataProvider implements GitHttpAuthDataProvider {

    @Override
    public @Nullable AuthData getAuthData(@NotNull Project project, @NotNull String url) {
        return authDataFor(project, url, null);
    }

    /**
     * Called instead of the other one when the url names the login, as in "https://jdoe@gerrit.example.com/app".
     * Git then only asks for the password, which has to be the password of that login.
     */
    @Override
    public @Nullable AuthData getAuthData(@NotNull Project project, @NotNull String url, @NotNull String login) {
        return authDataFor(project, url, login);
    }

    @Nullable
    private AuthData authDataFor(Project project, String url, @Nullable String login) {
        GerritAccounts accounts = GerritAccounts.getInstance();
        boolean schemeKnown = isSchemeOfUrlKnown();
        Collection<String> remoteUrls = schemeKnown
            ? Collections.emptyList() : GerritProjectAccount.getRemoteUrls(project);
        GerritAccount account = accountFor(GerritProjectAccount.getInstance(project).get(), accounts.getAccounts(),
            url, login, schemeKnown, remoteUrls);
        if (account == null || StringUtil.isEmptyOrSpaces(account.login)) {
            return null;
        }
        // the version before the password: a password saved in between then ends a rejection rather than inherit it
        int passwordVersion = accounts.getPasswordVersion(account);
        if (GerritGitAuthFailures.getInstance().isRejected(account.id, url, account.login, passwordVersion)) {
            return null;
        }
        String password = accounts.getPassword(account);
        if (StringUtil.isEmptyOrSpaces(password)) {
            return null;
        }
        return new AccountAuthData(account.id, account.login, password, passwordVersion);
    }

    /**
     * Git calls this when it was refused with what {@link #getAuthData} gave it, and asks the next provider, in the
     * end the user, the next time. The password stays stored: the REST api and other repositories may well still
     * accept it.
     */
    @Override
    public void forgetPassword(@NotNull Project project, @NotNull String url, @NotNull AuthData authData) {
        if (authData instanceof AccountAuthData) {
            AccountAuthData accountAuthData = (AccountAuthData) authData;
            GerritGitAuthFailures.getInstance().reject(accountAuthData.accountId, url, authData.getLogin(),
                accountAuthData.passwordVersion);
        }
    }

    /**
     * Nothing here asks the user, so git may also use it for the fetches it runs in the background.
     */
    @Override
    public boolean isSilent() {
        return true;
    }

    /**
     * The url says which instance git talks to, and it need not be the one of the project: a submodule can live on
     * another, and a project with several accounts to choose between may not have chosen yet. The account of the
     * project goes first; another one only when it is the only one on that instance, as two logins there leave no
     * way to tell whose credentials git wants.
     *
     * A login in the url narrows the accounts to the ones with that login, compared ignoring case as Gerrit commonly
     * accepts it in any case.
     *
     * @param remoteUrls the urls of the project's remotes, only looked at where git does not tell the scheme
     */
    @Nullable
    static GerritAccount accountFor(@Nullable GerritAccount own, List<GerritAccount> accounts, String url,
                                    @Nullable String login, boolean schemeKnown, Collection<String> remoteUrls) {
        String fullLogin = login != null ? fullLogin(decode(login), url) : null;
        Predicate<GerritAccount> matches = account -> isGerritUrl(account, url, schemeKnown, remoteUrls)
            && (fullLogin == null || fullLogin.equalsIgnoreCase(account.login));
        if (own != null && matches.test(own)) {
            return own;
        }
        GerritAccount match = null;
        for (GerritAccount account : accounts) {
            if (matches.test(account)) {
                if (match != null) {
                    return null;
                }
                match = account;
            }
        }
        return match;
    }

    /**
     * Git hands over the login as the url has it, "jdoe%40example.com" for an email address; some versions decode it
     * first.
     */
    private static String decode(String login) {
        try {
            return URLDecoder.decode(login.replace("+", "%2B"), StandardCharsets.UTF_8); // '+' is no space here
        } catch (IllegalArgumentException e) { // a '%' which starts no escape
            return login;
        }
    }

    /**
     * Git cuts the login off the url at its first '@' (GitHttpGuiAuthenticator.splitToUsernameAndUnifiedUrl), so
     * where git decoded the login, the rest of an email address stays in the url, as in
     * "https://example.com@review.example.com".
     */
    private static String fullLogin(String login, String url) {
        try {
            String rest = UrlUtils.createUriFromGitConfigString(url).getUserInfo();
            return rest != null ? login + '@' + rest : login;
        } catch (IllegalArgumentException e) { // not a url, which no account matches either
            return login;
        }
    }

    /**
     * Up to 2024.1 git hands providers the url with its scheme replaced by "http", whatever the remote uses
     * (GitHttpGuiAuthenticator.splitToUsernameAndUnifiedUrl), so "http" there says nothing about the connection.
     */
    private static boolean isSchemeOfUrlKnown() {
        return ApplicationInfo.getInstance().getBuild().getBaselineVersion() >= 242;
    }

    /**
     * Git asks for the credentials of a repository url (e.g. "https://gerrit.example.com/my-project"), which is never
     * equal to the configured Gerrit url: they have the origin in common.
     */
    private static boolean isGerritUrl(GerritAccount account, String url, boolean schemeKnown,
                                       Collection<String> remoteUrls) {
        return hasSameOrigin(url, account.host, schemeKnown, remoteUrls)
            || hasSameOrigin(url, account.cloneBaseUrl, schemeKnown, remoteUrls);
    }

    /**
     * The password is only handed out for the Gerrit instance itself: the scheme and the port have to match as well,
     * so that it does not end up at another service on the same host, or on an unencrypted connection.
     *
     * Where git does not tell the scheme, the port is compared, a url without one being on the default port of either
     * scheme, and the remotes of the project stand in for the scheme: the password of an https instance goes only to
     * a host and port the remotes reach over https, and to none which one of them reaches over plain http. That is
     * only as good as the remotes: a clone from the welcome screen has none, so git asks the user there, while a url
     * which is not a remote of the project, such as that of another clone, is taken to use the scheme they use.
     */
    private static boolean hasSameOrigin(String url, String gerritUrl, boolean schemeKnown,
                                         Collection<String> remoteUrls) {
        if (StringUtil.isEmptyOrSpaces(gerritUrl)) {
            return false;
        }
        try {
            if (!UrlUtils.urlHasSameHost(url, gerritUrl)) {
                return false;
            }
            URI repositoryUri = UrlUtils.createUriFromGitConfigString(url);
            URI gerritUri = URI.create(gerritUrl);
            if (!schemeKnown) {
                return portOrDefault(repositoryUri) == portOrDefault(gerritUri)
                    && (!"https".equalsIgnoreCase(gerritUri.getScheme())
                        || isReachedOnlyOverHttps(remoteUrls, gerritUrl, portOrDefault(repositoryUri)));
            }
            return repositoryUri.getScheme() != null
                && repositoryUri.getScheme().equalsIgnoreCase(gerritUri.getScheme())
                && port(repositoryUri) == port(gerritUri);
        } catch (IllegalArgumentException e) { // not a url, so it cannot point to the Gerrit instance
            return false;
        }
    }

    private static boolean isReachedOnlyOverHttps(Collection<String> remoteUrls, String gerritUrl, int port) {
        boolean https = false;
        for (String remoteUrl : remoteUrls) {
            try {
                if (!UrlUtils.urlHasSameHost(remoteUrl, gerritUrl)) {
                    continue;
                }
                URI remoteUri = UrlUtils.createUriFromGitConfigString(remoteUrl);
                if (portOrDefault(remoteUri) != port) {
                    continue;
                }
                if ("http".equalsIgnoreCase(remoteUri.getScheme())) {
                    return false;
                }
                https |= "https".equalsIgnoreCase(remoteUri.getScheme());
            } catch (IllegalArgumentException e) { // not a url, so not one git fetches over http either
            }
        }
        return https;
    }

    /**
     * @return the port, or -1 for either default port, which is all a url whose scheme is not known can say
     */
    private static int portOrDefault(URI uri) {
        return uri.getPort() == 80 || uri.getPort() == 443 ? -1 : uri.getPort();
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Remembers whose credentials these are, which {@link #forgetPassword} is not told otherwise.
     */
    private static final class AccountAuthData extends AuthData {
        private final String accountId;
        private final int passwordVersion;

        AccountAuthData(String accountId, String login, String password, int passwordVersion) {
            super(login, password);
            this.accountId = accountId;
            this.passwordVersion = passwordVersion;
        }
    }
}
