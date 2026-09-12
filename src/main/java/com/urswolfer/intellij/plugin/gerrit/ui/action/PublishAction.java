/*
 * Copyright 2013-2016 Urs Wolfer
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

import com.google.common.base.Optional;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

import java.util.Map;

/**
 * @author Urs Wolfer
 */
public class PublishAction extends AbstractLoggedInChangeAction {

    public PublishAction() {
        super("Publish Draft", "Publish Draft Change", AllIcons.Actions.Forward);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Optional<ChangeInfo> selectedChange = getSelectedChange(e);
        if (selectedChange.isPresent() && !canPublish(selectedChange.get())) {
            e.getPresentation().setEnabled(false);
        }
    }

    private boolean canPublish(ChangeInfo selectedChange) {
        if (!ChangeStatus.DRAFT.equals(selectedChange.status)) {
            return false;
        }
        RevisionInfo currentRevision = getCurrentRevision(selectedChange);
        Map<String, ActionInfo> revisionActions = currentRevision != null ? currentRevision.actions : null;
        if (revisionActions == null) {
            // if there are absolutely no actions, assume an older Gerrit instance
            // which does not support receiving actions
            // return false once we drop Gerrit < 2.9 support
            return true;
        }
        ActionInfo publishAction = revisionActions.get("publish");
        return publishAction != null && Boolean.TRUE.equals(publishAction.enabled);
    }

    /** Neither the revisions nor the current revision of a change are guaranteed to be there. */
    private RevisionInfo getCurrentRevision(ChangeInfo selectedChange) {
        if (selectedChange.revisions == null || selectedChange.currentRevision == null) {
            return null;
        }
        return selectedChange.revisions.get(selectedChange.currentRevision);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();

        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        gerritUtil.postPublish(selectedChange.get().id, project);
    }

}
