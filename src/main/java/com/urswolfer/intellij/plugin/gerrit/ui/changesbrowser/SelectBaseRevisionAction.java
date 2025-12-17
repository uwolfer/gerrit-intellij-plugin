/*
 *
 *  * Copyright 2013-2014 Urs Wolfer
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction;
import com.urswolfer.intellij.plugin.gerrit.util.RevisionInfos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * @author Thomas Forrer
 */
@SuppressWarnings("ComponentNotRegistered")
public class SelectBaseRevisionAction extends BasePopupAction {

    private static final String BASE = "Base";
    private static final Function<Pair<String, RevisionInfo>, String> REVISION_LABEL_FUNCTION = new Function<Pair<String, RevisionInfo>, String>() {
        @Override
        public String apply(Pair<String, RevisionInfo> revisionInfo) {
            return String.format("%s: %s",
                    revisionInfo.getSecond()._number,
                    revisionInfo.getFirst().substring(0, 7));
        }
    };
    private final SelectedRevisions selectedRevisions;

    private Optional<ChangeInfo> selectedChange = Optional.absent();
    private Optional<Pair<String, RevisionInfo>> selectedValue = Optional.absent();
    private List<Listener> listeners = Lists.newArrayList();

    public SelectBaseRevisionAction(final SelectedRevisions selectedRevisions) {
        super("Diff against");
        this.selectedRevisions = selectedRevisions;
        selectedRevisions.addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                if (arg != null && arg instanceof String && selectedValue.isPresent() ) {
                    Optional<String> selectedRevision = selectedRevisions.get((String) arg);
                    if (selectedRevision.isPresent() && selectedRevision.get().equals(selectedValue.get().getFirst())) {
                        removeSelectedValue();
                        updateLabel();
                    }
                }
            }
        });
        updateLabel();
    }

    @Override
    protected void createActions(Consumer<AnAction> anActionConsumer) {
        anActionConsumer.consume(new DumbAwareUpdateInBackgroundAction("Base") {
            @Override
            public void actionPerformed(AnActionEvent e) {
                removeSelectedValue();
                updateLabel();
            }
        });
        if (selectedChange.isPresent()) {
            ImmutableSortedSet<Map.Entry<String, RevisionInfo>> revisions = ImmutableSortedSet.copyOf(
                    RevisionInfos.MAP_ENTRY_COMPARATOR,
                    selectedChange.get().revisions.entrySet());
            for (Map.Entry<String, RevisionInfo> entry : revisions) {
                anActionConsumer.consume(getActionForRevision(entry.getKey(), entry.getValue()));
            }
        }
    }

    public void setSelectedChange(ChangeInfo selectedChange) {
        this.selectedChange = Optional.of(selectedChange);
        selectedValue = Optional.absent();
        updateLabel();
    }

    public void addRevisionSelectedListener(Listener listener) {
        this.listeners.add(listener);
    }

    private DumbAwareAction getActionForRevision(final String commitHash, final RevisionInfo revisionInfo) {
        final Pair<String, RevisionInfo> infoPair = Pair.create(commitHash, revisionInfo);
        String actionLabel = REVISION_LABEL_FUNCTION.apply(infoPair);
        return new DumbAwareUpdateInBackgroundAction(actionLabel) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                updateSelectedValue(infoPair);
                updateLabel();
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(!isSameRevisionAsSelected());
            }

            private boolean isSameRevisionAsSelected() {
                return commitHash.equals(selectedRevisions.get(selectedChange.get()));
            }
        };
    }

    private void updateSelectedValue(Pair<String, RevisionInfo> revisionInfo) {
        selectedValue = Optional.of(revisionInfo);
        notifyListeners();
    }

    private void removeSelectedValue() {
        selectedValue = Optional.absent();
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.revisionSelected(selectedValue);
        }
    }

    private void updateLabel() {
        updateFilterValueLabel(selectedValue.transform(REVISION_LABEL_FUNCTION).or(BASE));
    }

    public static interface Listener {
        void revisionSelected(Optional<Pair<String, RevisionInfo>> revisionInfo);
    }

    private abstract class DumbAwareUpdateInBackgroundAction extends DumbAwareAction implements UpdateInBackground {
        protected DumbAwareUpdateInBackgroundAction(@Nullable @NlsActions.ActionText String text) {
            super(text);
        }
    }
}
