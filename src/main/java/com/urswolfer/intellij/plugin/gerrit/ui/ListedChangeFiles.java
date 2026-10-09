/*
 * Copyright 2026 Urs Wolfer
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
import com.intellij.openapi.vcs.changes.Change;

import java.util.List;

/**
 * What a Gerrit changes browser lists: the files of one patch set of a change.
 */
public interface ListedChangeFiles {
    /** @return the change whose files are listed, or null while there are none */
    ChangeInfo getListedChange();

    /** @return the patch set the files are of, or null while there are none */
    String getListedRevision();

    List<Change> getSelectedChanges();
}
