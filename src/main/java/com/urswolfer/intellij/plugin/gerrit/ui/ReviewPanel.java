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

package com.urswolfer.intellij.plugin.gerrit.ui;

import javax.swing.*;

/**
 * @author Urs Wolfer
 */
public class ReviewPanel {
    private JPanel myPane;
    private JTextArea myMessageField;
    private JCheckBox mySubmitCheckBox;

    public ReviewPanel(final ReviewDialog dialog) {
    }

    public JComponent getPanel() {
        return myPane;
    }

    public void setMessage(final String message) {
        myMessageField.setText(message);
    }

    public String getMessage() {
        return myMessageField.getText().trim();
    }

    public boolean getSubmitChange() {
        return mySubmitCheckBox.isSelected();
    }

    public JComponent getPreferrableFocusComponent() {
        return myMessageField;
    }
}

