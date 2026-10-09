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

package com.urswolfer.intellij.plugin.gerrit;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.urswolfer.intellij.plugin.gerrit.util.GitilesUrls;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * A Gerrit instance the user has credentials for.
 *
 * The id is generated once and never changes, so the password keeps belonging to the account when its host or login
 * is edited, and a project keeps pointing at the account it was bound to.
 *
 * @author Urs Wolfer
 */
@Tag("account")
public final class GerritAccount {

    @Attribute("id") public String id = "";
    @Attribute("login") public String login = "";
    @Attribute("host") public String host = "";
    @Attribute("cloneBaseUrl") public String cloneBaseUrl = "";
    @Attribute("gitilesUrl") public String gitilesUrl = "";

    /**
     * Set on the account taken over from a version which had no accounts, and kept set for as long as the account
     * exists. It says the password may also be sitting under the key that version used, which stays true for every
     * machine the accounts are synced to: the accounts travel, the credential store does not, so a machine which
     * has not done the move yet still has to know to look there. Reading it costs nothing once the account has its
     * own password, and only clearing the password clears this.
     */
    @Attribute("usesLegacyPasswordKey") public boolean usesLegacyPasswordKey = false;

    /**
     * The serializer needs this; everything else goes through {@link #create}.
     */
    public GerritAccount() {
    }

    public static GerritAccount create(String host, String login, String cloneBaseUrl) {
        GerritAccount account = new GerritAccount();
        account.id = UUID.randomUUID().toString();
        account.host = host != null ? host : "";
        account.login = login != null ? login : "";
        account.cloneBaseUrl = cloneBaseUrl != null ? cloneBaseUrl : "";
        return account;
    }

    public GerritAccount copy() {
        GerritAccount copy = new GerritAccount();
        copy.id = id;
        copy.login = login;
        copy.host = host;
        copy.cloneBaseUrl = cloneBaseUrl;
        copy.gitilesUrl = gitilesUrl;
        copy.usesLegacyPasswordKey = usesLegacyPasswordKey;
        return copy;
    }

    public String getCloneBaseUrlOrHost() {
        return cloneBaseUrl == null || cloneBaseUrl.isEmpty() ? host : cloneBaseUrl;
    }

    /**
     * @return whether the url is on this account's instance, under its web url or its clone base url
     */
    public boolean isOnInstance(String url) {
        return isOnHost(url, host) || isOnHost(url, cloneBaseUrl);
    }

    private static boolean isOnHost(String url, @Nullable String accountUrl) {
        if (accountUrl == null || accountUrl.isEmpty()) {
            return false;
        }
        try {
            return UrlUtils.urlHasSameHost(url, accountUrl);
        } catch (IllegalArgumentException e) { // java.net.URI rejects some remotes git accepts; on no host then
            return false;
        }
    }

    /**
     * @return the account together with its host and login: a session logged in with one of them is not one of
     *         another, even under the same id
     */
    public String getIdentity() {
        return id + '\n' + host + '\n' + login;
    }

    public String getGitilesUrlOrDefault() {
        return GitilesUrls.getBaseUrl(gitilesUrl, host);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GerritAccount && id.equals(((GerritAccount) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        if (host == null || host.isEmpty()) {
            return login == null || login.isEmpty() ? "New account" : login;
        }
        return login == null || login.isEmpty() ? host : login + "@" + host;
    }
}
