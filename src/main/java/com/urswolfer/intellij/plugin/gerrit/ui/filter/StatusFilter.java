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
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction;
import org.jetbrains.annotations.Nullable;

/**
 * @author Thomas Forrer
 */
public class StatusFilter extends AbstractChangesFilter {
    private static final ImmutableList<Status> statuses = ImmutableList.of(
            new Status("All", null),
            new Status("Open", "open"),
            new Status("Merged", "merged"),
            new Status("Abandoned", "abandoned"),
            new Status("Drafts", "draft")
    );

    private Optional<Status> value = Optional.absent();

    public StatusFilter() {
        value = Optional.of(statuses.get(1));
    }

    @Override
    public AnAction getAction(final Project project) {
        return new StatusPopupAction(project, "Status");
    }

    @Override
    @Nullable
    public String getSearchQueryPart() {
        if (value.isPresent() && value.get().forQuery.isPresent()) {
            return String.format("is:%s", value.get().forQuery.get());
        } else {
            return null;
        }
    }

    private static final class Status {
        String label;
        Optional<String> forQuery;

        private Status(String label, String forQuery) {
            this.label = label;
            this.forQuery = Optional.fromNullable(forQuery);
        }
    }

    public final class StatusPopupAction extends BasePopupAction {
        private final Project project;

        public StatusPopupAction(Project project, String labelText) {
            super(labelText);
            this.project = project;
            updateFilterValueLabel(value.get().label);
        }

        @Override
        protected void createActions(Consumer<AnAction> actionConsumer) {
            for (final Status status : statuses) {
                actionConsumer.consume(new DumbAwareAction(status.label) {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        value = Optional.of(status);
                        updateFilterValueLabel(status.label);
                        setChanged();
                        notifyObservers(project);
                    }
                });
            }
        }
    }
}
