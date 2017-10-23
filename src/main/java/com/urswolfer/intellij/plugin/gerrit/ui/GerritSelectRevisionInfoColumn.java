/*
 * Copyright 2013-2014 Urs Wolfer
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

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.inject.Inject;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.ColumnInfo;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.util.RevisionInfos;
import org.jetbrains.annotations.Nullable;

import javax.swing.border.Border;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class representing the column in the {@link com.urswolfer.intellij.plugin.gerrit.ui.GerritChangeListPanel} to select
 * a revision for the row's change.
 *
 * @author Thomas Forrer
 */
public class GerritSelectRevisionInfoColumn extends ColumnInfo<ChangeInfo, String> {
    @Inject
    private SelectedRevisions selectedRevisions;

    private static final Function<Map.Entry<String, RevisionInfo>, Pair<String, RevisionInfo>> MAP_ENTRY_TO_PAIR = new Function<Map.Entry<String, RevisionInfo>, Pair<String, RevisionInfo>>() {
        @Override
        public Pair<String, RevisionInfo> apply(Map.Entry<String, RevisionInfo> entry) {
            return Pair.create(entry.getKey(), entry.getValue());
        }
    };

    public GerritSelectRevisionInfoColumn() {
        super("Patch Set");
    }

    @Nullable
    @Override
    public String valueOf(ChangeInfo changeInfo) {
        String activeRevision = selectedRevisions.get(changeInfo);
        if (activeRevision == null) {
            return "";
        }
        RevisionInfo revisionInfo = changeInfo.revisions.get(activeRevision);
        return getRevisionLabelFunction(changeInfo).apply(Pair.create(activeRevision, revisionInfo));
    }

    @Override
    public boolean isCellEditable(ChangeInfo changeInfo) {
        return changeInfo.revisions != null && changeInfo.revisions.size() > 1;
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(final ChangeInfo changeInfo) {
        ComboBoxTableRenderer<String> editor = createComboBoxTableRenderer(changeInfo);
        editor.addCellEditorListener(new CellEditorListener() {
            @Override
            public void editingStopped(ChangeEvent e) {
                ComboBoxTableRenderer cellEditor = (ComboBoxTableRenderer) e.getSource();
                String value = (String) cellEditor.getCellEditorValue();
                Iterable<Pair<String, RevisionInfo>> pairs = Iterables.transform(changeInfo.revisions.entrySet(), MAP_ENTRY_TO_PAIR);
                Map<String, Pair<String, RevisionInfo>> map = Maps.uniqueIndex(pairs, getRevisionLabelFunction(changeInfo));
                Pair<String, RevisionInfo> pair = map.get(value);
                selectedRevisions.put(changeInfo.id, pair.getFirst());
            }

            @Override
            public void editingCanceled(ChangeEvent e) {}
        });
        return editor;
    }

    @Nullable
    @Override
    public String getMaxStringValue() {
        return "99/99: abcedf1";
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(ChangeInfo changeInfo) {
        final ComboBoxTableRenderer<String> renderer = createComboBoxTableRenderer(changeInfo);
        if (!isCellEditable(changeInfo)) {
            return new DefaultTableCellRenderer() {
                @Override
                public void setBorder(Border border) {
                    super.setBorder(renderer.getBorder());
                }
            };
        }
        return renderer;
    }

    private ComboBoxTableRenderer<String> createComboBoxTableRenderer(final ChangeInfo changeInfo) {
        List<String> revisions = getRevisions(changeInfo);
        String[] array = new String[revisions.size()];
        return new ComboBoxTableRenderer<String>(revisions.toArray(array)) {
            @Override
            public boolean isCellEditable(EventObject event) {
                if (!GerritSelectRevisionInfoColumn.this.isCellEditable(changeInfo)) {
                    return false;
                }
                if (event instanceof MouseEvent) {
                    return ((MouseEvent) event).getClickCount() >= 1;
                }
                return false;
            }
        };
    }

    private List<String> getRevisions(ChangeInfo changeInfo) {
        if (changeInfo.revisions == null) {
            return Collections.emptyList();
        }
        Set<Map.Entry<String, RevisionInfo>> revisions = ImmutableSortedSet.copyOf(
                RevisionInfos.MAP_ENTRY_COMPARATOR,
                changeInfo.revisions.entrySet());
        return Lists.newArrayList(Iterables.transform(
                        revisions,
                        Functions.compose(getRevisionLabelFunction(changeInfo), MAP_ENTRY_TO_PAIR)
                )
        );
    }

    private Function<Pair<String, RevisionInfo>, String> getRevisionLabelFunction(final ChangeInfo changeInfo) {
        return new Function<Pair<String, RevisionInfo>, String>() {
            @Override
            public String apply(Pair<String, RevisionInfo> revisionInfo) {
                int size = changeInfo.revisions.size();
                int number = revisionInfo.getSecond()._number;
                String revision = revisionInfo.getFirst().substring(0, 7);
                if (size < number) { // size not available in older Gerrit versions
                    return String.format("%s: %s", number, revision);
                }
                return String.format("%s/%s: %s", number, size, revision);
            }
        };
    }
}
