/*
 * Copyright 2000-2011 JetBrains s.r.o.
 * Copyright 2013 Urs Wolfer
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.LabelInfo;
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
import java.util.List;
import java.util.Map;

import static com.intellij.icons.AllIcons.Actions.*;

/**
 * A table with the list of changes.
 * Parts based on git4idea.ui.GitCommitListPanel
 *
 * @author Kirill Likhodedov
 * @author Urs Wolfer
 */
public class GerritChangeListPanel extends JPanel implements TypeSafeDataProvider, Consumer<List<ChangeInfo>> {

    @Inject
    private ReviewCommentSink reviewCommentSink;

    private final List<ChangeInfo> changes;
    private final TableView<ChangeInfo> table;
    private GerritToolWindow gerritToolWindow;

    public GerritChangeListPanel() {
        this.changes = Lists.newArrayList();

        this.table = new TableView<ChangeInfo>() {
            /**
             * Renderer marks changes with reviews pending to submit with changed color.
             */
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row) ) {
                    Iterable<CommentInput> commentInputs = GerritChangeListPanel.this.reviewCommentSink
                            .getCommentsForChange(this.getRow(row).getId());
                    if (!Iterables.isEmpty(commentInputs)) {
                        component.setForeground(JBColor.BLUE);
                    } else {
                        component.setForeground(JBColor.BLACK);
                    }
                }
                return component;
            }
        };
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        PopupHandler.installPopupHandler(table, "Gerrit.ListPopup", ActionPlaces.UNKNOWN);

        updateModel();
        table.setStriped(true);

        setLayout(new BorderLayout());
        add(ScrollPaneFactory.createScrollPane(table));
    }

    @Override
    public void consume(List<ChangeInfo> commits) {
        setChanges(commits);
    }

    /**
     * Adds a listener that would be called once user selects a change in the table.
     */
    public void addListSelectionListener(final @NotNull Consumer<ChangeInfo> listener) {
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(final ListSelectionEvent e) {
                ListSelectionModel lsm = (ListSelectionModel) e.getSource();
                int i = lsm.getMaxSelectionIndex();
                if (i >= 0 && e.getValueIsAdjusting()) {
                    listener.consume(changes.get(i));
                }
            }
        });
    }

    /**
     * Registers the diff action which will be called when the diff shortcut is pressed in the table.
     */
    public void registerDiffAction(@NotNull AnAction diffAction) {
        diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), table);
    }

    // Make changes available for diff action
    @Override
    public void calcData(DataKey key, DataSink sink) {
        sink.put(GerritDataKeys.TOOL_WINDOW, gerritToolWindow);

        if (VcsDataKeys.CHANGES.equals(key)) {
            int[] rows = table.getSelectedRows();
            if (rows.length != 1) return;
            int row = rows[0];

            // TODO impl ?
//            ChangeInfo change = changes.get(row);
            // suppressing: inherited API
            //noinspection unchecked
//            sink.put(key, ArrayUtil.toObjectArray(change.getChanges(), Change.class));
        }
    }

    @NotNull
    public JComponent getPreferredFocusComponent() {
        return table;
    }

    public TableView<ChangeInfo> getTable() {
        return table;
    }

    public void clearSelection() {
        table.clearSelection();
    }

    public void setChanges(@NotNull List<ChangeInfo> changes) {
        this.changes.clear();
        this.changes.addAll(changes);
        updateModel();
        table.repaint();
    }

    public void registerChangeListPanel(GerritToolWindow gerritToolWindow) {
        this.gerritToolWindow = gerritToolWindow;
    }

    private void updateModel() {
        table.setModelAndUpdateColumns(new ListTableModel<ChangeInfo>(generateColumnsInfo(changes), changes, 0));
    }

    @NotNull
    private ColumnInfo[] generateColumnsInfo(@NotNull List<ChangeInfo> changes) {
        ItemAndWidth hash = new ItemAndWidth("", 0);
        ItemAndWidth author = new ItemAndWidth("", 0);
        ItemAndWidth project = new ItemAndWidth("", 0);
        ItemAndWidth branch = new ItemAndWidth("", 0);
        ItemAndWidth time = new ItemAndWidth("", 0);
        for (ChangeInfo change : changes) {
            hash = getMax(hash, getHash(change));
            author = getMax(author, getOwner(change));
            project = getMax(project, getProject(change));
            branch = getMax(branch, getBranch(change));
            time = getMax(time, getTime(change));
        }

        return new ColumnInfo[]{
                new GerritChangeColumnStarredInfo(),
                new GerritChangeColumnInfo("ID", hash.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getHash(change);
                    }
                },
                new ColumnInfo<ChangeInfo, String>("Subject") {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return change.getSubject();
                    }
                },
                new GerritChangeColumnInfo("Owner", author.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getOwner(change);
                    }
                },
                new GerritChangeColumnInfo("Project", project.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getProject(change);
                    }
                },
                new GerritChangeColumnInfo("Branch", branch.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getBranch(change);
                    }
                },
                new GerritChangeColumnInfo("Updated", time.item) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getTime(change);
                    }
                },
                new GerritChangeColumnIconLabelInfo("CR") {
                    @Override
                    public LabelInfo getLabelInfo(ChangeInfo change) {
                        return getCodeReview(change);
                    }
                },
                new GerritChangeColumnIconLabelInfo("V") {
                    @Override
                    public LabelInfo getLabelInfo(ChangeInfo change) {
                        return getVerified(change);
                    }
                }
        };
    }

    private ItemAndWidth getMax(ItemAndWidth current, String candidate) {
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

    private static String getHash(ChangeInfo change) {
        return change.getChangeId().substring(0, 9);
    }

    private static String getOwner(ChangeInfo change) {
        return change.getOwner().getName();
    }

    private static String getProject(ChangeInfo change) {
        return change.getProject();
    }

    private static String getBranch(ChangeInfo change) {
        return change.getBranch();
    }

    private static String getTime(ChangeInfo change) {
        return DateFormatUtil.formatPrettyDateTime(change.getUpdated());
    }

    private static LabelInfo getCodeReview(ChangeInfo change) {
        return getLabel(change, "Code-Review");
    }

    private static LabelInfo getVerified(ChangeInfo change) {
        return getLabel(change, "Verified");
    }

    private static LabelInfo getLabel(ChangeInfo change, String labelName) {
        Map<String,LabelInfo> labels = change.getLabels();
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
                if (labelInfo.getApproved() != null) {
                    return Checked;
                }
                if (labelInfo.getRecommended() != null) {
                    return MoveUp;
                }
                if (labelInfo.getDisliked() != null) {
                    return MoveDown;
                }
                if (labelInfo.getRejected() != null) {
                    return Cancel;
                }
            }
            return null;
        }

        private static String getToolTipForLabel(LabelInfo labelInfo) {
            if (labelInfo != null) {
                if (labelInfo.getApproved() != null) {
                    return labelInfo.getApproved().getName();
                }
                if (labelInfo.getRecommended() != null) {
                    return labelInfo.getRecommended().getName();
                }
                if (labelInfo.getDisliked() != null) {
                    return labelInfo.getDisliked().getName();
                }
                if (labelInfo.getRejected() != null) {
                    return labelInfo.getRejected().getName();
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
                    if (changeInfo.getStarred()) {
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
