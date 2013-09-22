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

    private static Logger LOG = GerritUtil.LOG;

    private final LoginPanel myLoginPanel;
    private final Project myProject;

    // TODO: login must be merged with tasks server settings
    public LoginDialog(final Project project) {
        super(project, true);
        myProject = project;
        myLoginPanel = new LoginPanel(this);
        final GerritSettings settings = GerritSettings.getInstance();
        myLoginPanel.setHost(settings.getHost());
        myLoginPanel.setLogin(settings.getLogin());
        myLoginPanel.setPassword(settings.getPassword());
        setTitle("Login to Gerrit");
        setOKButtonText("Login");
        init();
    }

    @NotNull
    protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
        return myLoginPanel.getPanel();
    }

    @Override
    protected String getHelpId() {
        return "login_to_gerrit";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myLoginPanel.getPreferrableFocusComponent();
    }

    @Override
    protected void doOKAction() {
        final String login = myLoginPanel.getLogin();
        final String password = myLoginPanel.getPassword();
        final String host = myLoginPanel.getHost();
        try {
            boolean loggedSuccessfully = GerritUtil.checkCredentials(myProject, host, login, password);
            if (loggedSuccessfully) {
                final GerritSettings settings = GerritSettings.getInstance();
                settings.setLogin(login);
                settings.setPassword(password);
                settings.setHost(host);
                super.doOKAction();
            } else {
                setErrorText("Can't login with given credentials");
            }
        } catch (Exception e) {
            LOG.info(e);
            setErrorText("Can't login: " + GerritUtil.getErrorTextFromException(e));
        }
    }

    public void clearErrors() {
        setErrorText(null);
    }
}