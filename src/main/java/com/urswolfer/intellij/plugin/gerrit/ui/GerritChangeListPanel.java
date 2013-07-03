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

import java.awt.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.ui.action.CherryPickAction;
import com.urswolfer.intellij.plugin.gerrit.ui.action.CompareAction;
import com.urswolfer.intellij.plugin.gerrit.ui.action.FetchAction;
import com.urswolfer.intellij.plugin.gerrit.ui.action.ReviewAction;
import com.urswolfer.intellij.plugin.gerrit.ui.action.SubmitAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A table with the list of changes.
 *
 * Parts based on git4idea.ui.GitCommitListPanel
 *
 * @author Kirill Likhodedov
 * @author Urs Wolfer
 */
public class GerritChangeListPanel extends JPanel implements TypeSafeDataProvider {

    private final List<ChangeInfo> myChanges;
    private final TableView<ChangeInfo> myTable;

    public GerritChangeListPanel(@NotNull List<ChangeInfo> changes, @Nullable String emptyText) {
        myChanges = changes;

        myTable = new TableView<ChangeInfo>();

        setupActions();

        updateModel();
        myTable.setStriped(true);
        if (emptyText != null) {
            myTable.getEmptyText().setText(emptyText);
        }

        setLayout(new BorderLayout());
        add(ScrollPaneFactory.createScrollPane(myTable));
    }

    private void setupActions() {
        final DefaultActionGroup contextMenuActionGroup = new DefaultActionGroup();

        contextMenuActionGroup.add(new FetchAction());

        contextMenuActionGroup.add(new CompareAction());

        contextMenuActionGroup.add(new CherryPickAction());

        final DefaultActionGroup reviewActionGroup = new DefaultActionGroup("Review", true);
        reviewActionGroup.getTemplatePresentation().setIcon(AllIcons.ToolbarDecorator.Export);
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, 2, AllIcons.Actions.Checked, false));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, 2, AllIcons.Actions.Checked, true));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, 1, AllIcons.Actions.MoveUp, false));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, 1, AllIcons.Actions.MoveUp, true));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, 0, AllIcons.Actions.Forward, false));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, 0, AllIcons.Actions.Forward, true));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, -1, AllIcons.Actions.MoveDown, false));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, -1, AllIcons.Actions.MoveDown, true));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, -2, AllIcons.Actions.Cancel, false));
        reviewActionGroup.add(new ReviewAction(ReviewAction.CODE_REVIEW, -2, AllIcons.Actions.Cancel, true));

        final DefaultActionGroup verifyActionGroup = new DefaultActionGroup("Verify", true);
        verifyActionGroup.getTemplatePresentation().setIcon(AllIcons.Debugger.Watch);
        verifyActionGroup.add(new ReviewAction(ReviewAction.VERIFIED, 1, AllIcons.Actions.Checked, false));
        verifyActionGroup.add(new ReviewAction(ReviewAction.VERIFIED, 1, AllIcons.Actions.Checked, true));
        verifyActionGroup.add(new ReviewAction(ReviewAction.VERIFIED, 0, AllIcons.Actions.Forward, false));
        verifyActionGroup.add(new ReviewAction(ReviewAction.VERIFIED, 0, AllIcons.Actions.Forward, true));
        verifyActionGroup.add(new ReviewAction(ReviewAction.VERIFIED, -1, AllIcons.Actions.Cancel, false));
        verifyActionGroup.add(new ReviewAction(ReviewAction.VERIFIED, -1, AllIcons.Actions.Cancel, true));

        contextMenuActionGroup.add(reviewActionGroup);
        contextMenuActionGroup.add(verifyActionGroup);

        contextMenuActionGroup.add(new SubmitAction());

        PopupHandler.installPopupHandler(myTable, contextMenuActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    }

    /**
     * Adds a listener that would be called once user selects a change in the table.
     */
    public void addListSelectionListener(final @NotNull Consumer<ChangeInfo> listener) {
        myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(final ListSelectionEvent e) {
                ListSelectionModel lsm = (ListSelectionModel) e.getSource();
                int i = lsm.getMaxSelectionIndex();
                if (i >= 0) {
                    listener.consume(myChanges.get(i));
                }
            }
        });
    }

    /**
     * Registers the diff action which will be called when the diff shortcut is pressed in the table.
     */
    public void registerDiffAction(@NotNull AnAction diffAction) {
        diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), myTable);
    }

    // Make changes available for diff action
    @Override
    public void calcData(DataKey key, DataSink sink) {
        if (VcsDataKeys.CHANGES.equals(key)) {
            int[] rows = myTable.getSelectedRows();
            if (rows.length != 1) return;
            int row = rows[0];

            // TODO impl ?
//            ChangeInfo change = myChanges.get(row);
            // suppressing: inherited API
            //noinspection unchecked
//            sink.put(key, ArrayUtil.toObjectArray(change.getChanges(), Change.class));
        }
    }

    @NotNull
    public JComponent getPreferredFocusComponent() {
        return myTable;
    }

    public void clearSelection() {
        myTable.clearSelection();
    }

    public void setChanges(@NotNull List<ChangeInfo> changes) {
        myChanges.clear();
        myChanges.addAll(changes);
        updateModel();
        myTable.repaint();
    }

    private void updateModel() {
        myTable.setModelAndUpdateColumns(new ListTableModel<ChangeInfo>(generateColumnsInfo(myChanges), myChanges, 0));
    }

    @NotNull
    private ColumnInfo[] generateColumnsInfo(@NotNull List<ChangeInfo> changes) {
        ItemAndWidth hash = new ItemAndWidth("", 0);
        ItemAndWidth author = new ItemAndWidth("", 0);
        ItemAndWidth time = new ItemAndWidth("", 0);
        for (ChangeInfo change : changes) {
            hash = getMax(hash, getHash(change));
            author = getMax(author, getOwner(change));
            time = getMax(time, getTime(change));
        }

        return new ColumnInfo[]{
                new GerritChangeColumnInfo("ID", hash.myItem) {
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
                new GerritChangeColumnInfo("Owner", author.myItem) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getOwner(change);
                    }
                },
                new GerritChangeColumnInfo("Updated", time.myItem) {
                    @Override
                    public String valueOf(ChangeInfo change) {
                        return getTime(change);
                    }
                }
        };
    }

    private ItemAndWidth getMax(ItemAndWidth current, String candidate) {
        int width = myTable.getFontMetrics(myTable.getFont()).stringWidth(candidate);
        if (width > current.myWidth) {
            return new ItemAndWidth(candidate, width);
        }
        return current;
    }

    private static class ItemAndWidth {
        private final String myItem;
        private final int myWidth;

        private ItemAndWidth(String item, int width) {
            myItem = item;
            myWidth = width;
        }
    }

    private static String getHash(ChangeInfo change) {
        return change.getChangeId();
    }

    private static String getOwner(ChangeInfo change) {
        return change.getOwner().getName();
    }

    private static String getTime(ChangeInfo change) {
        return DateFormatUtil.formatPrettyDateTime(change.getUpdated());
    }

    private abstract static class GerritChangeColumnInfo extends ColumnInfo<ChangeInfo, String> {

        @NotNull
        private final String myMaxString;

        public GerritChangeColumnInfo(@NotNull String name, @NotNull String maxString) {
            super(name);
            myMaxString = maxString;
        }

        @Override
        public String getMaxStringValue() {
            return myMaxString;
        }

        @Override
        public int getAdditionalWidth() {
            return UIUtil.DEFAULT_HGAP;
        }
    }

}
