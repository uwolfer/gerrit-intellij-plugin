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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.intellij.dvcs.push.ui.PushTargetTextField;
import com.intellij.openapi.diagnostic.Logger;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.push.GitPushTarget;
import git4idea.push.GitPushTargetPanel;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class GerritPushTargetPanel extends GitPushTargetPanel {

    private static final Logger LOG = Logger.getInstance(GerritPushTargetPanel.class);

    public GerritPushTargetPanel(@NotNull GitRepository repository, @Nullable GitPushTarget defaultTarget, GerritPushOptionsPanel gerritPushOptionsPanel) {
        super(repository, defaultTarget);

        String initialBranch = null;
        if (defaultTarget != null) {
            initialBranch = defaultTarget.getBranch().getNameForRemoteOperations();
        }
        gerritPushOptionsPanel.getGerritPushExtensionPanel().registerGerritPushTargetPanel(this, initialBranch);
    }

    public void updateBranch(String branch) {
        if (branch.isEmpty() || branch.endsWith("/")) {
            return;
        }

        try {
            Field myTargetTextFieldField = getField("myTargetTextField");
            PushTargetTextField myTargetTextField = (PushTargetTextField) myTargetTextFieldField.get(this);

            Field myCurrentTargetField = getField("myCurrentTarget");

            Field myFireOnChangeActionField = getField("myFireOnChangeAction");
            Runnable myFireOnChangeAction = (Runnable) myFireOnChangeActionField.get(this);

            myTargetTextField.setText(branch);
            GitRemoteBranch rb = new GitStandardRemoteBranch(getValue().getBranch().getRemote(), branch, git4idea.GitBranch.DUMMY_HASH);
            myCurrentTargetField.set(this, new GitPushTarget(rb, true));
            if (myFireOnChangeAction != null) {
                myFireOnChangeAction.run();
            }
        } catch (NoSuchFieldException e) {
            LOG.error(e);
        } catch (IllegalAccessException e) {
            LOG.error(e);
        }
    }

    private Field getField(String fieldName) throws NoSuchFieldException {
        Field myFireOnChangeActionField = GitPushTargetPanel.class.getDeclaredField(fieldName);
        myFireOnChangeActionField.setAccessible(true);
        return myFireOnChangeActionField;
    }
}
