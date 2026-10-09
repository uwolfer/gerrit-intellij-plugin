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

import java.util.concurrent.TimeUnit;

/**
 * Decides whether the change list loads again when the tool window is shown. Used on the event dispatch thread only.
 */
class ListRefreshOnShow {
    static final long THROTTLE_MILLIS = TimeUnit.SECONDS.toMillis(10);
    /**
     * A load which dies before it reports back, such as one whose project closed, would block every later one.
     */
    static final long RUNNING_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private long lastStarted = Long.MIN_VALUE;
    private long lastFinished = Long.MIN_VALUE;
    private boolean running;

    void loadStarted(long now) {
        lastStarted = now;
        running = true;
    }

    void loadFinished(long now) {
        lastFinished = now;
        running = false;
    }

    /**
     * Asked when the tool window turns from hidden to visible. The load which the content creation starts counts as
     * the last one, so showing the window for the first time does not load a second time.
     */
    boolean shouldReload(long now) {
        if (running && now - lastStarted < RUNNING_TIMEOUT_MILLIS) {
            return false;
        }
        return lastFinished == Long.MIN_VALUE || now - lastFinished >= THROTTLE_MILLIS;
    }
}
