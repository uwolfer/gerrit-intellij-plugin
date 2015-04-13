/*
 * Copyright 2000-2011 JetBrains s.r.o.
 * Copyright 2013-2015 Urs Wolfer
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

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.inject.Inject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.*;
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
import com.urswolfer.intellij.plugin.gerrit.util.GerritDataKeys;
import icons.Git4ideaIcons;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.icons.AllIcons.Actions.*;

/**
 * A table with the list of changes.
 * Parts based on git4idea.ui.GitCommitListPanel
 *
 * @author Kirill Likhodedov
 * @author Urs Wolfer
 */
public class GerritChangeListPanel extends JPanel implements DataProvider, Consumer<LoadChangesProxy> {
    private final SelectedRevisions selectedRevisions;
    private final GerritSelectRevisionInfoColumn selectRevisionInfoColumn;
    private final GerritSettings gerritSettings;
    private final ShowSettingsUtil showSettingsUtil;

    private final List<ChangeInfo> changes;
    private final TableView<ChangeInfo> table;
    private GerritToolWindow gerritToolWindow;
    private LoadChangesProxy loadChangesProxy = null;

    private volatile boolean loadingMoreChanges = false;
    private final JScrollPane scrollPane;

    @Inject
    public GerritChangeListPanel(SelectedRevisions selectedRevisions,
                                 GerritSelectRevisionInfoColumn selectRevisionInfoColumn,
                                 GerritSettings gerritSettings,
                                 ShowSettingsUtil showSettingsUtil) {
        this.selectedRevisions = selectedRevisions;
        this.selectRevisionInfoColumn = selectRevisionInfoColumn;
        this.gerritSettings = gerritSettings;
        this.showSettingsUtil = showSettingsUtil;
        this.changes = Lists.newArrayList();

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
                if (!loadingMoreChanges && loadChangesProxy != null) {
                    loadingMoreChanges = true;
                    try {
                        int lowerEnd = e.getAdjustable().getVisibleAmount() + e.getAdjustable().getValue();
                        if (lowerEnd == e.getAdjustable().getMaximum()) {
                            loadChangesProxy.getNextPage(new Consumer<List<ChangeInfo>>() {
                                @Override
                                public void consume(List<ChangeInfo> changeInfos) {
                                    addChanges(changeInfos);
                                }
                            });
                        }
                    } finally {
                        loadingMoreChanges = false;
                    }
                }
            }
        });
        add(scrollPane);
    }

    @Override
    public void consume(LoadChangesProxy proxy) {
        loadChangesProxy = proxy;
        proxy.getNextPage(new Consumer<List<ChangeInfo>>() {
            @Override
            public void consume(List<ChangeInfo> changeInfos) {
                setChanges(changeInfos);
                setupEmptyTableHint();
            }
        });
    }

    private void setupEmptyTableHint() {
        StatusText emptyText = table.getEmptyText();
        emptyText.clear();
        emptyText.appendText(
            "No changes to display. " +
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
                    showSettingsUtil.showSettingsDialog(project, GerritSettingsConfigurable.NAME);
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
            public void valueChanged(final ListSelectionEvent e) {
                ListSelectionModel lsm = (ListSelectionModel) e.getSource();
                int i = lsm.getMaxSelectionIndex();
                if (i >= 0 && !e.getValueIsAdjusting()) {
                    listener.consume(changes.get(i));
                }
            }
        });
    }

    @Nullable
    @Override
    public Object getData(String dataId) {
        if (GerritDataKeys.TOOL_WINDOW.is(dataId)) {
            return gerritToolWindow;
        }
        return null;
    }

    public TableView<ChangeInfo> getTable() {
        return table;
    }

    public void setChanges(@NotNull List<ChangeInfo> changes) {
        this.changes.clear();
        this.changes.addAll(changes);
        initModel();
        table.repaint();
        selectedRevisions.clear();
    }

    public void addChanges(@NotNull List<ChangeInfo> changes) {
        this.changes.addAll(changes);
        // did not find another way to update the scrollbar after adding more changes...
        scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getValue() - 1);
    }

    public void registerChangeListPanel(GerritToolWindow gerritToolWindow) {
        this.gerritToolWindow = gerritToolWindow;
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
        ItemAndWidth subject = new ItemAndWidth("", 0);
        ItemAndWidth author = new ItemAndWidth("", 0);
        ItemAndWidth project = new ItemAndWidth("", 0);
        ItemAndWidth branch = new ItemAndWidth("", 0);
        ItemAndWidth time = new ItemAndWidth("", 0);
        Set<String> availableLabels = Sets.newTreeSet();
        for (ChangeInfo change : changes) {
            number = getMax(number, getNumber(change));
            hash = getMax(hash, getHash(change));
            subject = getMax(subject, getShortenedSubject(change));
            author = getMax(author, getOwner(change));
            project = getMax(project, getProject(change));
            branch = getMax(branch, getBranch(change));
            time = getMax(time, getTime(change));
            for (String label : change.labels.keySet()) {
                availableLabels.add(label);
            }
        }

        List<ColumnInfo> columnList = Lists.newArrayList();
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
        columnList.add(
            new GerritChangeColumnInfo("Subject", subject.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return change.subject;
                }
            }
        );
        columnList.add(
            new GerritChangeColumnInfo("Owner", author.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getOwner(change);
                }
            }
        );
        columnList.add(
            new GerritChangeColumnInfo("Project", project.item) {
                @Override
                public String valueOf(ChangeInfo change) {
                    return getProject(change);
                }
            }
        );
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
                new GerritChangeColumnIconLabelInfo(getShortLabelDisplay(label)) {
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

    /**
     * Builds "Gerrit-like" short display of label:
     * Code-Review -> CR: collect first letter of every word part.
     */
    private String getShortLabelDisplay(String label) {
        String result = "";
        Iterable<String> parts = Splitter.on('-').omitEmptyStrings().split(label);
        for (String part : parts) {
            result += part.substring(0, 1);
        }
        return result;
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
        return "" + change._number;
    }

    private static String getHash(ChangeInfo change) {
        return change.changeId.substring(0, 9);
    }

    private static String getShortenedSubject(ChangeInfo change) {
        if (change.subject.length() > 80) {
            return change.subject.substring(0, 80);
        }
        return change.subject;
    }

    private static String getOwner(ChangeInfo change) {
        return change.owner.name;
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

        public GerritChangeColumnIconLabelInfo(String name) {
            super(name);
        }

        @Nullable
        @Override
        public LabelInfo valueOf(ChangeInfo changeInfo) {
            return null;
        }

        public abstract LabelInfo getLabelInfo(ChangeInfo change);

        @Nullable
        @Override
        public TableCellRenderer getRenderer(final ChangeInfo changeInfo) {
            return new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    LabelInfo labelInfo = getLabelInfo(changeInfo);
                    label.setIcon(getIconForLabel(labelInfo));
                    label.setToolTipText(getToolTipForLabel(labelInfo));
                    label.setHorizontalAlignment(CENTER);
                    label.setVerticalAlignment(CENTER);
                    return label;
                }
            };
        }

        @Override
        public int getWidth(JTable table) {
            return Checked.getIconWidth() + 20;
        }

        private static Icon getIconForLabel(LabelInfo labelInfo) {
            if (labelInfo != null) {
                if (labelInfo.approved != null) {
                    return Checked;
                }
                if (labelInfo.recommended != null) {
                    return MoveUp;
                }
                if (labelInfo.disliked != null) {
                    return MoveDown;
                }
                if (labelInfo.rejected != null) {
                    return Cancel;
                }
            }
            return null;
        }

        private static String getToolTipForLabel(LabelInfo labelInfo) {
            if (labelInfo != null) {
                if (labelInfo.approved != null) {
                    return labelInfo.approved.name;
                }
                if (labelInfo.recommended != null) {
                    return labelInfo.recommended.name;
                }
                if (labelInfo.disliked != null) {
                    return labelInfo.disliked.name;
                }
                if (labelInfo.rejected != null) {
                    return labelInfo.rejected.name;
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
                        label.setIcon(Git4ideaIcons.Star);
                    }
                    label.setHorizontalAlignment(CENTER);
                    label.setVerticalAlignment(CENTER);
                    return label;
                }
            };
        }

        @Override
        public int getWidth(JTable table) {
            return Git4ideaIcons.Star.getIconWidth();
        }
    }
}
