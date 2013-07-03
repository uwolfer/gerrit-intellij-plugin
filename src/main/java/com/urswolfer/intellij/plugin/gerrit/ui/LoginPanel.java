/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.UIUtil;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubLoginPanel
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class LoginPanel {
    private JPanel myPane;
    private JTextField myHostTextField;
    private JTextField myLoginTextField;
    private JPasswordField myPasswordField;
    private JTextPane myGerritLoginInfoTestField;

    public LoginPanel(final LoginDialog dialog) {
        myHostTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String text = myHostTextField.getText();
                if (text.endsWith("/")) {
                    myHostTextField.setText(text.substring(0, text.length() - 1));
                }
            }
        });
        DocumentListener listener = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                dialog.clearErrors();
            }
        };
        myLoginTextField.getDocument().addDocumentListener(listener);
        myPasswordField.getDocument().addDocumentListener(listener);
        myGerritLoginInfoTestField.setText("* You need to set a HTTP access password for your account in Gerrit (Settings > HTTP Password).");
        myGerritLoginInfoTestField.setMargin(new Insets(5, 0, 0, 0));
        myGerritLoginInfoTestField.setBackground(UIUtil.TRANSPARENT_COLOR);
    }

    public JComponent getPanel() {
        return myPane;
    }

    public void setHost(final String host) {
        myHostTextField.setText(host);
    }

    public void setLogin(final String login) {
        myLoginTextField.setText(login);
    }

    public void setPassword(final String password) {
        myPasswordField.setText(password);
    }

    public String getHost() {
        return myHostTextField.getText().trim();
    }

    public String getLogin() {
        return myLoginTextField.getText().trim();
    }

    public String getPassword() {
        return String.valueOf(myPasswordField.getPassword());
    }

    public JComponent getPreferrableFocusComponent() {
        return myHostTextField.getText().isEmpty() ? myHostTextField : myLoginTextField;
    }
}

