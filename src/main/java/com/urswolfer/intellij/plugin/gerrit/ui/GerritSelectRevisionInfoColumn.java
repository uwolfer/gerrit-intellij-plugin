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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.inject.Inject;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ComboBoxCellEditor;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
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

    private final Function<Pair<String, RevisionInfo>, String> REVISION_LABEL_FUNCTION = new Function<Pair<String, RevisionInfo>, String>() {
        @Override
        public String apply(Pair<String, RevisionInfo> revisionInfo) {
            return String.format("%s: %s",
                    revisionInfo.getSecond()._number,
                    revisionInfo.getFirst().substring(0, 7));
        }
    };
    private final Function<Map.Entry<String, RevisionInfo>, Pair<String, RevisionInfo>> MAP_ENTRY_TO_PAIR = new Function<Map.Entry<String, RevisionInfo>, Pair<String, RevisionInfo>>() {
        @Override
        public Pair<String, RevisionInfo> apply(Map.Entry<String, RevisionInfo> entry) {
            return Pair.create(entry.getKey(), entry.getValue());
        }
    };

    public GerritSelectRevisionInfoColumn() {
        super("Revision");
    }

    @Nullable
    @Override
    public String valueOf(ChangeInfo changeInfo) {
        String activeRevision = selectedRevisions.get(changeInfo);
        RevisionInfo revisionInfo = changeInfo.revisions.get(activeRevision);
        return REVISION_LABEL_FUNCTION.apply(Pair.create(activeRevision, revisionInfo));
    }

    @Override
    public boolean isCellEditable(ChangeInfo changeInfo) {
        return changeInfo.revisions.size() > 1;
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(final ChangeInfo changeInfo) {
        ComboBoxCellEditor editor = new ComboBoxCellEditor() {
            @Override
            protected List<String> getComboBoxItems() {
                Set<Map.Entry<String, RevisionInfo>> revisions = changeInfo.revisions.entrySet();
                return Lists.newArrayList(Iterables.transform(
                                revisions,
                                Functions.compose(REVISION_LABEL_FUNCTION, MAP_ENTRY_TO_PAIR)
                        )
                );
            }
        };
        editor.addCellEditorListener(new CellEditorListener() {
            @Override
            public void editingStopped(ChangeEvent e) {
                ComboBoxCellEditor cellEditor = (ComboBoxCellEditor) e.getSource();
                String value = (String) cellEditor.getCellEditorValue();
                Iterable<Pair<String, RevisionInfo>> pairs = Iterables.transform(changeInfo.revisions.entrySet(), MAP_ENTRY_TO_PAIR);
                Map<String, Pair<String, RevisionInfo>> map = Maps.uniqueIndex(pairs, REVISION_LABEL_FUNCTION);
                Pair<String, RevisionInfo> pair = map.get(value);
                selectedRevisions.put(changeInfo.changeId, pair.getFirst());
            }

            @Override
            public void editingCanceled(ChangeEvent e) {

            }
        });
        return editor;
    }

    @Nullable
    @Override
    public String getMaxStringValue() {
        return "100: eeeeeee";
    }
}
