/*
 * Copyright 2026 Urs Wolfer
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.urswolfer.intellij.plugin.gerrit.ui.SafeHtmlTextEditor;
import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Asks for the message which goes with an action on a change, such as abandoning it.
 */
class ChangeMessageDialog extends DialogWrapper {
    private final SafeHtmlTextEditor editor;

    ChangeMessageDialog(Project project, String title, String okText, String initialMessage) {
        super(project, true);
        editor = new SafeHtmlTextEditor(project);
        editor.getMessageField().setText(initialMessage);
        setTitle(title);
        setOKButtonText(okText);
        init();
    }

    /** Null when there is none, which Gerrit takes to mean no message. */
    @Nullable
    String message() {
        // not String#trim(): a pasted message can be surrounded by whitespace which it does not remove
        String message = Whitespace.trim(editor.getMessageField().getText());
        return message.isEmpty() ? null : message;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return editor;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return editor.getMessageField();
    }
}
