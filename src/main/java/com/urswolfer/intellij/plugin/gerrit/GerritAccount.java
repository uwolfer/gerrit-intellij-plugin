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

    /**
     * Set on the account seeded from the settings of a version which had no accounts, until its password has been
     * read from where that version kept it. The credential store can be locked or memory-only for a whole session,
     * which is indistinguishable from an empty store, so the move has to stay possible on a later run.
     */
    @Attribute("fromLegacySettings") public boolean fromLegacySettings = false;

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
        copy.fromLegacySettings = fromLegacySettings;
        return copy;
    }

    public String getCloneBaseUrlOrHost() {
        return cloneBaseUrl == null || cloneBaseUrl.isEmpty() ? host : cloneBaseUrl;
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
        return login == null || login.isEmpty() ? host : login + "@" + host;
    }
}
