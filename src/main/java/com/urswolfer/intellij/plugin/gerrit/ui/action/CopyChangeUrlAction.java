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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

import java.awt.datatransfer.StringSelection;
import java.util.Optional;

/**
 * @author Urs Wolfer
 */
public class CopyChangeUrlAction extends AbstractChangeAction {
    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private final NotificationService notificationService = NotificationService.getInstance();

    public CopyChangeUrlAction() {
        super(AllIcons.Actions.Copy);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        String url = OpenInBrowserAction.getUrl(gerritSettings.getHost(), selectedChange.get());
        CopyPasteManager.getInstance().setContents(new StringSelection(url));
        NotificationBuilder builder = new NotificationBuilder(anActionEvent.getProject(), "Copy", "Copied change URL to clipboard.");
        notificationService.notify(builder);
    }

    /**
     * Without a host the url would come out as a bare change number.
     */
    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        String host = gerritSettings.getHost();
        e.getPresentation().setEnabled(host != null && !host.isEmpty());
    }
}
