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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.table.TableView;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;

import javax.swing.*;

/**
 * @author Urs Wolfer
 */
public abstract class AbstractChangeAction extends AnAction implements DumbAware {

    public AbstractChangeAction(String text, String description, Icon icon) {
        super(text, description, icon);
    }

    protected ChangeInfo getSelectedChange(AnActionEvent anActionEvent) {
        final TableView table = (TableView) anActionEvent.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        assert table != null;
        final ChangeInfo selectedChange = (ChangeInfo) table.getSelectedObject();
        assert selectedChange != null;
        return selectedChange;
    }

    protected ChangeInfo getChangeDetail(ChangeInfo selectedChange) {
        final GerritSettings settings = GerritSettings.getInstance();

        return GerritUtil.getChangeDetails(GerritApiUtil.getApiUrl(),
                settings.getLogin(), settings.getPassword(),
                selectedChange.getNumber());
    }
}
