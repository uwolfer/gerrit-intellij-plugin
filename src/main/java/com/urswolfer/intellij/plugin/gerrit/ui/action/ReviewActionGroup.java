/*
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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import static com.intellij.icons.AllIcons.Actions.Cancel;
import static com.intellij.icons.AllIcons.Actions.Checked;
import static com.intellij.icons.AllIcons.Actions.Forward;
import static com.intellij.icons.AllIcons.Actions.MoveDown;
import static com.intellij.icons.AllIcons.Actions.MoveUp;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.inject.Inject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class ReviewActionGroup extends ActionGroup {
    private static final ImmutableMap<Integer, Icon> ICONS = ImmutableMap.of(
        -2, Cancel,
        -1, MoveDown,
        0, Forward,
        1, MoveUp,
        2, Checked
    );

    @Inject
    private ReviewActionFactory reviewActionFactory;
    @Inject
    private GerritSettings gerritSettings;

    public ReviewActionGroup() {
        super("Review", "Review Change", AllIcons.Debugger.Watch);
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(gerritSettings.isLoginAndPasswordAvailable());
    }

    @Override
    public boolean isPopup() {
        return true;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent anActionEvent) {
        Optional<ChangeInfo> selectedChange = ActionUtil.getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return new AnAction[0];
        }
        Map<String, Collection<String>> permittedLabels = selectedChange.get().permittedLabels;
        List<AnAction> labels = Lists.newArrayList();
        if (permittedLabels != null) {
            for (Map.Entry<String, Collection<String>> entry : permittedLabels.entrySet()) {
                labels.add(createLabelGroup(entry));
            }
        }
        return labels.toArray(new AnAction[labels.size()]);
    }

    private ActionGroup createLabelGroup(final Map.Entry<String, Collection<String>> entry) {
        return new ActionGroup(entry.getKey(), true) {
            @NotNull
            @Override
            public AnAction[] getChildren(@Nullable AnActionEvent anActionEvent) {
                List<AnAction> valueActions = Lists.newArrayList();
                Collection<String> values = entry.getValue();
                List<Integer> intValues = Lists.newArrayList(Collections2.transform(
                    values, new Function<String, Integer>() {
                        @Override
                        public Integer apply(String v) {
                            v = v.trim();
                            if (v.charAt(0) == '+') v = v.substring(1); // required for Java 6 support
                            return Integer.valueOf(v);
                        }
                    }));
                Collections.sort(intValues);
                Collections.reverse(intValues);
                for (Integer value : intValues) {
                    valueActions.add(reviewActionFactory.get(entry.getKey(), value, ICONS.get(value), false));
                    valueActions.add(reviewActionFactory.get(entry.getKey(), value, ICONS.get(value), true));
                }
                return valueActions.toArray(new AnAction[valueActions.size()]);
            }
        };
    }

    public static class Proxy extends ReviewActionGroup {
        private final ReviewActionGroup delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(ReviewActionGroup.class);
        }

        @Override
        public boolean isPopup() {
            return delegate.isPopup();
        }

        @Override
        public void update(AnActionEvent e) {
            delegate.update(e);
        }

        @NotNull
        @Override
        public AnAction[] getChildren(@Nullable AnActionEvent anActionEvent) {
            return delegate.getChildren(anActionEvent);
        }
    }

}
