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

import java.util.Map;

/**
 * Which of the actions Gerrit offers for a change the list may enable. Gerrit leaves out the actions the user may not
 * perform, and marks the ones which do not apply right now as not enabled; both mean disabled here, as does a server
 * which does not report actions.
 */
final class ChangeActionAvailability {
    private ChangeActionAvailability() {}

    /**
     * Gerrit offers rebase for a change which is up to date as well, with the tip of the branch as the one base which
     * does not change anything, so the action only has to be there; see {@link #canRebaseOnTip}.
     */
    static boolean canRebase(ChangeInfo change) {
        return rebaseAction(change) != null;
    }

    static boolean canRebaseOnTip(ChangeInfo change) {
        ActionInfo action = rebaseAction(change);
        return action != null && Boolean.TRUE.equals(action.enabled);
    }

    private static ActionInfo rebaseAction(ChangeInfo change) {
        if (!ChangeStatus.NEW.equals(change.status) || change.revisions == null || change.currentRevision == null) {
            return null;
        }
        // a revision action, unlike abandon, restore and revert
        RevisionInfo current = change.revisions.get(change.currentRevision);
        return current != null && current.actions != null ? current.actions.get("rebase") : null;
    }

    static boolean canAbandon(ChangeInfo change) {
        return isEnabled(change.actions, "abandon");
    }

    static boolean canRestore(ChangeInfo change) {
        return ChangeStatus.ABANDONED.equals(change.status) && isEnabled(change.actions, "restore");
    }

    static boolean canRevert(ChangeInfo change) {
        return ChangeStatus.MERGED.equals(change.status) && isEnabled(change.actions, "revert");
    }

    private static boolean isEnabled(Map<String, ActionInfo> actions, String key) {
        ActionInfo action = actions != null ? actions.get(key) : null;
        return action != null && Boolean.TRUE.equals(action.enabled);
    }
}
