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
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction;
import git4idea.GitRemoteBranch;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * @author Thomas Forrer
 */
public class BranchFilter extends AbstractChangesFilter {
    private final GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
    private final GerritUtil gerritUtil = GerritUtil.getInstance();

    private Optional<BranchDescriptor> value = Optional.empty();

    @Override
    public AnAction getAction(Project project) {
        return new BranchPopupAction(project, "Branch");
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
            updateFilterValueLabel("All");
        }

        @Override
        protected void createActions(Consumer<AnAction> actionConsumer) {
            actionConsumer.consume(new DumbAwareAction("All") {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    value = Optional.empty();
                    updateFilterValueLabel("All");
                    fireFilterChanged();
                }
            });
            Iterable<GitRepository> repositories = gerritGitUtil.getRepositories(project);
            for (final GitRepository repository : repositories) {
                DefaultActionGroup group = new DefaultActionGroup();
                group.add(new Separator(getNameForRepository(repository)));
                group.add(new DumbAwareAction("All") {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        value = Optional.of(new BranchDescriptor(repository));
                        updateFilterValueLabel(String.format("All (%s)", getNameForRepository(repository)));
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
                                value = Optional.of(new BranchDescriptor(repository, branch));
                                updateFilterValueLabel(String.format("%s (%s)", branch.getNameForRemoteOperations(), getNameForRepository(repository)));
                                fireFilterChanged();
                            }
                        });
                    }
                }
                actionConsumer.consume(group);
            }
        }
    }

    private String getNameForRepository(GitRepository repository) {
        List<String> projectNames = gerritUtil.getProjectNames(repository.getProject(), repository.getRemotes());
        return projectNames.isEmpty() ? "" : projectNames.get(0);
    }

    private final class BranchDescriptor {
        private final GitRepository repository;
        private final Optional<GitRemoteBranch> branch;

        private BranchDescriptor(GitRepository repository, GitRemoteBranch branch) {
            this.repository = repository;
            this.branch = Optional.of(branch);
        }

        private BranchDescriptor(GitRepository repository) {
            this.repository = repository;
            this.branch = Optional.empty();
        }

        public String getQuery() {
            if (branch.isPresent()) {
                return String.format("(project:%s+branch:%s)",
                        getNameForRepository(repository),
                        branch.get().getNameForRemoteOperations());
            } else {
                return String.format("project:%s", getNameForRepository(repository));
            }
        }
    }
}
