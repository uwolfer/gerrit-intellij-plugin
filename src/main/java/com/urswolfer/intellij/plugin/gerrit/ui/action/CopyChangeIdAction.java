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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

import java.awt.datatransfer.StringSelection;

/**
 * @author Wurstmeister
 */
public class CopyChangeIdAction extends AbstractChangeAction {
    private final NotificationService notificationService = NotificationService.getInstance();

    public CopyChangeIdAction() {
        super("Copy", "Copy Change-ID", AllIcons.Actions.Copy);
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        ChangeInfo changeDetails = selectedChange.get();
        String stringToCopy = changeDetails.changeId;
        CopyPasteManager.getInstance().setContents(new StringSelection(stringToCopy));
        Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
        NotificationBuilder builder = new NotificationBuilder(project, "Copy", "Copied Change-ID to clipboard.");
        notificationService.notify(builder);
    }

}
