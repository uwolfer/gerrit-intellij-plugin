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

import com.intellij.util.EventDispatcher;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.Map;

/**
 * @author Thomas Forrer
 */
public abstract class AbstractChangesFilter implements ChangesFilter  {
    private final EventDispatcher<Listener> eventDispatcher = EventDispatcher.create(Listener.class);

    /**
     * Adds what differs from the default to the state; a filter at its default adds nothing.
     */
    public abstract void saveState(@NotNull Map<String, String> state);

    /**
     * Takes over what {@link #saveState} wrote, or the default for a value which is missing or does not fit any
     * more. Does not notify the listeners.
     */
    public abstract void restoreState(@NotNull Map<String, String> state, @NotNull FilterEnvironment environment);

    public void addListener(Listener listener) {
        eventDispatcher.addListener(listener);
    }

    /**
     * Puts the filter back to what it is set to at the start, without notifying the listeners.
     */
    void reset() {
        restoreState(Map.of(), FilterEnvironment.UNKNOWN);
    }

    protected void fireFilterChanged() {
        eventDispatcher.getMulticaster().filterChanged();
    }

    public interface Listener extends EventListener {
        void filterChanged();
    }
}
