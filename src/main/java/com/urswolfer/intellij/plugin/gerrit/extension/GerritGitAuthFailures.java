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

package com.urswolfer.intellij.plugin.gerrit.extension;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The accounts whose credentials git rejected for a url, so that they are not handed to it again. Deleting the
 * password instead would also take it from the REST api, which may well still accept it. Git names only the scheme,
 * host and port in the url unless credential.useHttpPath is set, so a rejection usually covers every repository of
 * the instance.
 *
 * A rejection holds in every project until the login changes or the password is saved again, the same one included:
 * entering it again is how someone says that it should work now. Nothing retries it before, as trying a wrong
 * password again in the background can lock the account. It is kept in memory only, like what git itself remembers
 * of a failed attempt.
 */
@Service(Service.Level.APP)
public final class GerritGitAuthFailures {

    private final Map<String, String> rejected = new ConcurrentHashMap<>();

    public static GerritGitAuthFailures getInstance() {
        return ApplicationManager.getApplication().getService(GerritGitAuthFailures.class);
    }

    /**
     * @param passwordVersion {@link com.urswolfer.intellij.plugin.gerrit.GerritAccounts#getPasswordVersion} when the
     *                        password was read
     */
    public void reject(@NotNull String accountId, @NotNull String url, @NotNull String login, int passwordVersion) {
        rejected.put(key(accountId, url), credentials(login, passwordVersion));
    }

    public boolean isRejected(@NotNull String accountId, @NotNull String url, @NotNull String login,
                              int passwordVersion) {
        return credentials(login, passwordVersion).equals(rejected.get(key(accountId, url)));
    }

    private static String key(String accountId, String url) {
        return accountId + ' ' + url;
    }

    private static String credentials(String login, int passwordVersion) {
        return passwordVersion + " " + login;
    }
}
