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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.urswolfer.intellij.plugin.gerrit.util.RevisionInfos;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Class keeping record of all selected revisions by change.
 *
 * @author Thomas Forrer
 */
@Service(Service.Level.PROJECT)
public final class SelectedRevisions {
    private final Map<String, String> map = new HashMap<>();
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
     * @return the selected revision for the provided changeId, or {@link java.util.Optional#empty()} if
     *         the current revision was selected.
     */
    public Optional<String> get(String changeId) {
        return Optional.ofNullable(map.get(changeId));
    }

    /**
     * @return the selected revision for the provided change info object
     */
    public String get(ChangeInfo changeInfo) {
        return get(changeInfo.id).orElse(currentRevision(changeInfo));
    }

    private static String currentRevision(ChangeInfo changeInfo) {
        if (changeInfo.currentRevision != null) {
            return changeInfo.currentRevision;
        }
        // don't know why with some changes currentRevision is not set,
        // the revisions map however is usually populated
        return getNewestRevision(changeInfo);
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

    /**
     * Forgets the selections which no longer apply after the list has been reloaded: those of changes which are not
     * listed any more, those of a revision the change no longer has, and those of a change which got a new patch set,
     * so that a review does not end up on an outdated one without the user noticing.
     */
    public void retain(Collection<ChangeInfo> previousChanges, Collection<ChangeInfo> reloadedChanges) {
        Map<String, String> previousCurrentRevisions = currentRevisions(previousChanges);
        Map<String, ChangeInfo> reloaded = new HashMap<>();
        for (ChangeInfo change : reloadedChanges) {
            reloaded.put(change.id, change);
        }
        boolean removed = map.entrySet().removeIf(selection -> {
            ChangeInfo change = reloaded.get(selection.getKey());
            return change == null || change.revisions == null || !change.revisions.containsKey(selection.getValue())
                || !Objects.equals(currentRevision(change), previousCurrentRevisions.get(change.id));
        });
        if (removed) {
            eventDispatcher.getMulticaster().selectedRevisionChanged(null);
        }
    }

    private static Map<String, String> currentRevisions(Collection<ChangeInfo> changes) {
        Map<String, String> currentRevisions = new HashMap<>();
        for (ChangeInfo change : changes) {
            currentRevisions.put(change.id, currentRevision(change));
        }
        return currentRevisions;
    }

    public interface Listener extends EventListener {
        /**
         * @param changeId the change for which the selected revision changed, or {@code null} if selections which no
         *                 longer apply after a reload were dropped
         */
        void selectedRevisionChanged(@Nullable String changeId);
    }
}
