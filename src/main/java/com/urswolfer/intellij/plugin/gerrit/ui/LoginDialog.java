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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Parts based on org.jetbrains.plugins.github.ui.GithubLoginDialog
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class LoginDialog extends DialogWrapper {

    private final Logger log;

    private final LoginPanel loginPanel;
    private final Project project;
    private final GerritUtil gerritUtil;
    private final GerritSettings gerritSettings;

    // TODO: login must be merged with tasks server settings
    public LoginDialog(final Project project, final GerritSettings gerritSettings, final GerritUtil gerritUtil, Logger log) {
        super(project, true);
        this.gerritUtil = gerritUtil;
        this.gerritSettings = gerritSettings;
        this.project = project;
        this.log = log;
        loginPanel = new LoginPanel(this);
        loginPanel.setHost(gerritSettings.getHost());
        loginPanel.setLogin(gerritSettings.getLogin());
        loginPanel.setPassword(gerritSettings.getPassword());
        setTitle("Login to Gerrit");
        setOKButtonText("Login");
        init();
    }

    @Override
    @NotNull
    protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
        return loginPanel.getPanel();
    }

    @Override
    protected String getHelpId() {
        return "login_to_gerrit";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return loginPanel.getPreferrableFocusComponent();
    }

    @Override
    protected void doOKAction() {
        final String login = loginPanel.getLogin();
        final String password = loginPanel.getPassword();
        final String host = loginPanel.getHost();
        GerritAuthData.Basic gerritAuthData = new GerritAuthData.Basic(host, login, password);
        try {
            boolean loggedSuccessfully = gerritUtil.checkCredentials(project, gerritAuthData);
            if (loggedSuccessfully) {
                gerritSettings.setLogin(login);
                gerritSettings.setPassword(password);
                gerritSettings.setHost(host);
                super.doOKAction();
            } else {
                setErrorText("Can't login with given credentials");
            }
        } catch (Exception e) {
            log.info(e);
            setErrorText("Can't login: " + gerritUtil.getErrorTextFromException(e));
        }
    }

    public void clearErrors() {
        setErrorText(null);
    }
}
