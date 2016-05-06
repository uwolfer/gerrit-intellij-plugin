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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Consumer;
import git4idea.history.wholeTree.BasePopupAction;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Thomas Forrer
 */
public abstract class AbstractUserFilter extends AbstractChangesFilter {
    @Inject
    private JBPopupFactory jbPopupFactory;

    private ImmutableList<User> users;
    private JBPopup popup;
    private AnAction selectOkAction;
    private JTextArea selectUserTextArea;
    private Optional<User> value = Optional.absent();

    public abstract String getActionLabel();
    public abstract String getQueryField();

    @Override
    public AnAction getAction(final Project project) {
        users = ImmutableList.of(
                new User("All", null),
                new User("Me", "self")
        );
        value = Optional.of(users.get(0));
        return new UserPopupAction(project, getActionLabel() + ":", getActionLabel());
    }

    @Override
    @Nullable
    public String getSearchQueryPart() {
        if (value.isPresent() && value.get().forQuery.isPresent()) {
            return String.format("%s:%s", getQueryField(), value.get().forQuery.get());
        } else {
            return null;
        }
    }

    private static final class User {
        String label;
        Optional<String> forQuery;

        private User(String label, String forQuery) {
            this.label = label;
            this.forQuery = Optional.fromNullable(forQuery);
        }
    }

    public final class UserPopupAction extends BasePopupAction {
        private final Project project;

        public UserPopupAction(Project project, String labelText, String asTextLabel) {
            super(project, labelText, asTextLabel);
            this.project = project;
            myLabel.setText(value.get().label);
        }

        @Override
        protected void createActions(Consumer<AnAction> actionConsumer) {
            for (final User user : users) {
                actionConsumer.consume(new DumbAwareAction(user.label) {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        change(user);
                    }
                });
            }
            selectUserTextArea = new JTextArea();
            selectOkAction = buildOkAction();
            actionConsumer.consume(new DumbAwareAction("Select...") {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    if (popup != null) {
                        selectOkAction.unregisterCustomShortcutSet(popup.getContent());
                    }
                    popup = buildBalloon(selectUserTextArea);
                    Point point = new Point(0, 0);
                    SwingUtilities.convertPointToScreen(point, myLabel);
                    popup.showInScreenCoordinates(myLabel, point);
                    JComponent content = popup.getContent();
                    selectOkAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, content);
                }
            });
        }

        private void change(User user) {
            value = Optional.of(user);
            myLabel.setText(user.label);
            setChanged();
            notifyObservers(project);
        }

        private AnAction buildOkAction() {
            return new AnAction() {
                public void actionPerformed(AnActionEvent e) {
                    popup.closeOk(e.getInputEvent());
                    String newText = selectUserTextArea.getText().trim();
                    if (newText.isEmpty()) {
                        return;
                    }
                    if (!Comparing.equal(newText, myLabel.getText())) {
                        User user = new User(newText, newText);
                        change(user);
                    }
                }
            };
        }

        private JBPopup buildBalloon(JTextArea textArea) {
            ComponentPopupBuilder builder = jbPopupFactory.
                createComponentPopupBuilder(textArea, textArea);
            builder.setAdText("Ctrl+Enter to search");
            builder.setResizable(true);
            builder.setMovable(true);
            builder.setRequestFocus(true);
            return builder.createPopup();
        }
    }
}
