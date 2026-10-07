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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.gerrit.client.rest.http.HttpStatusException;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.util.List;

/**
 * The draft comments a review is about to publish, each of which can be edited or discarded before.
 */
public class DraftCommentsPanel extends JPanel {
    private final Project project;
    private final int changeNr;
    private final String revision;
    private final JBLabel titleLabel = new JBLabel();
    private final RowsPanel rowsPanel = new RowsPanel();
    private boolean changed;

    public DraftCommentsPanel(Project project, int changeNr, String revision, List<DraftComment> drafts) {
        super(new BorderLayout(0, JBUI.scale(4)));
        this.project = project;
        this.changeNr = changeNr;
        this.revision = revision;

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        add(titleLabel, BorderLayout.NORTH);
        for (DraftComment draft : drafts) {
            rowsPanel.add(new Row(draft));
        }
        updateTitle();

        JBScrollPane scrollPane = new JBScrollPane(rowsPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(JBUI.size(600, 250));
        add(scrollPane, BorderLayout.CENTER);
        setBorder(JBUI.Borders.emptyTop(8));
    }

    /**
     * Whether a draft was edited or discarded, which the change list only shows once it is reloaded.
     */
    public boolean isChanged() {
        return changed;
    }

    private void updateTitle() {
        titleLabel.setText(GerritBundle.message("review.drafts.title", rowsPanel.getComponentCount()));
    }

    private void discarded(Row row) {
        changed = true;
        rowsPanel.remove(row);
        if (rowsPanel.getComponentCount() == 0) {
            setVisible(false);
            return;
        }
        updateTitle();
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    static String locationOf(DraftComment draft) {
        String path = draft.getPath();
        String file;
        switch (path) {
            case DraftComment.PATCHSET_LEVEL:
                return GerritBundle.message("review.drafts.patchSet");
            case DraftComment.COMMIT_MSG:
                file = GerritBundle.message("review.drafts.commitMessage");
                break;
            case DraftComment.MERGE_LIST:
                file = GerritBundle.message("review.drafts.mergeList");
                break;
            default:
                file = path;
        }
        CommentInfo comment = draft.getComment();
        String location = comment.line != null ? file + ":" + comment.line : file;
        return comment.side == Side.PARENT ? GerritBundle.message("review.drafts.base", location) : location;
    }

    private class Row extends JPanel {
        private DraftComment draft;
        private final JTextArea messageArea = new JTextArea();

        Row(DraftComment draft) {
            super(new BorderLayout(JBUI.scale(8), JBUI.scale(2)));
            this.draft = draft;
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(4, 4, 6, 4)));

            add(new JBLabel(locationOf(draft)), BorderLayout.NORTH);

            messageArea.setEditable(false);
            // else setting the text scrolls the list to this row, so that it opens scrolled to the last one
            ((DefaultCaret) messageArea.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            messageArea.setLineWrap(true);
            messageArea.setWrapStyleWord(true);
            messageArea.setOpaque(false);
            messageArea.setFont(UIUtil.getLabelFont());
            messageArea.setBorder(JBUI.Borders.emptyLeft(12));
            messageArea.setText(draft.getComment().message);
            add(messageArea, BorderLayout.CENTER);

            JButton editButton = new JButton(GerritBundle.message("review.drafts.edit"));
            editButton.addActionListener(e -> edit());
            JButton discardButton = new JButton(GerritBundle.message("review.drafts.discard"));
            discardButton.addActionListener(e -> discard());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0));
            buttons.setOpaque(false);
            buttons.add(editButton);
            buttons.add(discardButton);
            add(buttons, BorderLayout.EAST);
        }

        private void edit() {
            EditDialog dialog = new EditDialog(this, locationOf(draft), draft.getComment().message);
            // saving the same text would still move the draft's date in Gerrit
            if (!dialog.showAndGet() || dialog.getMessage().equals(draft.getComment().message)) {
                return;
            }
            try {
                CommentInfo updated = GerritUtil.getInstance().saveDraftCommentWithModalProgress(
                    changeNr, revision, draft.toDraftInput(dialog.getMessage()), project);
                changed = true;
                draft = draft.withComment(updated);
                messageArea.setText(updated.message);
                rowsPanel.revalidate();
            } catch (RuntimeException e) {
                showError("review.drafts.saveFailed", e);
            }
        }

        private void discard() {
            int answer = Messages.showOkCancelDialog(this,
                GerritBundle.message("review.drafts.discard.confirm", locationOf(draft)),
                GerritBundle.message("review.drafts.discard.title"),
                GerritBundle.message("review.drafts.discard"), Messages.getCancelButton(), Messages.getQuestionIcon());
            if (answer != Messages.OK) {
                return;
            }
            try {
                GerritUtil.getInstance().deleteDraftCommentWithModalProgress(
                    changeNr, revision, draft.getComment().id, project);
                discarded(this);
            } catch (RuntimeException e) {
                // published or deleted elsewhere since the dialog opened: either way, the review will not publish it
                if (statusCode(e) == 404) {
                    discarded(this);
                } else {
                    showError("review.drafts.discardFailed", e);
                }
            }
        }

        private void showError(String titleKey, RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Messages.showErrorDialog(this, GerritUtil.getInstance().getErrorTextFromException(cause),
                GerritBundle.message(titleKey));
        }
    }

    private static int statusCode(RuntimeException e) {
        return e.getCause() instanceof HttpStatusException ? ((HttpStatusException) e.getCause()).getStatusCode() : 0;
    }

    /**
     * Tracks the width of the viewport, so that the messages wrap rather than scroll sideways.
     */
    private static class RowsPanel extends JPanel implements Scrollable {
        RowsPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return JBUI.scale(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private class EditDialog extends DialogWrapper {
        private final SafeHtmlTextEditor editor;

        EditDialog(Component parent, String location, String message) {
            super(parent, true);
            editor = new SafeHtmlTextEditor(project);
            editor.getMessageField().setText(message);
            setTitle(GerritBundle.message("review.drafts.edit.title", location));
            setOKButtonText(GerritBundle.message("review.drafts.edit.save"));
            init();
            initValidation();
        }

        @Override
        protected JComponent createCenterPanel() {
            return editor;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return editor.getMessageField();
        }

        /**
         * Gerrit deletes a draft which is saved without a message, which Discard does more plainly.
         */
        @Nullable
        @Override
        protected ValidationInfo doValidate() {
            return getMessage().isEmpty()
                ? new ValidationInfo(GerritBundle.message("review.drafts.edit.empty"), editor.getMessageField())
                : null;
        }

        String getMessage() {
            return editor.getMessageField().getText().trim();
        }
    }
}
