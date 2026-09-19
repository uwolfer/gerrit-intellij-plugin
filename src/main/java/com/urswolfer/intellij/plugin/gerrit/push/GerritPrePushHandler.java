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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.intellij.dvcs.push.PrePushHandler;
import com.intellij.dvcs.push.PushInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Stops a push which would not carry the Gerrit push settings entered for it. Their ref is not written to the
 * repository rows while a value cannot be used, and the push dialog builds its push targets out of those rows
 * without offering a way to mark one as unusable.
 *
 * @author Urs Wolfer
 */
public class GerritPrePushHandler implements PrePushHandler {

    @NotNull
    @Override
    public String getPresentableName() {
        return "Gerrit Push Settings";
    }

    @NotNull
    @Override
    public Result handle(@NotNull Project project,
                         @NotNull List<PushInfo> pushDetails,
                         @NotNull ProgressIndicator indicator) {
        String error = GerritPushPanelHolder.getInstance(project).getValidationError();
        if (error == null) {
            return Result.OK;
        }
        // the push dialog stays open for an aborted push without telling why
        ApplicationManager.getApplication().invokeAndWait(
            () -> Messages.showErrorDialog(project, error, "Cannot Push to Gerrit"),
            indicator.getModalityState());
        return Result.ABORT;
    }
}
