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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import org.jetbrains.annotations.Nullable;

/**
 * Goes to the next or previous comment of a Gerrit diff. Not updated in the background: the side-by-side viewer,
 * which tells how the lines of its sides align, wants the event dispatch thread.
 * <p>
 * TODO once the minimum IDE has AnAction.getActionUpdateThread (2020.3 has not, 2026.2 has): return
 *  ActionUpdateThread.EDT, which 2026.2 only infers from update() being overridden without UpdateInBackground.
 *
 * <p>
 * Settled, a review should not raise these again:
 *
 * <ul>
 *   <li>the shortcuts are those of Previous/Next Occurrence, as in the bundled GitHub plugin; the actions are also
 *   registered on each diff editor, so that they come before Next Occurrence there, rather than through an {@code
 *   ActionPromoter};</li>
 *   <li>from a shortcut they stay enabled in every Gerrit diff and show a hint at the ends or without comments,
 *   rather than letting the key go on to the last search;</li>
 *   <li>they stop at the ends; Gerrit's web UI goes on to the next file, which the diff chain would have to do.</li>
 * </ul>
 */
public abstract class GoToCommentAction extends DumbAwareAction {
    public static final String NEXT_ID = "Gerrit.NextComment";
    public static final String PREVIOUS_ID = "Gerrit.PreviousComment";

    private final boolean forward;

    GoToCommentAction(boolean forward) {
        this.forward = forward;
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = findEditor(e);
        CommentNavigator navigator = editor != null ? editor.getUserData(CommentNavigator.KEY) : null;
        e.getPresentation().setVisible(navigator != null);
        // the shortcut, whichever comment is next, so that it shows where there is none rather than going on to what
        // else has it, such as Next Occurrence to the results of the last search
        e.getPresentation().setEnabled(navigator != null
            && (DiffUtil.isFromShortcut(e) || navigator.canGo(editor, forward)));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = findEditor(e);
        CommentNavigator navigator = editor != null ? editor.getUserData(CommentNavigator.KEY) : null;
        if (navigator != null && !navigator.go(editor, forward)) {
            HintManager.getInstance().showInformationHint(editor,
                forward ? GerritBundle.message("diff.noComment.down") : GerritBundle.message("diff.noComment.up"));
        }
    }

    @Nullable
    private static Editor findEditor(AnActionEvent e) {
        Editor editor = e.getData(DiffDataKeys.CURRENT_EDITOR);
        return editor != null ? editor : e.getData(CommonDataKeys.EDITOR);
    }

    public static final class Next extends GoToCommentAction {
        public Next() {
            super(true);
        }
    }

    public static final class Previous extends GoToCommentAction {
        public Previous() {
            super(false);
        }
    }
}
