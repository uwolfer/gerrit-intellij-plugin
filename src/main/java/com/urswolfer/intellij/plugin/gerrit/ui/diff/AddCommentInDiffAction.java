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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

/**
 * Keymap entry for adding a comment in a Gerrit diff, so that the shortcut can be configured in
 * "Settings | Keymap". The action carrying the actual state is built per diff editor by
 * {@link CommentsDiffTool}; this one looks it up and delegates to it.
 *
 * @author Urs Wolfer
 */
public class AddCommentInDiffAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(AnActionEvent e) {
        AddCommentAction addCommentAction = findAddCommentAction(e);
        if (addCommentAction != null) {
            addCommentAction.actionPerformed(e);
        }
    }

    @Override
    public void update(AnActionEvent e) {
        AddCommentAction addCommentAction = findAddCommentAction(e);
        e.getPresentation().setVisible(addCommentAction != null);
        if (addCommentAction == null) {
            e.getPresentation().setEnabled(false);
            return;
        }
        addCommentAction.update(e);
    }

    @Nullable
    private static AddCommentAction findAddCommentAction(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            editor = e.getData(DiffDataKeys.CURRENT_EDITOR);
        }
        return editor != null ? editor.getUserData(CommentsDiffTool.ADD_COMMENT_ACTION) : null;
    }
}
