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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Which {@link GerritAccount} a project talks to.
 *
 * Only the id is kept, and in the workspace file: which of the user's accounts this checkout belongs to is a local
 * choice, and is not something to share with everyone who clones the project. It is also why nothing is stored while
 * there is only one account to choose - the binding would be lost with the workspace file for no gain, and an
 * installation which has never seen this dialog would start asking questions it never asked before.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.PROJECT)
@State(name = "GerritDefaultAccount", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GerritProjectAccount implements PersistentStateComponent<GerritProjectAccount.AccountState> {

    public static final class AccountState {
        @Attribute("defaultAccountId") public String defaultAccountId = "";
    }

    private final Project project;

    private AccountState state = new AccountState();

    public GerritProjectAccount(@NotNull Project project) {
        this.project = project;
    }

    public static GerritProjectAccount getInstance(@NotNull Project project) {
        return project.getService(GerritProjectAccount.class);
    }

    @Override
    public AccountState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull AccountState state) {
        this.state = state;
    }

    /**
     * @return the account this project was bound to; the only account when it was never bound, so that everything
     *         which has one Gerrit instance keeps working without anyone choosing anything; {@code null} when the
     *         choice is real and has not been made
     */
    @Nullable
    public GerritAccount get() {
        return resolve(state.defaultAccountId, GerritAccounts.getInstance().getAccounts());
    }

    @Nullable
    static GerritAccount resolve(String boundId, List<GerritAccount> accounts) {
        for (GerritAccount account : accounts) {
            if (account.id.equals(boundId)) {
                return account;
            }
        }
        // an account this project was bound to can have been removed since, which is the same as never having chosen
        return accounts.size() == 1 ? accounts.get(0) : null;
    }

    public void set(@Nullable GerritAccount account) {
        state.defaultAccountId = account != null ? account.id : "";
    }

    /**
     * @return whether this project is bound to an account explicitly rather than following the only one there is
     */
    public boolean isBound() {
        return GerritAccounts.getInstance().findById(state.defaultAccountId) != null;
    }

    public String getHost() {
        GerritAccount account = get();
        return account != null ? account.host : "";
    }

    public String getLogin() {
        GerritAccount account = get();
        return account != null ? account.login : "";
    }

    public String getCloneBaseUrl() {
        GerritAccount account = get();
        return account != null ? account.cloneBaseUrl : "";
    }

    public String getCloneBaseUrlOrHost() {
        GerritAccount account = get();
        return account != null ? account.getCloneBaseUrlOrHost() : "";
    }

    public boolean isLoginAndPasswordAvailable() {
        return !getLogin().isEmpty();
    }

    /**
     * Blocks on the credential store, so it must not be called on the event dispatch thread; UI code uses
     * {@link #getPasswordWithModalProgress} instead.
     */
    @NotNull
    public String getPassword() {
        return GerritAccounts.getInstance().getPassword(get());
    }

    @NotNull
    public String getPasswordWithModalProgress() {
        return ProgressManager.getInstance().<String, RuntimeException>runProcessWithProgressSynchronously(
                this::getPassword, "Reading Gerrit Credentials", false, project);
    }

    /**
     * Saves what the login dialog collected on the account this project uses, creating it when the project has none
     * yet. Writing blocks on the credential store, so it runs behind a modal progress rather than on the event
     * dispatch thread.
     */
    public void saveCredentialsWithModalProgress(String host, String login, String password) {
        GerritAccounts accounts = GerritAccounts.getInstance();
        GerritAccount account = get();
        if (account == null) {
            account = GerritAccount.create(host, login, "");
            set(account);
        } else {
            account.host = host;
            account.login = login;
        }
        accounts.put(account);
        GerritAccount target = account;
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> accounts.setPassword(target, password), "Saving Gerrit Credentials", false, project);
    }

    public void forgetPassword() {
        GerritAccount account = get();
        if (account != null) {
            GerritAccounts.getInstance().forgetPassword(account);
        }
    }
}
