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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.table.TableView;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.SubmitInput;

/**
 * @author Urs Wolfer
 */
public class SubmitAction extends AnAction implements DumbAware {
    private final TableView myTable;

    public SubmitAction(TableView table) {
        super("Submit", "Submit Change", AllIcons.Actions.Export);
        this.myTable = table;
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final GerritSettings settings = GerritSettings.getInstance();

        final ChangeInfo selectedChange = (ChangeInfo) myTable.getSelectedObject();
        assert selectedChange != null;

        final ChangeInfo changeDetails = GerritUtil.getChangeDetails(GerritApiUtil.getApiUrl(),
                settings.getLogin(), settings.getPassword(),
                selectedChange.getNumber());

        final SubmitInput submitInput = new SubmitInput();
        GerritUtil.postSubmit(GerritApiUtil.getApiUrl(), settings.getLogin(), settings.getPassword(),
                changeDetails.getId(), submitInput);
    }
}
