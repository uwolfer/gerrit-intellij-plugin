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

import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubLoginPanel
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class LoginPanel {
    public static final String LOGIN_CREDENTIALS_INFO =
        "* If logging in with your normal Gerrit login credentials fails, you need to set a HTTP access password" +
        " for your account in the Gerrit Web Application (Settings > HTTP Password).";

    private JPanel pane;
    private JTextField hostTextField;
    private JTextField loginTextField;
    private JPasswordField passwordField;
    private JTextPane gerritLoginInfoTestField;

    public LoginPanel(final LoginDialog dialog) {
        hostTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                SettingsPanel.fixUrl(hostTextField);
            }
        });
        DocumentListener listener = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                dialog.clearErrors();
            }
        };
        loginTextField.getDocument().addDocumentListener(listener);
        passwordField.getDocument().addDocumentListener(listener);
        gerritLoginInfoTestField.setText(LOGIN_CREDENTIALS_INFO);
        gerritLoginInfoTestField.setMargin(new Insets(5, 0, 0, 0));
        gerritLoginInfoTestField.setBackground(UIUtil.TRANSPARENT_COLOR);
    }

    public JComponent getPanel() {
        return pane;
    }

    public void setHost(final String host) {
        hostTextField.setText(host);
    }

    public void setLogin(final String login) {
        loginTextField.setText(login);
    }

    public void setPassword(final String password) {
        passwordField.setText(password);
    }

    public String getHost() {
        return hostTextField.getText().trim();
    }

    public String getLogin() {
        return loginTextField.getText().trim();
    }

    public String getPassword() {
        return String.valueOf(passwordField.getPassword());
    }

    public JComponent getPreferrableFocusComponent() {
        return hostTextField.getText().isEmpty() ? hostTextField : loginTextField;
    }
}

