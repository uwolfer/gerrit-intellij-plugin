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
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.StatusText;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    private final SelectedRevisions selectedRevisions;

    private final List<ChangeInfo> changes;
    private final TableView<ChangeInfo> table;
    private final List<Runnable> selectionClearedListeners = new ArrayList<>();
    private boolean replacingChanges;
    private boolean rebuildingColumns;
    private LoadChangesProxy loadChangesProxy = null;
    private String listedQuery;

    private final JScrollPane scrollPane;
    private final GerritChangeColumns columns;

    public GerritChangeListPanel(Project project) {
        this.selectedRevisions = SelectedRevisions.getInstance(project);
        this.changes = new ArrayList<>();

        this.table = new TableView<ChangeInfo>();
        this.columns = new GerritChangeColumns(project, table);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        PopupHandler.installPopupHandler(table, "Gerrit.ListPopup", ActionPlaces.UNKNOWN);
        PopupHandler.installPopupHandler(table.getTableHeader(), "Gerrit.ColumnsPopup", ActionPlaces.UNKNOWN);

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
     * @param query  when it is the one of the listed changes, at least as many are loaded again, so that one which was
     *               loaded by scrolling down stays listed and selected
     */
    public void load(LoadChangesProxy proxy, boolean lookup, String query) {
        loadChangesProxy = proxy;
        int minimum = query.equals(listedQuery) ? changes.size() : 0;
        proxy.getFirstChanges(minimum, new Consumer<List<ChangeInfo>>() {
            @Override
            public void consume(List<ChangeInfo> changeInfos) {
                // the pages of a load arrive in the background; a later load may already have replaced this one
                if (proxy != loadChangesProxy) {
                    return;
                }
                listedQuery = query;
                setChanges(changeInfos);
                // a commit without a change is the usual reason for an empty lookup, a failed query the other
                setupEmptyTableHint(lookup ? "No change found for the selected commits. " : "No changes to display. ");
                // at its current patch set, which reviews and the other actions go to
                if (lookup && changeInfos.size() == 1) {
                    table.setSelection(changeInfos);
                }
            }
        });
    }

    private void setupEmptyTableHint(String lead) {
        StatusText emptyText = table.getEmptyText();
        emptyText.clear();
        emptyText.appendText(
            lead +
            "If you expect changes, there might be a configuration issue. " +
            "Click "
        );
        emptyText.appendText("here", SimpleTextAttributes.LINK_ATTRIBUTES, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                BrowserUtil.browse("https://github.com/uwolfer/gerrit-intellij-plugin#list-of-changes-is-empty");
            }
        });
        emptyText.appendText(" for hints.");
    }

    /**
     * Drops what is listed along with the query it came from, so that an answer still on its way, or the next page,
     * does not bring it back.
     */
    public void clear() {
        loadChangesProxy = null;
        listedQuery = null;
        setChanges(Collections.emptyList());
        // until a load says what there is, or the setup hint what is missing; neither may come if the load fails
        table.getEmptyText().setText("Nothing to show");
    }

    public void showSetupHintWhenRequired(final Project project) {
        GerritProjectAccount projectAccount = GerritProjectAccount.getInstance(project);
        if (projectAccount.needsChoice()) { // set up, only not for this project: telling to set it up would mislead
            StatusText emptyText = table.getEmptyText();
            emptyText.clear();
            emptyText.appendText("Press the refresh button to choose the Gerrit account of this project, or open ");
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
