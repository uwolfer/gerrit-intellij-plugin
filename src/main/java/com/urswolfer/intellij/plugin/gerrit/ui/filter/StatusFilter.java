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
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Thomas Forrer
 */
public class StatusFilter extends AbstractChangesFilter {
    private static final List<Status> STATUSES = List.of(
            new Status("All", null),
            new Status("Open", "open"),
            new Status("Merged", "merged"),
            new Status("Abandoned", "abandoned")
    );

    private static final Supplier<String> QUERY_FOR_ALL = new Supplier<String>() {
        @Override
        public String get() {
            Set<String> queryForAll = new HashSet<>();
            for (Status status : STATUSES) {
                if (status.forQuery.isPresent()) {
                    queryForAll.add(String.format("is:%s", status.forQuery.get()));
                }
            }
            return String.format("(%s)", String.join("+OR+", queryForAll));
        }
    };

    private static final String ALL = "all";
    private static final String STATE_KEY = "status";
    private static final Status DEFAULT = STATUSES.get(1);

    private Status value = DEFAULT;
    private StatusPopupAction action;

    @Override
    public AnAction getAction(final Project project) {
        action = new StatusPopupAction("Status");
        return action;
    }

    @Override
    public void saveState(@NotNull Map<String, String> state) {
        if (value != DEFAULT) {
            state.put(STATE_KEY, value.forQuery.orElse(ALL));
        }
    }

    @Override
    public void restoreState(@NotNull Map<String, String> state, @NotNull FilterEnvironment environment) {
        String saved = state.get(STATE_KEY);
        value = STATUSES.stream()
                .filter(status -> status.forQuery.orElse(ALL).equals(saved))
                .findFirst()
                .orElse(DEFAULT);
        if (action != null) {
            action.showValue();
        }
    }

    @Override
    @Nullable
    public String getSearchQueryPart() {
        if (value.forQuery.isPresent()) {
            return String.format("is:%s", value.forQuery.get());
        } else {
            return QUERY_FOR_ALL.get();
        }
    }

    private static final class Status {
        final String label;
        final Optional<String> forQuery;

        private Status(String label, String forQuery) {
            this.label = label;
            this.forQuery = Optional.ofNullable(forQuery);
        }
    }

    public final class StatusPopupAction extends BasePopupAction {
        public StatusPopupAction(String labelText) {
            super(labelText);
            updateFilterValueLabel(value.label);
        }

        void showValue() {
            updateFilterValueLabel(value.label);
        }

        @Override
        protected void createActions(Consumer<AnAction> actionConsumer) {
            for (final Status status : STATUSES) {
                actionConsumer.consume(new DumbAwareAction(status.label) {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        value = status;
                        updateFilterValueLabel(status.label);
                        fireFilterChanged();
                    }
                });
            }
        }
    }
}
