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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.urswolfer.gerrit.client.rest.bean.ChangeInfo;
import com.urswolfer.gerrit.client.rest.bean.CommentInfo;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Urs Wolfer
 */
public class CommentGutterIconRenderer extends GutterIconRenderer {
    private final CommentInfo fileComment;
    private final ReviewCommentSink reviewCommentSink;
    private final ChangeInfo changeInfo;
    private final RangeHighlighter highlighter;
    private final MarkupModel markup;

    public CommentGutterIconRenderer(CommentInfo fileComment, ReviewCommentSink reviewCommentSink, ChangeInfo changeInfo, RangeHighlighter highlighter, MarkupModel markup) {
        this.fileComment = fileComment;
        this.reviewCommentSink = reviewCommentSink;
        this.changeInfo = changeInfo;
        this.highlighter = highlighter;
        this.markup = markup;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        if (isNewCommentFromMyself()) {
            return AllIcons.Toolwindows.ToolWindowTodo;
        } else {
            return AllIcons.Toolwindows.ToolWindowMessages;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommentGutterIconRenderer that = (CommentGutterIconRenderer) o;

        if (!fileComment.equals(that.fileComment)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return fileComment.hashCode();
    }

    @Nullable
    @Override
    public String getTooltipText() {
        return String.format("<strong>%s</strong><br/>%s",
                fileComment.getAuthor().getName(),
                TextToHtml.textToHtml(fileComment.getMessage()));
    }

    @Nullable
    @Override
    public ActionGroup getPopupMenuActions() {
        if (isNewCommentFromMyself()) {
            DefaultActionGroup actionGroup = new DefaultActionGroup();
            RemoveCommentAction action = new RemoveCommentAction(fileComment, reviewCommentSink, changeInfo, highlighter, markup);
            action.setEnabled(true);
            actionGroup.add(action);

            actionGroup = null; // TODO FIXME: does not work yet, action is always disabled. thus do not return action. remove this line when fixes

            return actionGroup;
        } else {
            return null;
        }
    }

    private boolean isNewCommentFromMyself() {
        String name = fileComment.getAuthor().getName();
        return name != null && name.equals("Myself");
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
        // TODO: remove gutter also when removing comment
        return new RemoveCommentAction(fileComment, reviewCommentSink, changeInfo, highlighter, markup);
    }
}
