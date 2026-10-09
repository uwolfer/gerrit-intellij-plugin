/*
 * Copyright 2000-2011 JetBrains s.r.o.
 * Copyright 2013-2016 Urs Wolfer
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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.StatusText;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.LoadChangesProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A table with the list of changes.
 * Parts based on git4idea.ui.GitCommitListPanel
 *
 * @author Kirill Likhodedov
 * @author Urs Wolfer
 */
public class GerritChangeListPanel extends JPanel implements DataProvider {
    /**
     * The change selected in the list. Newer IDEs update actions in the background, where the table must not be
     * read; the platform asks the panel for this key on the EDT and passes the result on.
     */
    public static final DataKey<ChangeInfo> SELECTED_CHANGE = DataKey.create("Gerrit.SelectedChange");
    /** The columns which can be shown or hidden, in their order in the table. */
    public static final DataKey<List<GerritChangeColumns.ColumnToggle>> COLUMNS = DataKey.create("Gerrit.Columns");

    private final Project project;
    private final SelectedRevisions selectedRevisions;

    private final List<ChangeInfo> changes;
    private final TableView<ChangeInfo> table;
    private final List<Runnable> selectionClearedListeners = new ArrayList<>();
    private boolean replacingChanges;
    private boolean rebuildingColumns;
    private LoadChangesProxy loadChangesProxy = null;
    private String listedQuery;
    /** The load which listed the changes, which pages them while a later one has not answered or failed. */
    private LoadChangesProxy listedProxy;
    private Runnable clearFilters;
    private Runnable retry;

    private final JScrollPane scrollPane;
    private final GerritChangeColumns columns;

    public GerritChangeListPanel(Project project) {
        this.project = project;
        this.selectedRevisions = SelectedRevisions.getInstance(project);
        this.changes = new ArrayList<>();

        this.table = new TableView<ChangeInfo>();
        this.columns = new GerritChangeColumns(project, table);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        PopupHandler.installPopupHandler(table, "Gerrit.ListPopup", ActionPlaces.UNKNOWN);
        PopupHandler.installPopupHandler(table.getTableHeader(), "Gerrit.ColumnsPopup", ActionPlaces.UNKNOWN);

        installOpenChangeHandlers();

        updateModel(changes);
        table.setStriped(true);

        setLayout(new BorderLayout());
        scrollPane = ScrollPaneFactory.createScrollPane(table);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                LoadChangesProxy proxy = loadChangesProxy;
                if (proxy != null) {
                    int lowerEnd = e.getAdjustable().getVisibleAmount() + e.getAdjustable().getValue();
                    if (lowerEnd == e.getAdjustable().getMaximum()) {
                        // a load which is already running is skipped by the proxy
                        proxy.getNextPage(new Consumer<List<ChangeInfo>>() {
                            @Override
                            public void consume(List<ChangeInfo> changeInfos) {
                                if (proxy == loadChangesProxy) {
                                    addChanges(changeInfos);
                                }
                            }
                        });
                    }
                }
            }
        });
        add(scrollPane);
    }

    /**
     * @param lookup whether the changes were looked up for commits: the only one found is selected, which shows its
     *               details
     * @param filtersNarrowed whether a filter is set to something else than at the start, which an empty answer is
     *               then blamed on
     * @param query  when it is the one of the listed changes, at least as many are loaded again, so that one which was
     *               loaded by scrolling down stays listed and selected
     * @param finished called once the first changes are in, or the load failed, unless a later load replaced it
     */
    public void load(LoadChangesProxy proxy, boolean lookup, String query, boolean filtersNarrowed, Runnable finished) {
        loadChangesProxy = proxy;
        int minimum = query.equals(listedQuery) ? changes.size() : 0;
        proxy.getFirstChanges(minimum, new Consumer<List<ChangeInfo>>() {
            @Override
            public void consume(List<ChangeInfo> changeInfos) {
                // the pages of a load arrive in the background; a later load may already have replaced this one
                if (proxy != loadChangesProxy) {
                    return;
                }
                finished.run();
                if (proxy.hasFailed()) {
                    // reported to the user already; the changes listed before it stay, as there is no answer to
                    // replace them with. The text is for when they go, instead of the one of the load.
                    // the proxy which loaded them is also the one which loads their next pages
                    loadChangesProxy = listedProxy;
                    showFailure(proxy.getFailure());
                    return;
                }
                listedQuery = query;
                listedProxy = proxy;
                setChanges(changeInfos);
                setupEmptyTableHint(ChangeListEmptyText.of(lookup, filtersNarrowed));
                // at its current patch set, which reviews and the other actions go to
                if (lookup && changeInfos.size() == 1) {
                    table.setSelection(changeInfos);
                }
            }
        });
    }

    /** Enter and a double-click open the selected change like "Compare with Branch" in the popup does. */
    private void installOpenChangeHandlers() {
        Runnable open = () -> {
            AnAction compare = ActionManager.getInstance().getAction("Gerrit.CompareBranch");
            if (compare != null && table.getSelectedObject() != null) {
                ActionUtil.invokeAction(
                    compare, table, ActionPlaces.UNKNOWN, null, null);
            }
        };
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                if (table.rowAtPoint(event.getPoint()) < 0) {
                    return false;
                }
                open.run();
                return true;
            }
        }.installOn(table);
        // replaces the table's own Enter, which moves the selection down
        new DumbAwareAction() {
            @Override
            public void update(@NotNull AnActionEvent e) {
                // otherwise the key goes on to the table: Enter ends a type-ahead search
                SpeedSearchSupply search = SpeedSearchSupply.getSupply(table);
                e.getPresentation().setEnabled(table.getSelectedObject() != null
                    && (search == null || !search.isPopupActive()));
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                open.run();
            }
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, table);
    }

    /**
     * What the list says while there is nothing to show yet, rather than looking empty.
     */
    public void showLoading() {
        table.getEmptyText().setText(GerritBundle.message("list.empty.loading"));
    }

    /**
     * @param clearFilters puts the filters back to their defaults and loads again
     * @param retry        loads again
     */
    public void setEmptyTextActions(Runnable clearFilters, Runnable retry) {
        this.clearFilters = clearFilters;
        this.retry = retry;
    }

    private void setupEmptyTableHint(ChangeListEmptyText.Kind kind) {
        StatusText emptyText = table.getEmptyText();
        emptyText.clear();
        switch (kind) {
            case NO_MATCH:
                emptyText.appendText(GerritBundle.message("list.empty.filtered") + " ");
                emptyText.appendText(GerritBundle.message("list.empty.filtered.clear"),
                    SimpleTextAttributes.LINK_ATTRIBUTES, runLater(() -> clearFilters));
                break;
            case NO_LOOKUP_RESULT:
                // a commit without a change is the usual reason
                emptyText.appendText(GerritBundle.message("list.empty.lookup") + " ");
                appendConfigurationHint(emptyText);
                break;
            default:
                emptyText.appendText(GerritBundle.message("list.empty.none") + " ");
                appendConfigurationHint(emptyText);
        }
    }

    private void showFailure(@Nullable RestApiException failure) {
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        boolean refused = failure != null && account != null && GerritUtil.isRefusal(failure, account);
        String reason = failure == null ? null : ChangeListEmptyText.reason(failure.getMessage());
        StatusText emptyText = table.getEmptyText();
        emptyText.clear();
        emptyText.appendText((reason != null
            ? GerritBundle.message("list.empty.failed.reason", reason)
            : GerritBundle.message("list.empty.failed")) + " ");
        if (refused) {
            emptyText.appendText(GerritBundle.message("list.empty.failed.login"), SimpleTextAttributes.LINK_ATTRIBUTES,
                // saving the credentials announces the change, which loads the list again
                event -> GerritAccountDialog.logIn(project, account));
            emptyText.appendText(" ");
        }
        emptyText.appendText(GerritBundle.message("list.empty.failed.retry"), SimpleTextAttributes.LINK_ATTRIBUTES,
            runLater(() -> retry));
    }

    /**
     * The actions are set by the tool window after the panel is built; the link is only clicked later.
     */
    private static ActionListener runLater(Supplier<Runnable> action) {
        return event -> {
            Runnable runnable = action.get();
            if (runnable != null) {
                runnable.run();
            }
        };
    }

    private static void appendConfigurationHint(StatusText emptyText) {
        emptyText.appendText(GerritBundle.message("list.empty.hint") + " ");
        emptyText.appendText(GerritBundle.message("list.empty.hint.link"), SimpleTextAttributes.LINK_ATTRIBUTES,
            new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    BrowserUtil.browse("https://github.com/uwolfer/gerrit-intellij-plugin#list-of-changes-is-empty");
                }
            });
        emptyText.appendText(" " + GerritBundle.message("list.empty.hint.end"));
    }

    /**
     * Drops what is listed along with the query it came from, so that an answer still on its way, or the next page,
     * does not bring it back.
     */
    public void clear() {
        loadChangesProxy = null;
        listedQuery = null;
        listedProxy = null;
        setChanges(Collections.emptyList());
        // until a load says what there is, or the setup hint what is missing; neither may come if the load fails
        table.getEmptyText().setText("Nothing to show");
    }

    public void showSetupHintWhenRequired(final Project project) {
        GerritProjectAccount projectAccount = GerritProjectAccount.getInstance(project);
        if (projectAccount.needsChoice()) { // set up, only not for this project: telling to set it up would mislead
            StatusText emptyText = table.getEmptyText();
            emptyText.clear();
            emptyText.appendText("Choose the Gerrit account of this project from Account above, or open ");
            emptyText.appendText("settings", SimpleTextAttributes.LINK_ATTRIBUTES, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, GerritSettingsConfigurable.NAME);
                }
            });
            emptyText.appendText(".");
        } else if (projectAccount.getHost().isEmpty() || !projectAccount.isLoginAndPasswordAvailable()) {
            StatusText emptyText = table.getEmptyText();
            emptyText.clear();
            emptyText.appendText("Open ");
            emptyText.appendText("settings", SimpleTextAttributes.LINK_ATTRIBUTES, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, GerritSettingsConfigurable.NAME);
                }
            });
            emptyText.appendText(" to configure this plugin and press the refresh button afterwards.");
        }
    }

    /**
     * Adds a listener that would be called once user selects a change in the table.
     */
    public void addListSelectionListener(final @NotNull Consumer<ChangeInfo> listener) {
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(final ListSelectionEvent e) {
                ListSelectionModel lsm = (ListSelectionModel) e.getSource();
                int i = lsm.getMaxSelectionIndex();
                if (i >= 0 && !e.getValueIsAdjusting() && !rebuildingColumns) {
                    listener.consume(changes.get(i));
                }
            }
        });
    }

    /**
     * Adds a listener that would be called once no change is selected anymore, as when the selected change is no
     * longer listed after the changes were replaced.
     */
    public void addSelectionClearedListener(final @NotNull Runnable listener) {
        selectionClearedListeners.add(listener);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(final ListSelectionEvent e) {
                ListSelectionModel lsm = (ListSelectionModel) e.getSource();
                if (lsm.isSelectionEmpty() && !e.getValueIsAdjusting() && !replacingChanges && !rebuildingColumns) {
                    listener.run();
                }
            }
        });
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
        if (SELECTED_CHANGE.is(dataId)) {
            return table.getSelectedObject();
        }
        if (COLUMNS.is(dataId)) {
            return columns.getToggles();
        }
        return null;
    }

    public TableView<ChangeInfo> getTable() {
        return table;
    }

    public void setChanges(@NotNull List<ChangeInfo> changes) {
        ChangeInfo previouslySelected = table.getSelectedObject();
        List<ChangeInfo> previousChanges = new ArrayList<>(this.changes);
        this.changes.clear();
        this.changes.addAll(changes);
        // the new model drops the selection; whether that empties the panels depends on the change still being listed
        replacingChanges = true;
        try {
            initModel();
        } finally {
            replacingChanges = false;
        }
        table.repaint();
        selectedRevisions.retain(previousChanges, changes);
        reselect(previouslySelected);
    }

    /**
     * Selects the reloaded instance of the change, so that the details and the changes browser get its new state too.
     */
    private void reselect(@Nullable ChangeInfo previouslySelected) {
        if (previouslySelected == null) {
            return;
        }
        Optional<ChangeInfo> reloaded = findChange(previouslySelected.id);
        if (reloaded.isPresent()) {
            // not scrolled to: the user may have scrolled away from it to look at other changes
            table.setSelection(Collections.singletonList(reloaded.get()));
        } else {
            for (Runnable listener : selectionClearedListeners) {
                listener.run();
            }
        }
    }

    public Optional<ChangeInfo> findChange(String changeId) {
        return changes.stream().filter(change -> change.id.equals(changeId)).findFirst();
    }

    public void addChanges(@NotNull List<ChangeInfo> changes) {
        this.changes.addAll(changes);
        // did not find another way to update the scrollbar after adding more changes...
        scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getValue() - 1);
    }

    private void initModel() {
        // a right-click on the header leaves the column under it as the dragged one, as the menu takes the release;
        // the header would go on painting that column of the old model, at index -1, until the next click on it
        table.getTableHeader().setDraggedColumn(null);
        table.setModelAndUpdateColumns(new ListTableModel<ChangeInfo>(columns.create(changes), changes, 0));
    }

    /**
     * The listeners are not told about the selection which the new model drops and which is restored right away: the
     * selected change stays the same, so its details and files are not loaded again.
     */
    public void rebuildColumns() {
        ChangeInfo selected = table.getSelectedObject();
        rebuildingColumns = true;
        try {
            initModel();
            if (selected != null) {
                table.setSelection(Collections.singletonList(selected));
            }
        } finally {
            rebuildingColumns = false;
        }
    }

    private void updateModel(List<ChangeInfo> changes) {
        table.getListTableModel().addRows(changes);
    }
}
