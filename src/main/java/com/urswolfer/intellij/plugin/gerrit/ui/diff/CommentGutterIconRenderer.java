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
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.text.DateFormatUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritChangeDetailsPanel;
import com.urswolfer.intellij.plugin.gerrit.util.CommentHelper;
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Urs Wolfer
 */
public class CommentGutterIconRenderer extends GutterIconRenderer {
    static final Color PUBLISHED_STRIPE_COLOR = new JBColor(new Color(0x3574F0), new Color(0x548AF7));
    static final Color DRAFT_STRIPE_COLOR = new JBColor(new Color(0xE08A00), new Color(0xD6AE58));
    private static final int STRIPE_TOOLTIP_LENGTH = 80;

    private final DiffComments comments;
    private final Editor editor;
    private final AddCommentActionBuilder addCommentActionBuilder;
    private final Comment fileComment;
    private final CommentSide side;
    private final RangeHighlighter rangeHighlighter;

    CommentGutterIconRenderer(DiffComments comments,
                              Editor editor,
                              AddCommentActionBuilder addCommentActionBuilder,
                              Comment fileComment,
                              CommentSide side,
                              RangeHighlighter rangeHighlighter) {
        this.comments = comments;
        this.editor = editor;
        this.addCommentActionBuilder = addCommentActionBuilder;
        this.fileComment = fileComment;
        this.side = side;
        this.rangeHighlighter = rangeHighlighter;
    }

    Comment getComment() {
        return fileComment;
    }

    RangeHighlighter getRangeHighlighter() {
        return rangeHighlighter;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        if (isDraft(fileComment)) {
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
                getAuthorName(fileComment),
                fileComment.updated != null ? DateFormatUtil.formatPrettyDateTime(fileComment.updated) : "draft",
                TextToHtml.textToHtml(fileComment.message));
    }

    @Nullable
    @Override
    public ActionGroup getPopupMenuActions() {
        return createPopupMenuActionGroup();
    }

    private static boolean isDraft(Comment comment) {
        return comment instanceof CommentInfo && (((CommentInfo) comment).author == null);
    }

    static Color getErrorStripeColor(Comment comment) {
        return isDraft(comment) ? DRAFT_STRIPE_COLOR : PUBLISHED_STRIPE_COLOR;
    }

    static String getErrorStripeTooltip(Comment comment) {
        String excerpt = StringUtil.shortenTextWithEllipsis(
                StringUtil.notNullize(comment.message).trim().replaceAll("\\s+", " "), STRIPE_TOOLTIP_LENGTH, 0);
        return String.format("<b>%s</b>%s: %s",
                getAuthorName(comment),
                isDraft(comment) ? " (draft)" : "",
                StringUtil.escapeXmlEntities(excerpt));
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
        if (isDraft(fileComment)) {
            AddCommentAction commentAction = addCommentActionBuilder
                    .create(comments, editor)
                    .withText("Edit")
                    .withIcon(AllIcons.Toolwindows.ToolWindowMessages)
                    .update(fileComment, side)
                    .get();
            actionGroup.add(commentAction);

            actionGroup.add(new RemoveCommentAction(comments, fileComment, side));
        } else {
            AddCommentAction commentAction = addCommentActionBuilder
                    .create(comments, editor)
                    .withText("Reply")
                    .withIcon(AllIcons.Actions.Back)
                    .reply(fileComment, side)
                    .get();
            actionGroup.add(commentAction);

            actionGroup.add(new CommentDoneAction(comments, fileComment, side));
        }
        return actionGroup;
    }

    private static String getAuthorName(Comment comment) {
        AccountInfo author = comment instanceof CommentInfo ? ((CommentInfo) comment).author : null;
        return author == null ? "Myself" : GerritChangeDetailsPanel.accountName(author);
    }
}
