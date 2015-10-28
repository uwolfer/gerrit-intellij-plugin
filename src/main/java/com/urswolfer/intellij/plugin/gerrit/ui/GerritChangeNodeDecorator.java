/*
 * Copyright 2013-2014 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.SimpleColoredComponent;

/**
 * Interface for node decorators in this plugin's
 * {@link com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser}.
 *
 * Implementations might be added to the corresponding {@link com.google.inject.multibindings.Multibinder} in
 * {@link com.urswolfer.intellij.plugin.gerrit.ui.GerritUiModule}.
 *
 * @author Thomas Forrer
 */
public interface GerritChangeNodeDecorator {
    /**
     * Decorate the {@code component} on the provided {@code change} in the provided {@code project}
     */
    void decorate(Project project, Change change, SimpleColoredComponent component, ChangeInfo selectedChange);

    /**
     * This method is called, when a new change is selected in the changes list panel
     */
    void onChangeSelected(Project project, ChangeInfo selectedChange);
}
