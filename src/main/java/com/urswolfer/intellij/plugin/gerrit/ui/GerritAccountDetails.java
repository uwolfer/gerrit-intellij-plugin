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

import com.google.gerrit.extensions.common.AccountInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The name and avatar Gerrit has for each account of the settings page, or why it would not say, as the GitHub
 * plugin shows them. Each is asked for once, with what the page holds for the account: an edit which is not applied
 * yet is what its row shows. Only on the event dispatch thread.
 */
final class GerritAccountDetails {
    private static final Logger LOG = Logger.getInstance(GerritAccountDetails.class);
    private static final Executor EXECUTOR =
        AppExecutorUtil.createBoundedApplicationPoolExecutor("Gerrit account details", 2);

    static final class Result {
        @Nullable final AccountInfo info;
        @Nullable final String error;
        /** Gerrit refused the credentials, which logging in again mends. */
        final boolean refused;

        Result(@Nullable AccountInfo info, @Nullable String error, boolean refused) {
            this.info = info;
            this.error = error;
            this.refused = refused;
        }
    }

    private static final class Entry {
        String credentials;
        @Nullable Result result;

        Entry(String credentials) {
            this.credentials = credentials;
        }
    }

    private final Runnable changed;
    private final Map<String, Entry> byId = new HashMap<>();
    private int loading;

    /**
     * @param changed called when a load starts or ends
     */
    GerritAccountDetails(Runnable changed) {
        this.changed = changed;
    }

    /**
     * @param editedPassword the password entered on the page, {@code null} for the stored one
     * @return {@code null} while loading, and for an account without a login, which Gerrit has nothing on
     */
    @Nullable
    Result get(GerritAccount account, @Nullable String editedPassword) {
        if (account.host.isEmpty() || account.login.isEmpty()) {
            return null;
        }
        String credentials = credentials(account, editedPassword);
        Entry entry = byId.get(account.id);
        if (entry == null || !entry.credentials.equals(credentials)) {
            entry = new Entry(credentials);
            byId.put(account.id, entry);
            load(account.copy(), editedPassword, entry);
            loading++;
            // later: this runs while the list paints
            ApplicationManager.getApplication().invokeLater(changed, ModalityState.any());
        }
        return entry.result;
    }

    boolean isLoading() {
        return loading > 0;
    }

    /**
     * The password entered on the page for the account was stored: what was loaded with it is what the stored one
     * gives, and asking again would cost another login, which counts towards a lockout where it fails.
     */
    void stored(GerritAccount account, String password) {
        Entry entry = byId.get(account.id);
        if (entry != null && entry.credentials.equals(credentials(account, password))) {
            entry.credentials = credentials(account, null);
        }
    }

    // a marker no typed password can be, rather than the stored one, which only the background thread reads
    private static String credentials(GerritAccount account, @Nullable String editedPassword) {
        return account.host + '\n' + account.login + '\n' + (editedPassword == null ? "\0" : "\1" + editedPassword);
    }

    /**
     * Asks again at the next paint, as after the account was saved in its dialog: what failed may be mended on the
     * server meanwhile.
     */
    void forget(GerritAccount account) {
        byId.remove(account.id);
    }

    private void load(GerritAccount account, @Nullable String editedPassword, Entry entry) {
        EXECUTOR.execute(() -> {
            Result result = null;
            try {
                // the stored one is read here: the credential store blocks, and may ask to be unlocked
                String password = editedPassword != null ? editedPassword
                    : GerritAccounts.getInstance().getPassword(account);
                GerritAuthData.Basic authData = new GerritAuthData.Basic(account.host, account.login, password) {
                    @Override
                    public boolean isLoginAndPasswordAvailable() {
                        return true; // an empty password is sent, and refused, rather than asking anonymously
                    }
                };
                result = new Result(GerritApiProvider.getInstance().create(authData).accounts().self().get(),
                    null, false);
            } catch (Exception e) {
                LOG.debug("Could not load the details of " + account, e);
                boolean refused = GerritAccountDialog.isRefusal(e);
                result = new Result(null, refused ? GerritBundle.message("details.loginRefused") : GerritAccountDialog.reason(e), refused);
            } finally { // even after an Error, or the list stays busy
                Result loaded = result;
                ApplicationManager.getApplication().invokeLater(() -> {
                    loading--;
                    // a later edit of the account has asked again, and that answer is the one to show
                    if (byId.get(account.id) == entry) {
                        entry.result = loaded;
                    }
                    changed.run();
                }, ModalityState.any());
            }
        });
    }
}
