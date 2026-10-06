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

import static com.intellij.icons.AllIcons.Actions.Cancel;
import static com.intellij.icons.AllIcons.Actions.Checked;
import static com.intellij.icons.AllIcons.Actions.MoveDown;
import static com.intellij.icons.AllIcons.Actions.MoveUp;

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.intellij.icons.AllIcons;
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
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.rest.LoadChangesProxy;
import git4idea.GitUtil;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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

    private final SelectedRevisions selectedRevisions;
    private final GerritSelectRevisionInfoColumn selectRevisionInfoColumn;
    private final GerritSettings gerritSettings;

    private final List<ChangeInfo> changes;
    private final TableView<ChangeInfo> table;
    private final List<Runnable> selectionClearedListeners = new ArrayList<>();
    private boolean replacingChanges;
    private LoadChangesProxy loadChangesProxy = null;
    private String listedQuery;

    private Project project;

    private final JScrollPane scrollPane;

    public GerritChangeListPanel(Project project) {
        this.project = project;
        this.selectedRevisions = SelectedRevisions.getInstance(project);
        this.selectRevisionInfoColumn = new GerritSelectRevisionInfoColumn(project);
        this.gerritSettings = GerritSettings.getInstance();
        this.changes = new ArrayList<>();

        this.table = new TableView<ChangeInfo>();
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        PopupHandler.installPopupHandler(table, "Gerrit.ListPopup", ActionPlaces.UNKNOWN);

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

    public void showSetupHintWhenRequired(final Project project) {
        if (!gerritSettings.isLoginAndPasswordAvailable()) {
            StatusText emptyText = table.getEmptyText();
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
                if (i >= 0 && !e.getValueIsAdjusting()) {
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
                if (lsm.isSelectionEmpty() && !e.getValueIsAdjusting() && !replacingChanges) {
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
        table.setModelAndUpdateColumns(new ListTableModel<ChangeInfo>(generateColumnsInfo(changes), changes, 0));
    }

    private void updateModel(List<ChangeInfo> changes) {
        table.getListTableModel().addRows(changes);
    }

    @NotNull
    private ColumnInfo[] generateColumnsInfo(@NotNull List<ChangeInfo> changes) {
        ItemAndWidth number = new ItemAndWidth("", 0);
        ItemAndWidth hash = new ItemAndWidth("", 0);
        ItemAndWidth topic = new ItemAndWidth("", 0);
        ItemAndWidth subject = new ItemAndWidth("", 0);
        ItemAndWidth status = new ItemAndWidth("", 0);
        ItemAndWidth author = new ItemAndWidth("", 0);
        ItemAndWidth projectName = new ItemAndWidth("", 0);
        ItemAndWidth branch = new ItemAndWidth("", 0);
        ItemAndWidth time = new ItemAndWidth("", 0);
        Set<String> availableLabels = new TreeSet<>();
        for (ChangeInfo change : changes) {
            number = getMax(number, getNumber(change));
            hash = getMax(hash, getHash(change));
            topic = getMax(topic, getTopic(change));
            subject = getMax(subject, getShortenedSubject(change));
            status = getMax(status, getStatus(change));
            author = getMax(author, getOwner(change));
            projectName = getMax(projectName, getProject(change));
            branch = getMax(branch, getBranch(change));
            time = getMax(time, getTime(change));
            if (change.labels != null) {
                for (String label : change.labels.keySet()) {
                    availableLabels.add(label);
                }
            }
        }

        List<ColumnInfo> columnList = new ArrayList<>();
        columnList.add(new GerritChangeColumnStarredInfo());
        boolean showChangeNumberColumn = gerritSettings.getShowChangeNumberColumn();
        if (showChangeNumberColumn) {
            columnList.add(
                new GerritChangeColumnInfo("#", number.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getNumber(change);
                    }
                }
            );
        }
        boolean showChangeIdColumn = gerritSettings.getShowChangeIdColumn();
        if (showChangeIdColumn) {
            columnList.add(
                new GerritChangeColumnInfo("ID", hash.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getHash(change);
                    }
                }
            );
        }
        boolean showTopicColumn = gerritSettings.getShowTopicColumn();
        if (showTopicColumn) {
            columnList.add(
                new GerritChangeColumnInfo("Topic", topic.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getTopic(change);
                    }
                }
            );
        }

        columnList.add(
            new GerritChangeColumnInfo("Subject", subject.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return change.subject;
                }

                @Nullable
                @Override
                public String getPreferredStringValue() {
                    return super.getMaxStringValue();
                }

                @Override
                public String getMaxStringValue() {
                    return null; // allow to use remaining space
                }
            }
        );
        columnList.add(
            new GerritChangeColumnInfo("Status", status.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getStatus(change);
                }
            }
        );
        columnList.add(
            new GerritChangeColumnInfo("Owner", author.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getOwner(change);
                }

                @Nullable
                @Override
                public TableCellRenderer getRenderer(final ChangeInfo changeInfo) {
                    return new DefaultTableCellRenderer() {
                        @Override
                        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                            JLabel labelComponent = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                            labelComponent.setToolTipText(getAccountTooltip(changeInfo.owner));
                            return labelComponent;
                        }
                    };
                }
            }
        );
        ShowProjectColumn showProjectColumn = gerritSettings.getShowProjectColumn();
        boolean listAllChanges = gerritSettings.getListAllChanges();
        if (showProjectColumn == ShowProjectColumn.ALWAYS
            || (showProjectColumn == ShowProjectColumn.AUTO && (listAllChanges || hasProjectMultipleRepos()))) {
            columnList.add(
                new GerritChangeColumnInfo("Project", projectName.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getProject(change);
                    }
                }
            );
        }
        columnList.add(
            new GerritChangeColumnInfo("Branch", branch.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getBranch(change);
                }
            }
        );
        columnList.add(
            new GerritChangeColumnInfo("Updated", time.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getTime(change);
                }
            }
        );
        for (final String label : availableLabels) {
            columnList.add(
                new GerritChangeColumnIconLabelInfo(getShortLabelDisplay(label), label) {
                    @Override
                    public LabelInfo getLabelInfo(ChangeInfo change) {
                        return getLabel(change, label);
                    }
                }
            );
        }
        columnList.add(selectRevisionInfoColumn);

        return columnList.toArray(new ColumnInfo[columnList.size()]);
    }

    private boolean hasProjectMultipleRepos() {
        if (project == null) {
            return false;
        }
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        return repositoryManager.getRepositories().size() > 1;
    }

    /**
     * Builds "Gerrit-like" short display of label:
     * Code-Review -> CR: collect first letter of every word part.
     */
    private String getShortLabelDisplay(String label) {
        StringBuilder result = new StringBuilder();
        for (String part : label.split("-")) {
            if (!part.isEmpty()) {
                result.append(part.charAt(0));
            }
        }
        return result.toString();
    }

    private ItemAndWidth getMax(ItemAndWidth current, String candidate) {
        if (candidate == null) {
            return current;
        }
        int width = table.getFontMetrics(table.getFont()).stringWidth(candidate);
        if (width > current.width) {
            return new ItemAndWidth(candidate, width);
        }
        return current;
    }

    private static class ItemAndWidth {
        private final String item;
        private final int width;

        private ItemAndWidth(String item, int width) {
            this.item = item;
            this.width = width;
        }
    }

    private static String getNumber(ChangeInfo change) {
        return Integer.toString(change._number);
    }

    private static String getHash(ChangeInfo change) {
        return change.changeId.substring(0, Math.min(change.changeId.length(), 9));
    }

    private static String getTopic(ChangeInfo change) {
        return change.topic;
    }

    private static String getShortenedSubject(ChangeInfo change) {
        return change.subject.substring(0, Math.min(change.subject.length(), 80));
    }

    private static String getStatus(ChangeInfo change) {
        if (ChangeStatus.MERGED.equals(change.status)) {
            return "Merged";
        }
        if (ChangeStatus.ABANDONED.equals(change.status)) {
            return "Abandoned";
        }
        if (change.mergeable != null && !change.mergeable) {
            return "Merge Conflict";
        }
        if (ChangeStatus.DRAFT.equals(change.status)) {
            return "Draft";
        }
        return "";
    }

    private static String getOwner(ChangeInfo change) {
        return change.owner.name;
    }

    private static String getAccountTooltip(AccountInfo accountInfo) {
        if (accountInfo.name == null) {
            // Gerrit leaves the name out of an account which has none, as often with bots
            return accountInfo.email != null ? accountInfo.email
                : accountInfo.username != null ? accountInfo.username
                : String.valueOf(accountInfo._accountId);
        }
        if (accountInfo.email != null) {
            return String.format("%s &lt;%s&gt;", accountInfo.name, accountInfo.email);
        } else {
            return accountInfo.name;
        }
    }

    private static String getProject(ChangeInfo change) {
        return change.project;
    }

    private static String getBranch(ChangeInfo change) {
        return change.branch;
    }

    private static String getTime(ChangeInfo change) {
        return change.updated != null ? DateFormatUtil.formatPrettyDateTime(change.updated) : "";
    }

    private static LabelInfo getLabel(ChangeInfo change, String labelName) {
        Map<String,LabelInfo> labels = change.labels;
        if (labels != null) {
            return labels.get(labelName);
        } else {
            return null;
        }
    }

    private abstract static class GerritChangeColumnInfo extends ColumnInfo<ChangeInfo, String> {

        @NotNull
        private final String maxString;

        public GerritChangeColumnInfo(@NotNull String name, @NotNull String maxString) {
            super(name);
            this.maxString = maxString;
        }

        @Override
        public String getMaxStringValue() {
            return maxString;
        }

        @Override
        public int getAdditionalWidth() {
            return UIUtil.DEFAULT_HGAP;
        }
    }

    private abstract static class GerritChangeColumnIconLabelInfo extends ColumnInfo<ChangeInfo, LabelInfo> {

        private final String label;

        public GerritChangeColumnIconLabelInfo(String shortLabel, String label) {
            super(shortLabel);
            this.label = label;
        }

        @Nullable
        @Override
        public LabelInfo valueOf(ChangeInfo changeInfo) {
            return null;
        }

        public abstract LabelInfo getLabelInfo(ChangeInfo change);

        @Nullable
        @Override
        public String getTooltipText() {
            return label;
        }

        @Nullable
        @Override
        public TableCellRenderer getRenderer(final ChangeInfo changeInfo) {
            return new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel labelComponent = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    LabelInfo labelInfo = getLabelInfo(changeInfo);
                    labelComponent.setIcon(getIconForLabel(labelInfo));
                    labelComponent.setToolTipText(getToolTipForLabel(labelInfo));
                    labelComponent.setHorizontalAlignment(CENTER);
                    labelComponent.setVerticalAlignment(CENTER);
                    return labelComponent;
                }
            };
        }

        @Override
        public int getWidth(JTable table) {
            return Checked.getIconWidth() + 20;
        }

        private static Icon getIconForLabel(LabelInfo labelInfo) {
            if (labelInfo != null) {
                if (labelInfo.rejected != null) {
                    return Cancel;
                }
                if (labelInfo.approved != null) {
                    return Checked;
                }
                if (labelInfo.disliked != null) {
                    return MoveDown;
                }
                if (labelInfo.recommended != null) {
                    return MoveUp;
                }
            }
            return null;
        }

        /**
         * Names the voter of the vote the icon shows: Gerrit fills in an account for each kind of vote at once.
         */
        private static String getToolTipForLabel(LabelInfo labelInfo) {
            if (labelInfo != null) {
                AccountInfo accountInfo = null;
                if (labelInfo.rejected != null) {
                    accountInfo = labelInfo.rejected;
                } else if (labelInfo.approved != null) {
                    accountInfo = labelInfo.approved;
                } else if (labelInfo.disliked != null) {
                    accountInfo = labelInfo.disliked;
                } else if (labelInfo.recommended != null) {
                    accountInfo = labelInfo.recommended;
                }
                if (accountInfo != null) {
                    return getAccountTooltip(accountInfo);
                }
            }
            return null;
        }
    }

    private static class GerritChangeColumnStarredInfo extends ColumnInfo<ChangeInfo, Boolean> {

        public GerritChangeColumnStarredInfo() {
            super("");
        }

        @Nullable
        @Override
        public Boolean valueOf(ChangeInfo changeInfo) {
            return null;
        }

        @Nullable
        @Override
        public TableCellRenderer getRenderer(final ChangeInfo changeInfo) {
            return new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (changeInfo.starred != null && changeInfo.starred) {
                        label.setIcon(AllIcons.Nodes.Favorite);
                    }
                    label.setHorizontalAlignment(CENTER);
                    label.setVerticalAlignment(CENTER);
                    return label;
                }
            };
        }

        @Override
        public int getWidth(JTable table) {
            return AllIcons.Nodes.Favorite.getIconWidth();
        }
    }
}
