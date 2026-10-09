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
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBSplitter;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Urs Wolfer
 */
public class ReviewPanel extends JPanel {
    private final EditorTextField messageField;
    private final JCheckBox notifyCheckBox;
    private final JCheckBox submitCheckBox;
    private final DraftCommentsPanel draftsPanel;

    public ReviewPanel(Project project, int changeNr, String revision, List<DraftComment> drafts) {
        super(new BorderLayout());

        SafeHtmlTextEditor editor = new SafeHtmlTextEditor(project);
        messageField = editor.getMessageField();
        if (drafts.isEmpty()) {
            draftsPanel = null;
            add(editor, BorderLayout.CENTER);
        } else {
            draftsPanel = new DraftCommentsPanel(project, changeNr, revision, drafts);
            // the message and the drafts share what height there is, as the user divides it
            JBSplitter splitter = new JBSplitter(true, "Gerrit.ReviewDialog.DraftsProportion", 0.5f);
            splitter.setFirstComponent(editor);
            splitter.setSecondComponent(draftsPanel);
            add(splitter, BorderLayout.CENTER);
        }

        JPanel southPanel = new JPanel();
        BoxLayout southLayout = new BoxLayout(southPanel, BoxLayout.Y_AXIS);
        southPanel.setLayout(southLayout);
        add(southPanel, BorderLayout.SOUTH);

        notifyCheckBox = new JCheckBox(GerritBundle.message("review.notify"), true);
        southPanel.add(notifyCheckBox);

        submitCheckBox = new JCheckBox(GerritBundle.message("review.submit"));
        southPanel.add(submitCheckBox);

        setBorder(BorderFactory.createEmptyBorder());
    }

    public void setMessage(final String message) {
        messageField.setText(message);
    }

    public String getMessage() {
        return messageField.getText().trim();
    }

    public boolean getSubmitChange() {
        return submitCheckBox.isSelected();
    }

    public boolean isDraftsChanged() {
        return draftsPanel != null && draftsPanel.isChanged();
    }

    public boolean getDoNotify() {
        return notifyCheckBox.isSelected();
    }

    public JComponent getPreferrableFocusComponent() {
        return messageField;
    }
}

