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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

/**
 * @author Urs Wolfer
 */
public class ReviewDialog extends DialogWrapper {

    private final ReviewPanel reviewPanel;

    public ReviewDialog(Project project) {
        super(project, true);
        reviewPanel = new ReviewPanel(project);
        setTitle("Review Change");
        setOKButtonText("Review");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        return reviewPanel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return reviewPanel.getPreferrableFocusComponent();
    }

    public ReviewPanel getReviewPanel() {
        return reviewPanel;
    }
}