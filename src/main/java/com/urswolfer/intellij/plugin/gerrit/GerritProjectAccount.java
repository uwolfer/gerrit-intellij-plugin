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

import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xmlb.annotations.Attribute;
import git4idea.GitUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Which {@link GerritAccount} a project talks to.
 *
 * Only the id is kept, and in the workspace file: which of the user's accounts this checkout belongs to is a local
 * choice, and is not something to share with everyone who clones the project. It is also why nothing is stored while
 * there is only one account to choose - the binding would be lost with the workspace file for no gain, and an
 * installation which has never seen this dialog would start asking questions it never asked before. Neither is it
 * stored when the remotes of the project point at the instance of one account only: that answer follows the remotes
 * when they change, and a fresh clone of a project needs no choice.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.PROJECT)
@State(name = "GerritDefaultAccount", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GerritProjectAccount implements PersistentStateComponent<GerritProjectAccount.AccountState>,
    Disposable {

    public static final class AccountState {
        @Attribute("defaultAccountId") public String defaultAccountId = "";
    }

    /**
     * The account resolved from these accounts while the binding and the remotes were at this generation. The list
     * is compared by identity: {@link GerritAccounts} replaces it on every change, which no listener has to have seen
     * first.
     */
    private static final class Resolved {
        final List<GerritAccount> accounts;
        final long generation;
        @Nullable final GerritAccount account;

        Resolved(List<GerritAccount> accounts, long generation, @Nullable GerritAccount account) {
            this.accounts = accounts;
            this.generation = generation;
            this.account = account;
        }
    }

    private final Project project;

    private volatile AccountState state = new AccountState();
    private volatile boolean loaded;
    private final AtomicLong generation = new AtomicLong();
    private volatile Resolved resolved;

    public GerritProjectAccount(@NotNull Project project) {
        this.project = project;
        // the remotes decide while the project is unbound; reading them for every request walks every repository
        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, repository -> generation.incrementAndGet());
        connection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, generation::incrementAndGet);
    }

    @Override
    public void dispose() {
    }

    public static GerritProjectAccount getInstance(@NotNull Project project) {
        return project.getService(GerritProjectAccount.class);
    }

    @Override
    public AccountState getState() {
        return state;
    }

    @Override
    public void noStateLoaded() {
        loaded = true;
    }

    @Override
    public void loadState(@NotNull AccountState state) {
        this.state = state;
        generation.incrementAndGet();
        // a later load, such as of a workspace file changed on disk, binds the project anew; the first is no change
        if (loaded) {
            project.getMessageBus().syncPublisher(GerritAccountsListener.TOPIC).accountsChanged();
        }
        loaded = true;
    }

    /**
     * @return the account this project was bound to; the only account when it was never bound, so that everything
     *         which has one Gerrit instance keeps working without anyone choosing anything; the only account whose
     *         instance the remotes of the project point at; {@code null} when the choice is real and has not been
     *         made
     */
    @Nullable
    public GerritAccount get() {
        List<GerritAccount> accounts = GerritAccounts.getInstance().getAccounts();
        long current = generation.get();
        Resolved last = resolved;
        if (last != null && last.accounts == accounts && last.generation == current) {
            return last.account;
        }
        GerritAccount account = resolve(state.defaultAccountId, accounts, () -> getRemoteUrls(project));
        resolved = new Resolved(accounts, current, account);
        return account;
    }

    @Nullable
    static GerritAccount resolve(String boundId, List<GerritAccount> accounts, Supplier<Collection<String>> remoteUrls) {
        for (GerritAccount account : accounts) {
            if (account.id.equals(boundId)) {
                return account;
            }
        }
        // an account this project was bound to can have been removed since, which is the same as never having chosen
        if (accounts.size() <= 1) {
            return accounts.isEmpty() ? null : accounts.get(0);
        }
        // several accounts on one instance, with different logins, are as undecided as none
        Collection<String> urls = remoteUrls.get();
        GerritAccount match = null;
        for (GerritAccount account : accounts) {
            if (isOnInstance(account, urls)) {
                if (match != null) {
                    return null;
                }
                match = account;
            }
        }
        return match;
    }

    private static boolean isOnInstance(GerritAccount account, Collection<String> urls) {
        return urls.stream().anyMatch(account::isOnInstance);
    }

    public static Collection<String> getRemoteUrls(@NotNull Project project) {
        List<String> urls = new ArrayList<>();
        if (project.isDisposed() || project.isDefault()) { // a clone from the welcome screen runs in the default one
            return urls;
        }
        for (GitRepository repository : GitUtil.getRepositoryManager(project).getRepositories()) {
            for (GitRemote remote : repository.getRemotes()) {
                urls.addAll(remote.getUrls());
                urls.addAll(remote.getPushUrls());
            }
        }
        return urls;
    }

    public void set(@Nullable GerritAccount account) {
        String id = account != null ? account.id : "";
        if (id.equals(state.defaultAccountId)) {
            return;
        }
        state.defaultAccountId = id;
        generation.incrementAndGet();
        project.getMessageBus().syncPublisher(GerritAccountsListener.TOPIC).accountsChanged();
    }

    /**
     * @return whether there are several accounts to choose between, this project has chosen none and its remotes do
     *         not tell either; the tool
     *         window asks rather than picking one, because picking would send this project's changes, and its
     *         credentials, to whichever instance happened to come first
     */
    public boolean needsChoice() {
        return GerritAccounts.getInstance().getAccounts().size() > 1 && get() == null;
    }

    public String getId() {
        GerritAccount account = get();
        return account != null ? account.id : "";
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

    public String getGitilesUrlOrDefault() {
        GerritAccount account = get();
        return account != null ? account.getGitilesUrlOrDefault() : "";
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
        GerritAccount current = get();
        GerritAccount account;
        boolean bind = false;
        if (current == null) {
            account = GerritAccount.create(host, login, "");
            bind = !accounts.getAccounts().isEmpty(); // the only account needs no binding, one of several does
        } else { // a copy: background requests read the stored account, and must not see it half-changed
            account = current.copy();
            account.host = host;
            account.login = login;
        }
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> accounts.put(account, password), "Saving Gerrit Credentials", false, project);
        } finally { // a password the credential store refused still leaves the account stored
            if (bind) {
                set(account);
            }
        }
    }
}
