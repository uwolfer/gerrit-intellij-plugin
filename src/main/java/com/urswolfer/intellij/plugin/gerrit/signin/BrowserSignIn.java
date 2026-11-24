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

package com.urswolfer.intellij.plugin.gerrit.signin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

/**
 * Signs in to what stands in front of a Gerrit, such as a single sign-on proxy, in a browser inside the IDE, and
 * keeps the cookies it hands out in {@link SignInCookies}.
 *
 * The one implementation uses the IDE's embedded browser and is registered in {@code gerrit-jcef.xml}, which the IDE
 * only loads where the "Web Browser (JCEF)" plugin exists: 2026.2 and later. Before, the browser's classes are part
 * of the platform but out of reach of a plugin's descriptor, and this code never touches them, so that an older IDE
 * runs exactly the code it ran before the sign-in existed. {@code BrowserSignInGateTest} keeps it that way.
 *
 * @author Urs Wolfer
 */
public interface BrowserSignIn {
    ExtensionPointName<BrowserSignIn> EP_NAME = ExtensionPointName.create("com.urswolfer.intellij.plugin.gerrit.browserSignIn");

    /**
     * @return whether the browser can run in this IDE: it can be switched off, or be missing on some systems
     */
    boolean isAvailable();

    /**
     * Shows the Gerrit at the url in a browser until its sign-in is done or the user gives up. Only on the event
     * dispatch thread.
     *
     * @param parent the component to show the browser over, {@code null} for the frame of the project
     * @return whether cookies were stored
     */
    boolean signIn(@Nullable Project project, @Nullable Component parent, String gerritUrl);

    /**
     * @return the sign-in, where this IDE has one which can run
     */
    @Nullable
    static BrowserSignIn find() {
        for (BrowserSignIn signIn : EP_NAME.getExtensionList()) {
            if (signIn.isAvailable()) {
                return signIn;
            }
        }
        return null;
    }
}
