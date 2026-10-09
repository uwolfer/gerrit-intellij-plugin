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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.List;
import java.util.Set;

/**
 * Which files of the patch set selected in the change list are reviewed. The change list loads them when a patch set
 * is selected, and the diff and the toggle action tell it what they marked, so that the tree shows it at once. All of
 * it on the event dispatch thread.
 */
@Service(Service.Level.PROJECT)
public final class ReviewedFilesService {
    private final Project project;
    private final ReviewedFiles state = new ReviewedFiles();
    private final EventDispatcher<Listener> eventDispatcher = EventDispatcher.create(Listener.class);

    public ReviewedFilesService(Project project) {
        this.project = project;
    }

    public static ReviewedFilesService getInstance(Project project) {
        return project.getService(ReviewedFilesService.class);
    }

    /**
     * @param parent disposed when the listener goes out of scope; this service outlives every listener it has
     */
    public void addListener(Listener listener, Disposable parent) {
        eventDispatcher.addListener(listener, parent);
    }

    /**
     * The patch set whose files are about to be {@link #loaded}; a null change or revision selects nothing.
     */
    void startLoading(@Nullable ChangeInfo change, @Nullable String revisionId) {
        if (change == null || revisionId == null) {
            state.clear();
            return;
        }
        state.startLoading(change.id, revisionId);
    }

    /** Keeps what is shown when the files could not be loaded, rather than taking every tick away. */
    void loadFailed(ChangeInfo change, String revisionId) {
        state.loadFailed(change.id, revisionId);
    }

    void loaded(ChangeInfo change, String revisionId, Set<String> reviewed) {
        if (state.loaded(change.id, revisionId, reviewed)) {
            eventDispatcher.getMulticaster().reviewedFilesChanged();
        }
    }

    public boolean isReviewed(String path) {
        return state.isReviewed(path);
    }

    public boolean isSelected(ChangeInfo change, String revisionId) {
        return change != null && state.isSelected(change.id, revisionId);
    }

    public boolean toggleTarget(ChangeInfo change, String revisionId, Collection<String> paths) {
        // the state is that of the selected patch set; of another one nothing is known, so the files get marked
        return !isSelected(change, revisionId) || state.toggleTarget(paths);
    }

    /**
     * Marks the files in Gerrit, and in the tree once Gerrit has taken them. Files known to have the state are left
     * alone, as in Gerrit's web UI; those marked while another patch set is selected are marked in Gerrit only.
     */
    public void setReviewed(ChangeInfo change, String revisionId, Collection<String> paths, boolean reviewed) {
        List<String> toMark = new ArrayList<>();
        for (String path : paths) {
            if (!state.isSelected(change.id, revisionId) || state.isReviewed(path) != reviewed) {
                toMark.add(path);
            }
        }
        if (toMark.isEmpty()) {
            return;
        }
        GerritUtil.getInstance().setReviewed(change._number, revisionId, toMark, reviewed, project, marked -> {
            boolean changed = false;
            for (String path : marked) {
                changed |= state.mark(change.id, revisionId, path, reviewed);
            }
            if (changed) {
                eventDispatcher.getMulticaster().reviewedFilesChanged();
            }
        });
    }

    /**
     * Gives all the files the state, which is a toggle if they differ: see {@link ReviewedFiles#toggleTarget}.
     */
    public void toggle(ChangeInfo change, String revisionId, Collection<String> paths) {
        setReviewed(change, revisionId, paths, toggleTarget(change, revisionId, paths));
    }

    public interface Listener extends EventListener {
        void reviewedFilesChanged();
    }
}
