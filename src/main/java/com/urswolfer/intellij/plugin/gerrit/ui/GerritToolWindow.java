/*
 * Copyright 2013 Urs Wolfer
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.GerritAccountsListener;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.LoadChangesProxy;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.ChangesFilter;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.GerritChangesFilters;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
public class GerritToolWindow implements Disposable {
    /**
     * Provided by the tool window content panel, so that actions can reach the tool window they were invoked from
     * instead of looking it up in a global holder.
     */
    public static final DataKey<GerritToolWindow> GERRIT_TOOL_WINDOW = DataKey.create("Gerrit.ToolWindow");

    private static final Logger LOG = Logger.getInstance(GerritToolWindow.class);

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private GerritChangeListPanel changeListPanel;
    private final GerritChangesFilters changesFilters = new GerritChangesFilters();
    private AccountPopupAction accountAction;
    private final RepositoryChangesBrowserProvider repositoryChangesBrowserProvider = new RepositoryChangesBrowserProvider();

    private GerritChangeDetailsPanel detailsPanel;
    private String detailsChangeId;
    private int changesLoad;
    private final AtomicBoolean reloadPending = new AtomicBoolean();
    private volatile boolean listSettingsChanged;
    /**
     * What the changes listed were loaded with: the account, the clone base url its query names the projects by, and
     * its password version. Another account clears the list; the others only load it again.
     */
    private String listedIdentity = "";
    private String listedCloneBaseUrl = "";
    private int listedPasswordVersion;
    private volatile boolean disposed;

    /**
     * Nothing to release here: this is the parent the tool window content's listeners are registered against, and
     * the platform disposes it with the content.
     */
    @Override
    public void dispose() {
        disposed = true;
    }

    public SimpleToolWindowPanel createToolWindowContent(final Project project) {
        changeListPanel = new GerritChangeListPanel(project);
        changeListPanel.setEmptyTextActions(() -> {
            changesFilters.reset();
            reloadChanges(project, true);
        }, () -> reloadChanges(project, false));

        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true) {
            @Override
            public Object getData(@NotNull String dataId) {
                if (GERRIT_TOOL_WINDOW.is(dataId)) {
                    return GerritToolWindow.this;
                }
                return super.getData(dataId);
            }
        };

        ActionToolbar toolbar = createToolbar(project);
        toolbar.setTargetComponent(changeListPanel);
        panel.setToolbar(toolbar.getComponent());

        CommittedChangesBrowser repositoryChangesBrowser = repositoryChangesBrowserProvider.get(project, changeListPanel, this);

        JBSplitter detailsSplitter = new OnePixelSplitter(true, 0.6f);
        detailsSplitter.setSplitterProportionKey("Gerrit.ListDetailSplitter.Proportion");
        detailsSplitter.setFirstComponent(changeListPanel);

        detailsPanel = new GerritChangeDetailsPanel(project);
        changeListPanel.addListSelectionListener(new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                changeSelected(changeInfo, project);
            }
        });
        changeListPanel.addSelectionClearedListener(() -> {
            detailsChangeId = null;
            detailsPanel.nothingSelected();
        });
        JPanel details = detailsPanel.getComponent();
        detailsSplitter.setSecondComponent(details);

        JBSplitter horizontalSplitter = new OnePixelSplitter(false, 0.7f);
        horizontalSplitter.setSplitterProportionKey("Gerrit.DetailRepositoryChangeBrowser.Proportion");
        horizontalSplitter.setFirstComponent(detailsSplitter);
        horizontalSplitter.setSecondComponent(repositoryChangesBrowser);

        panel.setContent(horizontalSplitter);

        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (!repositories.isEmpty()) {
            reloadChanges(project, false);
        }

        registerVcsChangeListener(project);
        MessageBusConnection settings = ApplicationManager.getApplication().getMessageBus().connect(this);
        settings.subscribe(GerritListSettingsListener.TOPIC, () -> {
            listSettingsChanged = true;
            scheduleReload(project);
        });
        settings.subscribe(GerritChangeColumns.CHANGED, changeListPanel::rebuildColumns);
        MessageBusConnection projectBus = project.getMessageBus().connect(this);
        projectBus.subscribe(GerritChangesListener.TOPIC, new GerritChangesListener() {
            @Override
            public void changesModified() {
                reloadChanges(project, false);
            }

            @Override
            public void changeModified(String changeId, Consumer<ChangeInfo> update) {
                updateChange(changeId, update, project);
            }
        });
        // on the project's bus, which also carries what is published on the application's
        projectBus.subscribe(GerritAccountsListener.TOPIC, () -> scheduleReload(project));

        changeListPanel.showSetupHintWhenRequired(project);

        return panel;
    }

    private void registerVcsChangeListener(final Project project) {
        VcsRepositoryMappingListener vcsListener = new VcsRepositoryMappingListener() {
            @Override
            public void mappingChanged() {
                // published from a pooled thread; loads are only started on the event dispatch thread
                ApplicationManager.getApplication().invokeLater(
                    () -> reloadChanges(project, false), project.getDisposed());
            }
        };
        project.getMessageBus().connect(this).subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, vcsListener);
    }

    private void changeSelected(ChangeInfo changeInfo, final Project project) {
        // the details of the change selected before would stay until this one's come, and for good when they do not;
        // those of the same change, selected again after a reload, stay until they are replaced
        if (!changeInfo.id.equals(detailsChangeId)) {
            detailsChangeId = null;
            detailsPanel.loading();
        }
        gerritUtil.getChangeDetailsOrNull(null, changeInfo._number, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeDetails) {
                // another change may have been selected meanwhile
                if (changeListPanel.getTable().getSelectedObject() != changeInfo) {
                    return;
                }
                if (changeDetails == null) {
                    if (detailsChangeId == null) {
                        detailsPanel.failed();
                    }
                } else {
                    detailsPanel.setData(changeDetails);
                    detailsChangeId = changeDetails.id;
                }
            }
        });
    }

    /**
     * The change is looked up by its id because a reload replaces every listed instance.
     */
    private void updateChange(String changeId, Consumer<ChangeInfo> update, Project project) {
        Optional<ChangeInfo> listed = changeListPanel.findChange(changeId);
        if (!listed.isPresent()) {
            return;
        }
        update.consume(listed.get());
        changeListPanel.getTable().repaint();
        if (changeListPanel.getTable().getSelectedObject() == listed.get()) {
            changeSelected(listed.get(), project);
        }
    }

    /**
     * Shows what the query finds, whatever the filters were set to, and selects the change if it is the only one.
     */
    public void showChanges(Project project, String query) {
        String host = GerritProjectAccount.getInstance(project).getHost();
        if (host.isEmpty()) { // the filters would show the lookup over a list which never loads
            return;
        }
        changesFilters.showLookup(query);
        reloadChanges(project, false);
    }

    /**
     * Saving settings announces several changes, some from behind a modal progress, so the reload waits for the
     * dialogs to close: one reload then follows them all, with everything they changed in place.
     */
    private void scheduleReload(Project project) {
        if (!reloadPending.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            reloadPending.set(false);
            boolean force = listSettingsChanged;
            listSettingsChanged = false;
            reloadForSettings(project, force);
        }, ModalityState.NON_MODAL, expired -> disposed || project.isDisposed());
    }

    /**
     * The accounts announce every change, most of which concern other projects. The changes listed belong to the
     * account they were loaded with, so they go as soon as this project talks to another one, rather than showing
     * until the next load arrives and taking actions and further pages there.
     */
    private void reloadForSettings(Project project, boolean force) {
        if (!GerritProjectSettings.isEnabled(project)) {
            return;
        }
        accountAction.refresh();
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        if (!canList(account)) {
            showSetupHint(project);
            return;
        }
        boolean otherAccount = !account.getIdentity().equals(listedIdentity);
        if (otherAccount) { // whether or not a load follows: a refresh may have listed it without repositories
            clearChanges();
        }
        // as when the tool window opens: without repositories the query names no project and lists every change
        if (GitUtil.getRepositoryManager(project).getRepositories().isEmpty()) {
            return;
        }
        if (force || otherAccount || !account.cloneBaseUrl.equals(listedCloneBaseUrl)
            || GerritAccounts.getInstance().getPasswordVersion(account) != listedPasswordVersion) {
            reloadChanges(project, false);
        }
    }

    private void clearChanges() {
        changesLoad++; // a load which has not reached the list yet must not bring the changes back
        changeListPanel.clear();
        listedIdentity = "";
    }

    /**
     * Without an account the project has none or several to choose between; one without a host is taken over from an
     * earlier version and needs the login dialog yet.
     */
    private static boolean canList(@Nullable GerritAccount account) {
        return account != null && !account.host.isEmpty();
    }

    /**
     * Nothing can be listed without an account, and what is listed may have come from one this project left.
     */
    private void showSetupHint(Project project) {
        clearChanges();
        changeListPanel.showSetupHintWhenRequired(project);
    }

    public void reloadChanges(final Project project, boolean requestSettingsIfNonExistent) {
        // the window of a project without Gerrit is only hidden, and still hears about settings and VCS mappings
        if (!GerritProjectSettings.isEnabled(project)) {
            return;
        }
        GerritProjectAccount projectAccount = GerritProjectAccount.getInstance(project);
        accountAction.refresh();
        if (requestSettingsIfNonExistent && projectAccount.needsChoice()) {
            chooseAccount(project, projectAccount);
        }
        // none at all, or the one chosen without an instance yet, such as one taken over from an earlier version
        if (requestSettingsIfNonExistent && !projectAccount.needsChoice() && projectAccount.getHost().isEmpty()) {
            GerritAccountDialog.logIn(project, projectAccount.get());
        }
        GerritAccount account = projectAccount.get();
        if (!canList(account)) {
            showSetupHint(project);
            return;
        }
        // loaded right away; the choice or login is also announced, which then finds this account listed already
        if (!account.getIdentity().equals(listedIdentity)) {
            // what is listed came from another account, and must not take actions and further pages to this one
            clearChanges();
            listedIdentity = account.getIdentity();
        }
        listedCloneBaseUrl = account.cloneBaseUrl;
        listedPasswordVersion = GerritAccounts.getInstance().getPasswordVersion(account);
        int load = ++changesLoad;
        boolean lookup = changesFilters.isShowingLookup();
        String query = changesFilters.getQuery();
        boolean narrowed = changesFilters.isNarrowed();
        Consumer<LoadChangesProxy> consumer = proxy -> {
            // loads run concurrently; one started earlier must not replace what a later one shows
            if (load == changesLoad) {
                changeListPanel.load(proxy, lookup, query, narrowed);
            }
        };
        changeListPanel.showLoading();
        if (lookup) {
            // a full hash is unique, and the projects of the repositories are not always known from their remotes
            gerritUtil.getChanges(query, project, consumer);
        } else {
            gerritUtil.getChangesForProject(query, project, consumer);
        }
    }

    /**
     * Asking beats the login dialog here: the accounts exist and have their passwords, it is only unknown which of
     * them this project belongs to.
     */
    private void chooseAccount(Project project, GerritProjectAccount projectAccount) {
        List<GerritAccount> accounts = GerritAccounts.getInstance().getAccounts();
        String[] labels = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            labels[i] = accounts.get(i).toString();
        }
        int index = Messages.showChooseDialog(project, "Which Gerrit account does this project use?",
            "Select Gerrit Account", Messages.getQuestionIcon(), labels, labels[0]);
        if (index >= 0) {
            projectAccount.set(accounts.get(index));
        }
    }

    private ActionToolbar createToolbar(final Project project) {
        DefaultActionGroup groupFromConfig = (DefaultActionGroup) ActionManager.getInstance().getAction("Gerrit.Toolbar");
        DefaultActionGroup group = new DefaultActionGroup(groupFromConfig); // copy required (otherwise config action group gets modified)

        DefaultActionGroup filterGroup = new DefaultActionGroup();
        Iterable<ChangesFilter> filters = changesFilters.getFilters();
        for (ChangesFilter filter : filters) {
            filterGroup.add(filter.getAction(project));
        }
        filterGroup.add(new Separator());
        group.add(filterGroup, Constraints.FIRST);
        accountAction = new AccountPopupAction(project);
        group.add(new DefaultActionGroup(accountAction, new Separator()), Constraints.FIRST);

        changesFilters.addListener(new GerritChangesFilters.Listener() {
            @Override
            public void filtersChanged() {
                reloadChanges(project, true);
            }
        });

        return ActionManager.getInstance().createActionToolbar("Gerrit.Toolbar", group, true);
    }
}
