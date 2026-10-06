/*
 * Copyright 2013-2015 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.util;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;

import java.util.Optional;

/**
 * @author Urs Wolfer
 */
public interface GerritUserDataKeys {
    Key<ChangeInfo> CHANGE = Key.create("gerrit.Change");
    /**
     * The commit of the patch set the diff shows; the one selected in the change list can change while it is open.
     */
    Key<String> REVISION = Key.create("gerrit.Change.Revision");
    Key<Optional<Pair<String, RevisionInfo>>> BASE_REVISION = Key.create("gerrit.Change.BaseRevision");
    /**
     * The parent of a merge commit which the diff against its base shows. Gerrit places a comment on that side with
     * this number; without one, the comment belongs to the auto-merge. Not set for a commit with a single parent.
     */
    Key<Integer> BASE_PARENT = Key.create("gerrit.Change.BaseParent");
}
