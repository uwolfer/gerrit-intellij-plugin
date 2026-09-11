/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.urswolfer.intellij.plugin.gerrit.extension;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.ui.VcsCloneComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.util.ui.cloneDialog.VcsCloneDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parts based on org.jetbrains.plugins.github.GithubCheckoutProvider
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class GerritCheckoutProvider implements CheckoutProvider {
    /**
     * The clone UI is provided by {@link #buildVcsCloneComponent}; this entry point only exists for callers which
     * still bring up a checkout by themselves. It shows the dialog which hosts that UI.
     */
    @Override
    public void doCheckout(@NotNull Project project, @Nullable Listener listener) {
        VcsCloneDialog dialog = new VcsCloneDialog.Builder(project).forVcs(GerritCheckoutProvider.class);
        if (!dialog.showAndGet()) {
            return;
        }
        dialog.doClone(listener != null ? listener : ProjectLevelVcsManager.getInstance(project).getCompositeCheckoutListener());
    }

    @NotNull
    @Override
    public VcsCloneComponent buildVcsCloneComponent(@NotNull Project project,
                                                    @NotNull ModalityState modalityState,
                                                    @NotNull VcsCloneDialogComponentStateListener dialogStateListener) {
        return new GerritCloneComponent(project, modalityState, dialogStateListener);
    }

    @Override
    public String getVcsName() {
        return "Gerrit";
    }
}
