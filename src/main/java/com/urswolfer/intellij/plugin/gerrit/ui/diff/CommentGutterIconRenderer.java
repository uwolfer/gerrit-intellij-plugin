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

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.text.DateFormatUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.CommentHelper;
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * @author Urs Wolfer
 */
public class CommentGutterIconRenderer extends GutterIconRenderer {
    private final CommentsDiffTool commentsDiffTool;
    private final Editor editor;
    private final GerritUtil gerritUtil;
    private final GerritSettings gerritSettings;
    private final AddCommentActionBuilder addCommentActionBuilder;
    private final Comment fileComment;
    private final ChangeInfo changeInfo;
    private final String revisionId;
    private final RangeHighlighter lineHighlighter;
    private final RangeHighlighter rangeHighlighter;

    public CommentGutterIconRenderer(CommentsDiffTool commentsDiffTool,
                                     Editor editor,
                                     GerritUtil gerritUtil,
                                     GerritSettings gerritSettings,
                                     AddCommentActionBuilder addCommentActionBuilder,
                                     Comment fileComment,
                                     ChangeInfo changeInfo,
                                     String revisionId,
                                     RangeHighlighter lineHighlighter,
                                     RangeHighlighter rangeHighlighter) {
        this.commentsDiffTool = commentsDiffTool;
        this.gerritSettings = gerritSettings;
        this.fileComment = fileComment;
        this.gerritUtil = gerritUtil;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
        this.lineHighlighter = lineHighlighter;
        this.editor = editor;
        this.rangeHighlighter = rangeHighlighter;
        this.addCommentActionBuilder = addCommentActionBuilder;
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

        if (!CommentHelper.equals(fileComment, that.fileComment)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return CommentHelper.hashCode(fileComment);
    }

    @Nullable
    @Override
    public String getTooltipText() {
        return String.format("<strong>%s</strong> (%s)<br/>%s",
                getAuthorName(),
                fileComment.updated != null ? DateFormatUtil.formatPrettyDateTime(fileComment.updated) : "draft",
                TextToHtml.textToHtml(fileComment.message));
    }

    @Nullable
    @Override
    public ActionGroup getPopupMenuActions() {
        return createPopupMenuActionGroup();
    }

    private boolean isNewCommentFromMyself() {
        return fileComment instanceof CommentInfo && (((CommentInfo) fileComment).author == null);
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
        return new DumbAwareAction() {
            @Override
            public void actionPerformed(AnActionEvent e) {
                MouseEvent inputEvent = (MouseEvent) e.getInputEvent();
                ActionManager actionManager = ActionManager.getInstance();
                DefaultActionGroup actionGroup = createPopupMenuActionGroup();
                ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, actionGroup);
                popupMenu.getComponent().show(inputEvent.getComponent(), inputEvent.getX(), inputEvent.getY());
            }
        };
    }

    private DefaultActionGroup createPopupMenuActionGroup() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        if (isNewCommentFromMyself()) {
            AddCommentAction commentAction = addCommentActionBuilder
                    .create(commentsDiffTool, changeInfo, revisionId, editor, fileComment.path, fileComment.side)
                    .withText("Edit")
                    .withIcon(AllIcons.Toolwindows.ToolWindowMessages)
                    .update(fileComment, lineHighlighter, rangeHighlighter)
                    .get();
            actionGroup.add(commentAction);

            RemoveCommentAction removeCommentAction = new RemoveCommentAction(
                    commentsDiffTool, editor, gerritUtil, changeInfo, fileComment, revisionId,
                    lineHighlighter, rangeHighlighter);
            actionGroup.add(removeCommentAction);
        } else {
            AddCommentAction commentAction = addCommentActionBuilder
                    .create(commentsDiffTool, changeInfo, revisionId, editor, fileComment.path, fileComment.side)
                    .withText("Reply")
                    .withIcon(AllIcons.Actions.Back)
                    .reply(fileComment)
                    .get();
            actionGroup.add(commentAction);

            CommentDoneAction commentDoneAction = new CommentDoneAction(
                    editor, commentsDiffTool, gerritUtil, gerritSettings, fileComment, changeInfo, revisionId);
            actionGroup.add(commentDoneAction);
        }
        return actionGroup;
    }

    private String getAuthorName() {
        String name = "Myself";
        if (!isNewCommentFromMyself()) {
            AccountInfo author = ((CommentInfo) fileComment).author;
            if (author != null) {
                name = author.name;
            }
        }
        return name;
    }
}
