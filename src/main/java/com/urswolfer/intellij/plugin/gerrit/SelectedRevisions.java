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
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.urswolfer.intellij.plugin.gerrit.util.RevisionInfos;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.EventListener;
import java.util.Map;

/**
 * Class keeping record of all selected revisions by change.
 *
 * @author Thomas Forrer
 */
@Service(Service.Level.PROJECT)
public final class SelectedRevisions {
    private final Map<String, String> map = Maps.newHashMap();
    private final EventDispatcher<Listener> eventDispatcher = EventDispatcher.create(Listener.class);

    public static SelectedRevisions getInstance(Project project) {
        return project.getService(SelectedRevisions.class);
    }

    /**
     * @param parent disposed when the listener goes out of scope; this service outlives every listener it has, so
     *               registering without one keeps the listener, and everything it holds, alive for the IDE session
     */
    public void addListener(Listener listener, Disposable parent) {
        eventDispatcher.addListener(listener, parent);
    }

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
        if (currentRevision == null) {
            // don't know why with some changes currentRevision is not set,
            // the revisions map however is usually populated
            currentRevision = getNewestRevision(changeInfo);
        }
        return get(changeInfo.id).or(Optional.fromNullable(currentRevision)).orNull();
    }

    /**
     * @return the revision with the highest patch set number, or {@code null} if the change provides no revisions.
     *         The revisions map has no defined order, so the newest revision cannot be taken from its last entry.
     */
    @VisibleForTesting
    static String getNewestRevision(ChangeInfo changeInfo) {
        if (changeInfo.revisions == null || changeInfo.revisions.isEmpty()) {
            return null;
        }
        return Collections.max(changeInfo.revisions.entrySet(), RevisionInfos.MAP_ENTRY_COMPARATOR).getKey();
    }

    public void put(String changeId, String revisionHash) {
        map.put(changeId, revisionHash);
        eventDispatcher.getMulticaster().selectedRevisionChanged(changeId);
    }

    public void clear() {
        map.clear();
        eventDispatcher.getMulticaster().selectedRevisionChanged(null);
    }

    public interface Listener extends EventListener {
        /**
         * @param changeId the change for which the selected revision changed, or {@code null} if all selections were
         *                 cleared
         */
        void selectedRevisionChanged(@Nullable String changeId);
    }
}
