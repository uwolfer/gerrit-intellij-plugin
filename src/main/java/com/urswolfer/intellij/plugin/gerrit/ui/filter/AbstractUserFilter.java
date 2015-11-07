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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction;
import org.jetbrains.annotations.Nullable;

/**
 * @author Thomas Forrer
 */
public abstract class AbstractUserFilter extends AbstractChangesFilter {
    private ImmutableList<User> users;

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
        return new UserPopupAction(project, getActionLabel());
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

        public UserPopupAction(Project project, String labelText) {
            super(labelText);
            this.project = project;
            updateFilterValueLabel(value.get().label);
        }

        @Override
        protected void createActions(Consumer<AnAction> actionConsumer) {
            for (final User user : users) {
                actionConsumer.consume(new DumbAwareAction(user.label) {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        value = Optional.of(user);
                        updateFilterValueLabel(user.label);
                        setChanged();
                        notifyObservers(project);
                    }
                });
            }
        }
    }
}
