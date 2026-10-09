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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.urswolfer.intellij.plugin.gerrit.ui.ListRefreshOnShow.RUNNING_TIMEOUT_MILLIS;
import static com.urswolfer.intellij.plugin.gerrit.ui.ListRefreshOnShow.THROTTLE_MILLIS;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ListRefreshOnShowTest {
    private ListRefreshOnShow policy;

    @BeforeMethod
    public void setUp() {
        policy = new ListRefreshOnShow();
    }

    @Test
    public void reloadsWhenTheLastLoadIsOlderThanTheThrottle() {
        policy.loadStarted(0);
        policy.loadFinished(1);
        assertTrue(policy.shouldReload(1 + THROTTLE_MILLIS));
    }

    @Test
    public void throttlesAfterARecentLoad() {
        policy.loadStarted(0);
        policy.loadFinished(1);
        assertFalse(policy.shouldReload(THROTTLE_MILLIS));
    }

    @Test
    public void doesNotReloadWhileALoadRuns() {
        policy.loadFinished(0);
        policy.loadStarted(5 * THROTTLE_MILLIS);
        assertFalse(policy.shouldReload(5 * THROTTLE_MILLIS + 1));
    }

    @Test
    public void reloadsAfterALoadWhichNeverReportedBack() {
        policy.loadStarted(0);
        assertFalse(policy.shouldReload(RUNNING_TIMEOUT_MILLIS - 1));
        assertTrue(policy.shouldReload(RUNNING_TIMEOUT_MILLIS));
    }

    @Test
    public void reloadsWhenNothingWasLoadedYet() {
        assertTrue(policy.shouldReload(0));
    }
}
