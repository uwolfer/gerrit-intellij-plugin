/*
 * Copyright 2013-2014 Urs Wolfer
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

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.AbandonInput;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewDialog;

/**
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class AbandonAction extends AbstractChangeAction {

    public AbandonAction() {
        super("Abandon", "Abandon Change", AllIcons.Actions.Delete);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);

        Optional<ChangeInfo> selectedChange = getSelectedChange(anActionEvent);
        if (!selectedChange.isPresent()) {
            return;
        }

        AbandonInput abandonInput = new AbandonInput();

        final ReviewDialog dialog = new ReviewDialog(project) {
            @Override
            protected void init() {
                super.init();
                setTitle("Abandon Change");
                setOKButtonText("Abandon");
            }
        };
        dialog.getReviewPanel().setSubmitCheckboxVisible(false);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }
        final String message = dialog.getReviewPanel().getMessage();
        if (!Strings.isNullOrEmpty(message)) {
            abandonInput.setMessage(message);
        }

        gerritUtil.postAbandon(selectedChange.get().getId(), abandonInput, project);
    }

    public static class Proxy extends AbandonAction {
        private final AbandonAction delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(AbandonAction.class);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
