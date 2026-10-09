/*
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

package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Thomas Forrer
 */
public abstract class AbstractUserFilter extends AbstractChangesFilter {
    private static final String POPUP_TEXT = GerritBundle.message("filter.user.hint", KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.getShortcuts()));


    private static final User ALL = new User(GerritBundle.message("filter.all"), null);
    private static final User ME = new User(GerritBundle.message("filter.me"), "self");
    private static final List<User> USERS = List.of(ALL, ME);

    private JBPopup popup;
    private AnAction selectOkAction;
    private JTextArea selectUserTextArea;
    private User value = ALL;
    /**
     * "Me" which was saved but could not be applied without a login, kept until the user changes this filter: the
     * login may only be missing for now.
     */
    @Nullable
    private String withoutLogin;
    private UserPopupAction action;

    public abstract String getActionLabel();
    public abstract String getQueryField();

    @Override
    public AnAction getAction(final Project project) {
        action = new UserPopupAction(getActionLabel());
        return action;
    }

    @Override
    public void saveState(@NotNull Map<String, String> state) {
        if (value.forQuery.isPresent()) {
            state.put(getQueryField(), value.forQuery.get());
        } else if (withoutLogin != null) {
            state.put(getQueryField(), withoutLogin);
        }
    }

    @Override
    public void restoreState(@NotNull Map<String, String> state, @NotNull FilterEnvironment environment) {
        String saved = state.get(getQueryField());
        value = ALL;
        withoutLogin = null;
        if (saved != null && !saved.trim().isEmpty()) {
            String query = saved.trim();
            Optional<User> known = USERS.stream().filter(user -> user.forQuery.equals(Optional.of(query))).findFirst();
            if (known.isPresent()) {
                // "self" is an error for Gerrit without a login, which would leave the list empty
                if (environment.isLoggedIn() || known.get() != ME) {
                    value = known.get();
                } else {
                    withoutLogin = query;
                }
            } else {
                value = new User(query, query);
            }
        }
        if (action != null) {
            action.showValue();
        }
    }

    @Override
    @Nullable
    public String getSearchQueryPart() {
        if (value.forQuery.isPresent()) {
            String queryValue = value.forQuery.get();
            queryValue = FulltextFilter.specialEncodeFulltextQuery(queryValue);
            return String.format("%s:%s", getQueryField(), queryValue);
        } else {
            return null;
        }
    }

    private static final class User {
        String label;
        Optional<String> forQuery;

        private User(String label, String forQuery) {
            this.label = label;
            this.forQuery = Optional.ofNullable(forQuery);
        }
    }

    public final class UserPopupAction extends BasePopupAction {
        public UserPopupAction(String labelText) {
            super(labelText);
            updateFilterValueLabel(value.label);
        }

        void showValue() {
            updateFilterValueLabel(value.label);
        }

        @Override
        protected void createActions(Consumer<AnAction> actionConsumer) {
            for (final User user : USERS) {
                actionConsumer.consume(new DumbAwareAction(user.label) {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        change(user);
                    }
                });
            }
            selectUserTextArea = new JTextArea();
            selectOkAction = buildOkAction();
            actionConsumer.consume(new DumbAwareAction(GerritBundle.message("filter.select")) {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    popup = buildBalloon(selectUserTextArea);
                    Point point = new Point(0, 0);
                    SwingUtilities.convertPointToScreen(point, getFilterValueLabel());
                    popup.showInScreenCoordinates(getFilterValueLabel(), point);
                    final JComponent content = popup.getContent();
                    selectOkAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, content);
                    popup.addListener(new JBPopupListener() {
                        @Override
                        public void beforeShown(LightweightWindowEvent lightweightWindowEvent) {}

                        @Override
                        public void onClosed(LightweightWindowEvent event) {
                            selectOkAction.unregisterCustomShortcutSet(content);
                        }
                    });
                }
            });
        }

        private void change(User user) {
            value = user;
            withoutLogin = null;
            updateFilterValueLabel(user.label);
            fireFilterChanged();
        }

        private AnAction buildOkAction() {
            return new AnAction() {
                public void actionPerformed(AnActionEvent e) {
                    popup.closeOk(e.getInputEvent());
                    String newText = selectUserTextArea.getText().trim();
                    if (newText.isEmpty()) {
                        return;
                    }
                    if (!Comparing.equal(newText, getFilterValueLabel().getText(), true)) {
                        User user = new User(newText, newText);
                        change(user);
                    }
                }
            };
        }

        private JBPopup buildBalloon(JTextArea textArea) {
            ComponentPopupBuilder builder = JBPopupFactory.getInstance().
                createComponentPopupBuilder(textArea, textArea);
            builder.setAdText(POPUP_TEXT);
            builder.setResizable(true);
            builder.setMovable(true);
            builder.setRequestFocus(true);
            return builder.createPopup();
        }
    }
}
