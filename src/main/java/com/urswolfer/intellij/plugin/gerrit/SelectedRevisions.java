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

package com.urswolfer.intellij.plugin.gerrit;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ChangeInfo;

import java.util.Map;
import java.util.Observable;
import java.util.Set;

/**
 * Class keeping record of all selected revisions by change.
 *
 * @author Thomas Forrer
 */
public class SelectedRevisions extends Observable {
    private final Map<String, String> map = Maps.newHashMap();

    /**
     * @return the selected revision for the provided changeId, or {@link com.google.common.base.Optional#absent()} if
     *         the current revision was selected.
     */
    public Optional<String> get(String changeId) {
        return Optional.fromNullable(map.get(changeId));
    }

    /**
     * @return the selected revision for the provided change info object
     */
    public String get(ChangeInfo changeInfo) {
        String currentRevision = changeInfo.currentRevision;
        if (currentRevision == null && changeInfo.revisions != null) {
            // don't know why with some changes currentRevision is not set,
            // the revisions map however is usually populated
            Set<String> revisionKeys = changeInfo.revisions.keySet();
            if (!revisionKeys.isEmpty()) {
                currentRevision = Iterables.getLast(revisionKeys);
            }
        }
        return get(changeInfo.id).or(Optional.fromNullable(currentRevision)).orNull();
    }

    public void put(String changeId, String revisionHash) {
        map.put(changeId, revisionHash);
        setChanged();
        notifyObservers(changeId);
    }

    public void clear() {
        map.clear();
        setChanged();
        notifyObservers();
    }
}
