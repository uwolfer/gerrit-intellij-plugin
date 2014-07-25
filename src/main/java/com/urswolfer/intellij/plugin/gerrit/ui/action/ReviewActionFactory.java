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

import com.google.inject.Inject;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

import javax.swing.*;

/**
 * @author Thomas Forrer
 */
public class ReviewActionFactory {
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private SubmitAction submitAction;
    @Inject
    private SelectedRevisions selectedRevisions;
    @Inject
    private NotificationService notificationService;

    public ReviewAction get(String label, int rating, Icon icon, boolean showDialog) {
        return new ReviewAction(label, rating, icon, showDialog,
                selectedRevisions, gerritUtil, submitAction, notificationService, gerritSettings);
    }
}
