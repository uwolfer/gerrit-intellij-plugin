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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ChangeActionAvailabilityTest {

    private static ActionInfo action(Boolean enabled) {
        ActionInfo action = new ActionInfo();
        action.enabled = enabled;
        return action;
    }

    private static ChangeInfo change(ChangeStatus status, String key, Boolean enabled) {
        ChangeInfo change = new ChangeInfo();
        change.status = status;
        change.actions = new HashMap<>();
        change.actions.put(key, action(enabled));
        return change;
    }

    private static ChangeInfo changeWithRevisionAction(ChangeStatus status, Boolean enabled) {
        ChangeInfo change = new ChangeInfo();
        change.status = status;
        change.currentRevision = "abc";
        RevisionInfo revision = new RevisionInfo();
        revision.actions = new HashMap<>();
        revision.actions.put("rebase", action(enabled));
        change.revisions = new HashMap<>();
        change.revisions.put("abc", revision);
        return change;
    }

    @Test
    public void restoreNeedsAnAbandonedChangeAndTheEnabledAction() {
        assertTrue(ChangeActionAvailability.canRestore(change(ChangeStatus.ABANDONED, "restore", true)));
        assertFalse(ChangeActionAvailability.canRestore(change(ChangeStatus.ABANDONED, "restore", false)));
        assertFalse(ChangeActionAvailability.canRestore(change(ChangeStatus.ABANDONED, "restore", null)));
        assertFalse(ChangeActionAvailability.canRestore(change(ChangeStatus.NEW, "restore", true)));
        assertFalse(ChangeActionAvailability.canRestore(change(ChangeStatus.ABANDONED, "abandon", true)));
    }

    @Test
    public void revertNeedsAMergedChangeAndTheEnabledAction() {
        assertTrue(ChangeActionAvailability.canRevert(change(ChangeStatus.MERGED, "revert", true)));
        assertFalse(ChangeActionAvailability.canRevert(change(ChangeStatus.MERGED, "revert", false)));
        assertFalse(ChangeActionAvailability.canRevert(change(ChangeStatus.NEW, "revert", true)));
    }

    @Test
    public void rebaseReadsTheActionsOfTheCurrentRevision() {
        assertTrue(ChangeActionAvailability.canRebase(changeWithRevisionAction(ChangeStatus.NEW, true)));
        assertTrue(ChangeActionAvailability.canRebaseOnTip(changeWithRevisionAction(ChangeStatus.NEW, true)));
        // up to date: only another base changes anything
        assertTrue(ChangeActionAvailability.canRebase(changeWithRevisionAction(ChangeStatus.NEW, false)));
        assertFalse(ChangeActionAvailability.canRebaseOnTip(changeWithRevisionAction(ChangeStatus.NEW, false)));
        assertFalse(ChangeActionAvailability.canRebaseOnTip(changeWithRevisionAction(ChangeStatus.NEW, null)));
        assertFalse(ChangeActionAvailability.canRebase(changeWithRevisionAction(ChangeStatus.MERGED, true)));
        // the change level, where abandon and the like are, does not carry it
        assertFalse(ChangeActionAvailability.canRebase(change(ChangeStatus.NEW, "rebase", true)));
    }

    @Test
    public void actionsWhichAreNotReportedDisableEverything() {
        ChangeInfo abandoned = new ChangeInfo();
        abandoned.status = ChangeStatus.ABANDONED;
        assertFalse(ChangeActionAvailability.canRestore(abandoned));
        ChangeInfo merged = new ChangeInfo();
        merged.status = ChangeStatus.MERGED;
        assertFalse(ChangeActionAvailability.canRevert(merged));
        ChangeInfo open = new ChangeInfo();
        open.status = ChangeStatus.NEW;
        assertFalse(ChangeActionAvailability.canRebase(open));
        open.currentRevision = "abc";
        assertFalse(ChangeActionAvailability.canRebase(open));
        open.revisions = new HashMap<>();
        assertFalse(ChangeActionAvailability.canRebase(open));
        open.revisions.put("abc", new RevisionInfo());
        assertFalse(ChangeActionAvailability.canRebase(open));
    }
}
