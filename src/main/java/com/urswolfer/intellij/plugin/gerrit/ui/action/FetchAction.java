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

import java.util.TreeMap;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.FetchInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.RevisionInfo;

/**
 * @author Urs Wolfer
 */
public class FetchAction extends AbstractChangeAction {

    public FetchAction() {
        super("Fetch", "Fetch change", AllIcons.Actions.Download);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final ChangeInfo selectedChange = getSelectedChange(anActionEvent);
        final ChangeInfo changeDetails = getChangeDetail(selectedChange);

        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);

        String ref = null;
        final TreeMap<String,RevisionInfo> revisions = changeDetails.getRevisions();
        for (RevisionInfo revisionInfo : revisions.values()) {
            final TreeMap<String,FetchInfo> fetch = revisionInfo.getFetch();
            for (FetchInfo fetchInfo : fetch.values()) {
                ref = fetchInfo.getRef();
            }
        }

        GerritGitUtil.fetchChange(project, ref);
    }
}
