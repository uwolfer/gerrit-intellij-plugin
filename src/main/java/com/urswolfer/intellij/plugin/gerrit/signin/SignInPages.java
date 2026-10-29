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

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.util.ExceptionUtil;
import com.urswolfer.gerrit.client.rest.http.SignInPageException;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import org.jetbrains.annotations.Nullable;

/**
 * What to tell the user about a request which a sign-in page answered.
 *
 * @author Urs Wolfer
 */
public final class SignInPages {

    private SignInPages() {
    }

    /**
     * The REST client wraps it in a RestApiException.
     */
    @Nullable
    public static SignInPageException find(@Nullable Throwable failure) {
        return failure == null ? null : ExceptionUtil.findCause(failure, SignInPageException.class);
    }

    /**
     * @return the text for the failure, with what can be done about it in this IDE; {@code null} where it is not
     *         about a sign-in page
     */
    @Nullable
    public static String describe(Throwable failure) {
        SignInPageException page = find(failure);
        if (page == null) {
            return null;
        }
        String text = GerritBundle.message("signIn.page", page.getPageOrigin());
        if (BrowserSignIn.find() != null) {
            return text + " " + GerritBundle.message("signIn.page.signIn");
        }
        // from 2026.2 on, no sign-in means the browser's plugin is switched off or missing
        boolean newEnough = ApplicationInfo.getInstance().getBuild().getBaselineVersion() >= 262;
        return text + " " + (newEnough || !BrowserSignIn.EP_NAME.getExtensionList().isEmpty()
            ? GerritBundle.message("signIn.page.noBrowser")
            : GerritBundle.message("signIn.page.needsNewerIde"));
    }
}
