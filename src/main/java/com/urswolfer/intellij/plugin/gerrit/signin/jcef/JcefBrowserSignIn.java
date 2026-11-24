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

package com.urswolfer.intellij.plugin.gerrit.signin.jcef;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.jcef.JBCefApp;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.signin.BrowserSignIn;
import com.urswolfer.intellij.plugin.gerrit.signin.SignInCookies;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

/**
 * The sign-in in the IDE's embedded browser. Registered in {@code gerrit-jcef.xml} only, see {@link BrowserSignIn}.
 *
 * @author Urs Wolfer
 */
public final class JcefBrowserSignIn implements BrowserSignIn {
    private static final Logger LOG = Logger.getInstance(JcefBrowserSignIn.class);

    /**
     * Also true where the IDE runs as root and the browser only shows that it is suspended, which the platform tells
     * the user in the window itself.
     */
    @Override
    public boolean isAvailable() {
        return JBCefApp.isSupported();
    }

    /**
     * Also where the window was cancelled just after the cookies were stored, before it closed by itself.
     */
    @Override
    public boolean signIn(@Nullable Project project, @Nullable Component parent, String gerritUrl) {
        try {
            JBCefApp.getInstance();
        } catch (IllegalStateException e) { // the browser failed to start, which isSupported does not tell
            LOG.warn("The embedded browser could not start", e);
            String title = GerritBundle.message("signIn.title", gerritUrl);
            if (parent != null) {
                Messages.showErrorDialog(parent, GerritBundle.message("signIn.noStart"), title);
            } else {
                Messages.showErrorDialog(project, GerritBundle.message("signIn.noStart"), title);
            }
            return false;
        }
        int before = SignInCookies.getInstance().getVersion(gerritUrl);
        return new BrowserSignInDialog(project, parent, gerritUrl).showAndGet()
            || SignInCookies.getInstance().getVersion(gerritUrl) != before;
    }
}
