/*
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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.ui.AnActionButton;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class AddCommentAction extends AnActionButton implements DumbAware {

    private final Editor myEditor;
    private final ReviewCommentSink myReviewCommentSink;
    private final ChangeInfo myChangeInfo;
    @Nullable
    private final FilePath myFilePath;

    public AddCommentAction(ReviewCommentSink reviewCommentSink,
                            ChangeInfo changeInfo,
                            @Nullable final Editor editor,
                            @Nullable FilePath filePath) {
        super("Add Comment", "Add a comment at current line", AllIcons.Toolwindows.ToolWindowMessages);
        myReviewCommentSink = reviewCommentSink;
        myChangeInfo = changeInfo;
        myFilePath = filePath;
        myEditor = editor;
    }

    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) return;
        addVersionedComment(project);
    }

    private void addVersionedComment(@NotNull final Project project) {
        if (myEditor == null || myFilePath == null) return;

        final CommentBalloonBuilder builder = new CommentBalloonBuilder();
        final CommentForm commentForm = new CommentForm(project, myFilePath, myReviewCommentSink, myChangeInfo);
        commentForm.setEditor(myEditor);
        final JBPopup balloon = builder.getNewCommentBalloon(commentForm, "Comment");
        balloon.addListener(new JBPopupAdapter() {
            @Override
            public void onClosed(LightweightWindowEvent event) {
                CommentInput comment = commentForm.getComment();
                if (comment != null) {
                    final MarkupModel markup = myEditor.getMarkupModel();
                    final RangeHighlighter highlighter = markup.addLineHighlighter(comment.getLine() - 1, HighlighterLayer.ERROR + 1, null);
                    highlighter.setGutterIconRenderer(new CommentGutterIconRenderer(comment.toCommentInfo(), myReviewCommentSink, myChangeInfo, highlighter, markup));
                }
            }
        });
        commentForm.setBalloon(balloon);
        balloon.showInBestPositionFor(myEditor);
        commentForm.requestFocus();
    }
}
