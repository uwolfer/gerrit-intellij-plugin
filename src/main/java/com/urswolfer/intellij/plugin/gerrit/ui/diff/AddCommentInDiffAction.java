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
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Keymap entry for adding a comment in a Gerrit diff, so that the shortcut can be configured in
 * "Settings | Keymap". The action carrying the actual state is built per diff editor by
 * {@link GerritCommentsDiffExtension}; this one looks it up and delegates to it.
 *
 * @author Urs Wolfer
 */
public class AddCommentInDiffAction extends AnAction implements DumbAware, UpdateInBackground {

    @Override
    public void actionPerformed(AnActionEvent e) {
        AddCommentAction addCommentAction = findAddCommentAction(e);
        if (addCommentAction != null) {
            addCommentAction.addVersionedComment(e.getProject());
        }
    }

    @Override
    public void update(AnActionEvent e) {
        AddCommentAction addCommentAction = findAddCommentAction(e);
        e.getPresentation().setVisible(addCommentAction != null);
        e.getPresentation().setEnabled(addCommentAction != null && addCommentAction.canComment(e.getProject())
            && !isTypedInFileEditor(e));
    }

    /**
     * A bare letter which the keymap gave this action, as "C" was in the diff (issue #410), is typing in the editor
     * of a file: disabled, the key goes to the text.
     */
    private static boolean isTypedInFileEditor(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null || editor.getEditorKind() != EditorKind.MAIN_EDITOR
            || !(e.getInputEvent() instanceof KeyEvent)) {
            return false;
        }
        KeyEvent key = (KeyEvent) e.getInputEvent();
        boolean modified = (key.getModifiersEx()
            & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
        return !modified && key.getKeyChar() != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(key.getKeyChar());
    }

    @Nullable
    private static AddCommentAction findAddCommentAction(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            editor = e.getData(DiffDataKeys.CURRENT_EDITOR);
        }
        return editor != null ? editor.getUserData(GerritCommentsDiffExtension.ADD_COMMENT_ACTION) : null;
    }
}
