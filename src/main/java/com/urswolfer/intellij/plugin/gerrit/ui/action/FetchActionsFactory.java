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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.inject.Inject;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;

import java.util.concurrent.Callable;

/**
 * @author Thomas Forrer
 */
public class FetchActionsFactory {
    private final GerritUtil gerritService;
    private final GerritGitUtil gerritGitUtil;

    @Inject
    public FetchActionsFactory(GerritUtil gerritService,
                               GerritGitUtil gerritGitUtil) {
        this.gerritService = gerritService;
        this.gerritGitUtil = gerritGitUtil;
    }

    public FetchAction get() {
        return get(null);
    }

    public FetchAction get(Callable<Void> successCallable) {
        return new FetchAction(gerritGitUtil, gerritService, successCallable);
    }
}
