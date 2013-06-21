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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.TableView;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.FetchInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.RevisionInfo;

/**
 * @author Urs Wolfer
 */
public class FetchAction extends AnAction implements DumbAware {

    private final TableView myTable;

    public FetchAction(TableView table) {
        super("Fetch", "Fetch change", AllIcons.Actions.Download);
        this.myTable = table;
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final ChangeInfo selectedChange = (ChangeInfo) myTable.getSelectedObject();
        assert selectedChange != null;

        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
        final GerritSettings settings = GerritSettings.getInstance();
        final ChangeInfo changeDetails = GerritUtil.getChangeDetails(GerritApiUtil.getApiUrl(),
                settings.getLogin(), settings.getPassword(),
                selectedChange.getNumber());

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
