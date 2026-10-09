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
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import git4idea.GitRemoteBranch;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Thomas Forrer
 */
public class BranchFilter extends AbstractChangesFilter {
    private static final String STATE_PROJECT = "branch.project";
    private static final String STATE_BRANCH = "branch.name";

    private Optional<Selection> value = Optional.empty();
    /**
     * A saved selection which could not be checked, as no remote branch was known yet, kept until the user changes
     * this filter.
     */
    @Nullable
    private Selection unchecked;
    private BranchPopupAction action;

    @Override
    public AnAction getAction(Project project) {
        action = new BranchPopupAction(project, "Branch");
        return action;
    }

    /**
     * The remote branches of every Gerrit project the repositories of the project belong to.
     */
    public static Map<String, Set<String>> getRemoteBranches(Project project) {
        Map<String, Set<String>> branches = new HashMap<>();
        for (GitRepository repository : GerritGitUtil.getInstance().getRepositories(project)) {
            String name = getNameForRepository(repository);
            // a repository without remote branches yet says nothing about what a saved branch should be
            if (name.isEmpty() || repository.getBranches().getRemoteBranches().isEmpty()) {
                continue;
            }
            Set<String> names = branches.computeIfAbsent(name, key -> new HashSet<>());
            for (GitRemoteBranch branch : repository.getBranches().getRemoteBranches()) {
                names.add(branch.getNameForRemoteOperations());
            }
        }
        return branches;
    }

    @Override
    public void saveState(@NotNull Map<String, String> state) {
        (value.isPresent() ? value : Optional.ofNullable(unchecked)).ifPresent(selection -> {
            state.put(STATE_PROJECT, selection.project);
            if (selection.branch != null) {
                state.put(STATE_BRANCH, selection.branch);
            }
        });
    }

    @Override
    public void restoreState(@NotNull Map<String, String> state, @NotNull FilterEnvironment environment) {
        value = Optional.empty();
        unchecked = null;
        String project = state.get(STATE_PROJECT);
        String branch = state.get(STATE_BRANCH);
        // a branch which is gone from the remotes, or a project which no repository belongs to any more, would only
        // list nothing
        Map<String, Set<String>> known = environment.getRemoteBranches();
        Set<String> branches = project != null ? known.get(project) : null;
        if (project != null && known.isEmpty()) {
            unchecked = new Selection(project, branch);
        } else if (branches != null && (branch == null || branches.contains(branch))) {
            value = Optional.of(new Selection(project, branch));
        }
        if (action != null) {
            action.showValue();
        }
    }

    @Override
    @Nullable
    public String getSearchQueryPart() {
        if (value.isPresent()) {
            return value.get().getQuery();
        } else {
            return null;
        }
    }

    public final class BranchPopupAction extends BasePopupAction {
        private final Project project;

        public BranchPopupAction(Project project, String filterName) {
            super(filterName);
            this.project = project;
            updateFilterValueLabel(value.map(Selection::getLabel).orElse("All"));
        }

        void showValue() {
            updateFilterValueLabel(value.map(Selection::getLabel).orElse("All"));
        }

        @Override
        protected void createActions(Consumer<AnAction> actionConsumer) {
            actionConsumer.consume(new DumbAwareAction("All") {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    value = Optional.empty();
                    unchecked = null;
                    updateFilterValueLabel("All");
                    fireFilterChanged();
                }
            });
            Iterable<GitRepository> repositories = GerritGitUtil.getInstance().getRepositories(project);
            for (final GitRepository repository : repositories) {
                DefaultActionGroup group = new DefaultActionGroup();
                group.add(new Separator(getNameForRepository(repository)));
                group.add(new DumbAwareAction("All") {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        value = Optional.of(new Selection(getNameForRepository(repository), null));
                        unchecked = null;
                        showValue();
                        fireFilterChanged();
                    }
                });
                List<GitRemoteBranch> branches = new ArrayList<>(repository.getBranches().getRemoteBranches());
                branches.sort(Comparator.comparing(GitRemoteBranch::getNameForRemoteOperations));
                for (final GitRemoteBranch branch : branches) {
                    if (!branch.getNameForRemoteOperations().equals("HEAD")) {
                        group.add(new DumbAwareAction(branch.getNameForRemoteOperations()) {
                            @Override
                            public void actionPerformed(AnActionEvent e) {
                                value = Optional.of(new Selection(getNameForRepository(repository),
                                        branch.getNameForRemoteOperations()));
                                unchecked = null;
                                showValue();
                                fireFilterChanged();
                            }
                        });
                    }
                }
                actionConsumer.consume(group);
            }
        }
    }

    private static String getNameForRepository(GitRepository repository) {
        List<String> projectNames = GerritRemotes.getProjectNames(repository.getProject(), repository.getRemotes());
        return projectNames.isEmpty() ? "" : projectNames.get(0);
    }

    private static final class Selection {
        private final String project;
        @Nullable
        private final String branch;

        private Selection(String project, @Nullable String branch) {
            this.project = project;
            this.branch = branch;
        }

        String getLabel() {
            return branch == null ? String.format("All (%s)", project) : String.format("%s (%s)", branch, project);
        }

        String getQuery() {
            if (branch != null) {
                return String.format("(project:%s+branch:%s)", project, branch);
            } else {
                return String.format("project:%s", project);
            }
        }
    }
}
