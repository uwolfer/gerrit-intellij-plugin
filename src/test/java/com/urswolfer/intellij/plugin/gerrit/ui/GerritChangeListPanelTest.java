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

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

public class GerritChangeListPanelTest {

    @Test
    public void testStatusOfOpenChange() {
        Assert.assertEquals("", GerritChangeListPanel.getStatus(change(ChangeStatus.NEW, null, true)));
    }

    @Test
    public void testStatusOfWorkInProgress() {
        Assert.assertEquals("WIP", GerritChangeListPanel.getStatus(change(ChangeStatus.NEW, true, true)));
    }

    @Test
    public void testStatusOfWorkInProgressWithMergeConflict() {
        Assert.assertEquals("WIP, Merge Conflict", GerritChangeListPanel.getStatus(change(ChangeStatus.NEW, true, false)));
    }

    @Test
    public void testStatusOfClosedChangeLeavesOutWorkInProgress() {
        Assert.assertEquals("Abandoned", GerritChangeListPanel.getStatus(change(ChangeStatus.ABANDONED, true, null)));
    }

    private static ChangeInfo change(ChangeStatus status, Boolean workInProgress, Boolean mergeable) {
        ChangeInfo change = new ChangeInfo();
        change.status = status;
        change.workInProgress = workInProgress;
        change.mergeable = mergeable;
        return change;
    }
}
