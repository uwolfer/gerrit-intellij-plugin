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

import javax.swing.*;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.table.TableView;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ReviewInput;

/**
 * @author Urs Wolfer
 */
public class ReviewAction extends AnAction implements DumbAware {
    public static final String CODE_REVIEW = "Code-Review";
    public static final String VERIFIED = "Verified";

    private final TableView myTable;
    private final String label;
    private final int rating;

    public ReviewAction(TableView table, String label, int rating, Icon icon) {
        super("" + rating, "Review Change with " + rating, icon);
        this.myTable = table;
        this.label = label;
        this.rating = rating;
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final GerritSettings settings = GerritSettings.getInstance();

        final ChangeInfo selectedChange = (ChangeInfo) myTable.getSelectedObject();
        assert selectedChange != null;

        final ChangeInfo changeDetails = GerritUtil.getChangeDetails(GerritApiUtil.getApiUrl(),
                settings.getLogin(), settings.getPassword(),
                selectedChange.getNumber());

        final ReviewInput reviewInput = new ReviewInput();
        reviewInput.addLabel(label, rating);
        GerritUtil.postReview(GerritApiUtil.getApiUrl(), settings.getLogin(), settings.getPassword(),
                changeDetails.getId(), changeDetails.getCurrentRevision(), reviewInput);
    }
}
