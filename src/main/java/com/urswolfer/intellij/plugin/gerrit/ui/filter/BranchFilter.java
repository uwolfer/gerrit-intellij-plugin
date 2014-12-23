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

import java.util.Collections;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction;
import com.urswolfer.intellij.plugin.gerrit.util.UrlUtils;
import git4idea.GitRemoteBranch;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;

/**
 * @author Thomas Forrer
 */
public class BranchFilter extends AbstractChangesFilter {
    @Inject
    private GerritGitUtil gerritGitUtil;

    private Optional<BranchDescriptor> value = Optional.absent();

    @Override
    public AnAction getAction(Project project) {
        return new BranchPopupAction(project, "Branch");
    }

    @Override
    @Nullable
    public String getSearchQueryPart() {
        if (value.isPresent()) {
            return String.format("project:%s+branch:%s",
                    getNameForRepository(value.get().getRepository()),
                    value.get().getBranch().getNameForRemoteOperations());
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
                    value = Optional.absent();
                    updateFilterValueLabel("All");
                    setChanged();
                    notifyObservers(project);
                }
            });
            Iterable<GitRepository> repositories = gerritGitUtil.getRepositories(project);
            for (final GitRepository repository : repositories) {
                DefaultActionGroup group = new DefaultActionGroup();
                group.add(new Separator(getNameForRepository(repository)));
                List<GitRemoteBranch> branches = Lists.newArrayList(repository.getBranches().getRemoteBranches());
                Ordering<GitRemoteBranch> ordering = Ordering.natural().onResultOf(new Function<GitRemoteBranch, String>() {
                    @Override
                    public String apply(GitRemoteBranch branch) {
                        return branch.getNameForRemoteOperations();
                    }
                });
                Collections.sort(branches, ordering);
                for (final GitRemoteBranch branch : branches) {
                    if (!branch.getNameForRemoteOperations().equals("HEAD")) {
                        group.add(new DumbAwareAction(branch.getNameForRemoteOperations()) {
                            @Override
                            public void actionPerformed(AnActionEvent e) {
                                value = Optional.of(new BranchDescriptor(repository, branch));
                                updateFilterValueLabel(branch.getNameForRemoteOperations());
                                setChanged();
                                notifyObservers(project);
                            }
                        });
                    }
                }
                actionConsumer.consume(group);
            }
        }
    }

    private String getNameForRepository(GitRepository repository) {
        return UrlUtils.stripGitExtension(repository.getRoot().getName());
    }

    private static final class BranchDescriptor {
        private final GitRepository repository;
        private final GitRemoteBranch branch;

        private BranchDescriptor(GitRepository repository, GitRemoteBranch branch) {
            this.repository = repository;
            this.branch = branch;
        }

        public GitRepository getRepository() {
            return repository;
        }

        public GitRemoteBranch getBranch() {
            return branch;
        }
    }
}
