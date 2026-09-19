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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import icons.MyIcons;

import java.util.Optional;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.intellij.openapi.project.Project;

/**
 * @author Urs Wolfer
 */
public class OpenInBrowserAction extends AbstractChangeAction {
    public OpenInBrowserAction() {
        super("Open in Gerrit", "Open corresponding link in browser", MyIcons.Gerrit);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }
        String urlToOpen = getUrl(anActionEvent.getProject(), selectedChange.get());
        BrowserUtil.browse(urlToOpen);
    }

    /**
     * Without a host there is no change to open: the url would come out as a bare change number, and the browser
     * would be sent to it. The action waits until the project knows the instance its changes live on.
     */
    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Project project = e.getProject();
        e.getPresentation().setEnabled(project != null
            && !GerritProjectAccount.getInstance(project).getHost().isEmpty());
    }

    private String getUrl(Project project, ChangeInfo change) {
        String url = GerritProjectAccount.getInstance(project).getHost();
        int changeNumber = change._number;
        return String.format("%s/%s", url, changeNumber);
    }

}
