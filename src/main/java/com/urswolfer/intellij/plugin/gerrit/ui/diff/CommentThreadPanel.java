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

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.AccountInfo;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.ui.avatar.AvatarIcons;
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Boolean.TRUE;
import static javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES;

/**
 * A thread of comments under its line, as Gerrit's web UI shows it: each comment with its author and when it was
 * written, folded to a line where the thread is resolved, and below them whether it is, with the replies it takes.
 * A draft, the reply being written, takes the place of those: it can be edited or removed. A click on the head of a
 * comment folds or unfolds it.
 */
final class CommentThreadPanel extends JPanel implements DataProvider {
    private static final int AVATAR = 20;
    private static final int GAP = 8;
    private static final int PADDING = 10;
    private static final int MARGIN = 4;
    private static final int ARC = 10;
    private static final String PLACE = "GerritCommentThread";
    private static final int TAB_SIZE = 4;
    private static final Pattern PRE = Pattern.compile("<pre>(.*?)</pre>", Pattern.DOTALL);
    private static final int RETRY_AFTER_MS = 3000;

    /** The actions on the comments, which the gutter icons have too, and what the panel keeps beyond its life. */
    interface Actions {
        boolean canComment();

        /** @param anchor what the form opens underneath */
        AnAction reply(Comment comment, JComponent anchor);

        AnAction resolve(Comment comment, CommentResolveAction.Reply reply);

        AnAction edit(Comment draft, JComponent anchor);

        AnAction remove(Comment draft);

        boolean isFolded(Comment comment);

        void setFolded(Comment comment, boolean folded);
    }

    private final CommentThreads.Thread<?> thread;
    private final Actions actions;
    @Nullable
    private final AvatarIcons avatarIcons;

    /**
     * @param avatarIcons those of the editor, which keep their images while the panels are built anew; null without
     *                    avatars
     */
    CommentThreadPanel(CommentThreads.Thread<?> thread, Actions actions, IntSupplier width,
                       @Nullable AvatarIcons avatarIcons) {
        super(new StackLayout(width, JBUI.scale(6)));
        this.thread = thread;
        this.actions = actions;
        this.avatarIcons = avatarIcons;
        setOpaque(false);
        setBorder(JBUI.Borders.empty(MARGIN + PADDING));
        // a click or a drag between the comments and their links would otherwise go to the editor, and move its caret
        addMouseListener(new MouseAdapter() {
        });
        build();
    }

    /**
     * What copies, cuts, pastes and deletes with the focus in the text of a comment, which is read-only: otherwise the
     * editor around the panel would, in the file. The editor itself the text hides from the keymap in 2020.3, see
     * {@link #body}; newer IDEs hide it from a focused text component themselves.
     *
     * TODO once the minimum IDE has com.intellij.openapi.actionSystem.UiDataProvider (2020.3 has not, 2026.2 has, and
     * DataProvider is obsolete there): set EDITOR, HOST_EDITOR and the other data of the editor null with
     * DataSink.setNull, as collaboration-tools does for its inlays; drop HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, gone
     * by then; and let the links and the heads of the comments take the focus, to be used by the keyboard.
     */
    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (!(focused instanceof JTextComponent) || !SwingUtilities.isDescendingFrom(focused, this)) return null;
        JTextComponent text = (JTextComponent) focused;
        if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
            return new CutProvider() {
                @Override
                public void performCut(@NotNull DataContext dataContext) {
                }

                @Override
                public boolean isCutEnabled(@NotNull DataContext dataContext) {
                    return false;
                }

                @Override
                public boolean isCutVisible(@NotNull DataContext dataContext) {
                    return true;
                }
            };
        }
        if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
            return new DeleteProvider() {
                @Override
                public void deleteElement(@NotNull DataContext dataContext) {
                }

                @Override
                public boolean canDeleteElement(@NotNull DataContext dataContext) {
                    return false;
                }
            };
        }
        if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
            return new PasteProvider() {
                @Override
                public void performPaste(@NotNull DataContext dataContext) {
                }

                @Override
                public boolean isPastePossible(@NotNull DataContext dataContext) {
                    return false;
                }

                @Override
                public boolean isPasteEnabled(@NotNull DataContext dataContext) {
                    return false;
                }
            };
        }
        // in 2020.3 the editor around would copy its own line
        if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
            return new CopyProvider() {
                @Override
                public void performCopy(@NotNull DataContext dataContext) {
                    text.copy();
                }

                @Override
                public boolean isCopyEnabled(@NotNull DataContext dataContext) {
                    return text.getSelectionStart() != text.getSelectionEnd();
                }

                @Override
                public boolean isCopyVisible(@NotNull DataContext dataContext) {
                    return true;
                }
            };
        }
        return null;
    }

    /** What the text of a comment is shifted by from the edges of the editor, scaled. */
    static int contentShift() {
        return JBUI.scale(2 * (MARGIN + PADDING) + AVATAR + GAP);
    }

    static AvatarIcons createAvatarIcons(JComponent repainted) {
        return new AvatarIcons(repainted, AVATAR);
    }

    private void build() {
        removeAll();
        List<Comment> comments = thread.comments();
        List<Icon> avatars = new ArrayList<>();
        for (Comment comment : comments) {
            AccountInfo author = CommentThreads.author(comment);
            avatars.add(avatarIcons != null && AvatarIcons.hasAvatar(author) ? new Avatar(avatarIcons, author) : null);
        }
        // the text of the comments one below the other, a draft's too, which has no avatar
        int shift = avatars.stream().anyMatch(Objects::nonNull) ? AVATAR + GAP : 0; // unscaled, as borders take it
        for (int i = 0; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            boolean folded = actions.isFolded(comment);
            Icon avatar = avatars.get(i);
            JComponent header = header(comment, folded, avatar);
            header.setBorder(JBUI.Borders.empty(i == 0 ? 0 : 4, avatar == null ? shift : 0, 0, 0));
            add(header);
            if (folded) continue;
            add(body(comment, shift));
            if (CommentThreads.isDraft(comment) && actions.canComment()) {
                ActionLink edit = link(GerritBundle.message("diff.edit"));
                ActionLink remove = link(GerritBundle.message("diff.remove"));
                List<ActionLink> draftLinks = Arrays.asList(edit, remove);
                edit.addActionListener(e -> performOnce(actions.edit(comment, edit), edit, draftLinks));
                remove.addActionListener(e -> performOnce(actions.remove(comment), remove, draftLinks));
                add(links(shift, edit, remove));
            }
        }
        add(footer());
    }

    private void toggle(Comment comment) {
        actions.setFolded(comment, !actions.isFolded(comment));
        build();
        revalidate();
        repaint();
    }

    private JComponent header(Comment comment, boolean folded, @Nullable Icon avatar) {
        JBLabel author = plainLabel(CommentThreads.authorName(comment));
        author.setIcon(avatar);
        author.setIconTextGap(JBUI.scale(GAP));
        author.setFont(author.getFont().deriveFont(Font.BOLD));
        // in a panel of its own, which a narrow editor cuts short rather than the date overlapping it
        JPanel who = new JPanel(new BorderLayout(JBUI.scale(GAP), 0));
        who.setOpaque(false);
        if (folded) {
            who.add(author, BorderLayout.WEST);
            JBLabel excerpt = plainLabel(CommentThreads.excerpt(comment.message));
            excerpt.setForeground(UIUtil.getContextHelpForeground());
            who.add(excerpt, BorderLayout.CENTER);
        } else {
            who.add(author, BorderLayout.CENTER);
        }
        JPanel header = new JPanel(new BorderLayout(JBUI.scale(GAP), 0));
        header.setOpaque(false);
        header.add(who, BorderLayout.CENTER);
        JBLabel when;
        if (CommentThreads.isDraft(comment)) {
            when = new JBLabel(GerritBundle.message("diff.thread.draft"));
            when.setForeground(CommentGutterIconRenderer.DRAFT_STRIPE_COLOR);
        } else {
            when = new JBLabel(comment.updated != null ? DateFormatUtil.formatPrettyDateTime(comment.updated) : "");
            when.setForeground(UIUtil.getContextHelpForeground());
        }
        header.add(when, BorderLayout.EAST);
        if (comment.id == null) return header; // what the user folds is kept by id
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // the second click of a double click would take the first one back
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    toggle(comment);
                }
            }
        });
        return header;
    }

    /**
     * Selectable, with its links followed. Hides the editor around it from the keymap, as 2020.3 asks of a component
     * painted over an editor; newer IDEs have dropped the property, and take the editor of the text, see
     * {@link #getData}.
     *
     * TODO once the minimum IDE has com.intellij.util.ui.HTMLEditorKitBuilder (2020.3 has not, 2026.2 has): take its
     * simple() rather than the deprecated UIUtil.getHTMLEditorKit().
     */
    @SuppressWarnings("deprecation")
    private static JComponent body(Comment comment, int shift) {
        // code keeps its indent as no-break spaces, which pasted into a file would not compile: whatever copies the
        // selection, the keymap of the IDE or the text component's own, takes it from here
        JEditorPane text = new JEditorPane() {
            @Nullable
            @Override
            public String getSelectedText() {
                String selected = super.getSelectedText();
                return selected != null ? plain(selected) : null;
            }
        };
        text.setEditorKit(UIUtil.getHTMLEditorKit());
        text.setEditable(false);
        text.setOpaque(false);
        text.putClientProperty(HONOR_DISPLAY_PROPERTIES, TRUE);
        text.putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, TRUE);
        text.setFont(UIUtil.getLabelFont());
        text.setForeground(UIUtil.getLabelForeground());
        text.setBorder(JBUI.Borders.emptyLeft(shift));
        text.setText("<html><body>" + wrapping(TextToHtml.textToHtml(StringUtil.notNullize(comment.message)))
            + "</body></html>");
        text.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
        text.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Nullable
            @Override
            protected Transferable createTransferable(JComponent c) {
                String selected = ((JTextComponent) c).getSelectedText();
                return selected != null ? new StringSelection(plain(selected)) : null;
            }
        });
        return text;
    }

    /** What the thread is, as the draft has it once published, and with no draft the replies it takes. */
    private JComponent footer() {
        Comment last = thread.last();
        boolean unresolved = thread.isUnresolved();
        JBLabel state = new JBLabel(GerritBundle.message(unresolved ? "diff.unresolved" : "diff.resolved"));
        state.setForeground(UIUtil.getContextHelpForeground());
        if (thread.hasDraft() || !actions.canComment()) {
            return links(0, state);
        }
        List<JComponent> row = new ArrayList<>(); // the state, then the links, which a click on one disables
        row.add(state);
        List<ActionLink> links = new ArrayList<>();
        ActionLink reply = link(GerritBundle.message("diff.reply"));
        reply.addActionListener(e -> performOnce(actions.reply(last, reply), reply, links));
        links.add(reply);
        if (CommentResolveAction.isOffered(thread)) { // the one rule, which the gutter icon goes by too
            for (CommentResolveAction.Reply kind : CommentResolveAction.Reply.values()) {
                ActionLink resolve = link(kind.text());
                resolve.addActionListener(e -> performOnce(actions.resolve(last, kind), resolve, links));
                links.add(resolve);
            }
        }
        row.addAll(links);
        return links(0, row.toArray(new JComponent[0]));
    }

    /** Not focusable, as nothing in the panel but the text of a comment, see {@link #getData}. */
    private static ActionLink link(String text) {
        ActionLink link = new ActionLink(text);
        link.setFocusable(false);
        return link;
    }

    /**
     * Through the action system, as from the menu of a gutter icon, which also gives the actions the read access of
     * the event dispatch thread which newer IDEs no longer grant a listener. A link is clicked or pressed by a key.
     *
     * TODO once the minimum IDE has ActionManager.tryToExecute with a nullable input event (2020.3 has not, 2026.2
     * has): pass the current event only where it is an input event, null otherwise, and drop the click made up here.
     */
    private static void perform(AnAction action, JComponent link) {
        AWTEvent event = EventQueue.getCurrentEvent();
        InputEvent input = event instanceof InputEvent ? (InputEvent) event
            : new MouseEvent(link, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false);
        ActionManager.getInstance().tryToExecute(action, input, link, PLACE, true);
    }

    /**
     * For a request to Gerrit, or a form, which a second click would send again or open twice. The panel is built anew
     * once Gerrit answered; a request which failed says so, and can be sent again after a while.
     *
     * @param links which a click on one disables, as each starts a reply or changes the draft
     */
    private static void performOnce(AnAction action, ActionLink link, List<ActionLink> links) {
        for (ActionLink other : links) {
            other.setAutoHideOnDisable(false);
            other.setEnabled(false);
        }
        Timer enable = new Timer(RETRY_AFTER_MS, e -> links.forEach(other -> other.setEnabled(true)));
        enable.setRepeats(false);
        enable.start();
        perform(action, link);
    }

    /** @param shift unscaled */
    private static JComponent links(int shift, JComponent... components) {
        // on one line, which a flow layout would break where the panel is measured as one line high
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.emptyLeft(shift));
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                row.add(Box.createHorizontalStrut(JBUI.scale(12)));
            }
            row.add(components[i]);
        }
        return row;
    }

    /**
     * Code, which the text of a comment has preformatted, in lines which wrap: Swing never wraps a pre, and the card
     * would cut it off. Spaces stay where they indent or align, a single one between words breaks the line.
     */
    static String wrapping(String html) {
        Matcher pre = PRE.matcher(html);
        StringBuffer out = new StringBuffer();
        while (pre.find()) {
            // a block for each line, as a line break copies as a space
            StringBuilder lines = new StringBuilder();
            String[] split = pre.group(1).split("<br />", -1);
            int count = split.length > 1 && split[split.length - 1].isEmpty() ? split.length - 1 : split.length;
            for (int i = 0; i < count; i++) {
                String line = keepSpaces(split[i]);
                lines.append("<div><tt>").append(line.isEmpty() ? "&nbsp;" : line).append("</tt></div>");
            }
            pre.appendReplacement(out, Matcher.quoteReplacement(lines.toString()));
        }
        pre.appendTail(out);
        return out.toString();
    }

    static String plain(String copied) {
        return copied.replace('\u00A0', ' ');
    }

    /** A space at the start of a line, or after another one, and a tab, as ones which do not break. */
    private static String keepSpaces(String line) {
        StringBuilder kept = new StringBuilder();
        boolean afterSpace = true;
        for (char c : line.toCharArray()) {
            if (c == '\t') {
                kept.append(StringUtil.repeat("&nbsp;", TAB_SIZE));
                afterSpace = true;
            } else if (c == ' ' && afterSpace) {
                kept.append("&nbsp;");
            } else {
                kept.append(c);
                afterSpace = c == ' ';
            }
        }
        return kept.toString();
    }

    /** For text from Gerrit, which a label would render as HTML if it started like it. */
    static JBLabel plainLabel(String text) {
        JBLabel label = new JBLabel();
        label.putClientProperty("html.disable", TRUE);
        label.setText(text);
        return label;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int margin = JBUI.scale(MARGIN);
            int arc = JBUI.scale(ARC);
            RoundRectangle2D card = new RoundRectangle2D.Float(margin, margin,
                getWidth() - 2 * margin - 1, getHeight() - 2 * margin - 1, arc, arc);
            // the colours of the UI rather than of the editor around it, which may be dark under a light theme or light
            // under a dark one: the text, the labels and the links have those of the UI
            g2.setColor(UIUtil.getPanelBackground());
            g2.fill(card);
            g2.setColor(JBColor.border());
            g2.draw(card);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Looked up on each paint, as the change list does, which asks again for an image that failed to load before.
     */
    private static final class Avatar implements Icon {
        private final AvatarIcons avatarIcons;
        private final AccountInfo account;

        Avatar(AvatarIcons avatarIcons, AccountInfo account) {
            this.avatarIcons = avatarIcons;
            this.account = account;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Icon icon = avatarIcons.getIcon(account);
            if (icon != null) {
                icon.paintIcon(c, g, x, y);
            }
        }

        @Override
        public int getIconWidth() {
            return JBUI.scale(AVATAR);
        }

        @Override
        public int getIconHeight() {
            return JBUI.scale(AVATAR);
        }
    }

    /**
     * The components one below the other, as wide as the panel. Their heights are asked for at the width the panel
     * will have, which wrapped text depends on: before it was laid out, a text component tells the height of one line.
     */
    private static final class StackLayout implements LayoutManager {
        private final IntSupplier width;
        private final int gap;

        StackLayout(IntSupplier width, int gap) {
            this.width = width;
            this.gap = gap;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int panelWidth = width.getAsInt();
            Insets insets = parent.getInsets();
            int inner = panelWidth - insets.left - insets.right;
            int height = insets.top + insets.bottom;
            int count = 0;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) continue;
                height += height(child, inner);
                count++;
            }
            return new Dimension(panelWidth, height + gap * Math.max(count - 1, 0));
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int inner = parent.getWidth() - insets.left - insets.right;
            int y = insets.top;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) continue;
                int height = height(child, inner);
                child.setBounds(insets.left, y, inner, height);
                y += height + gap;
            }
        }

        /** Of a text component, through its views rather than its size, which a layout would redo. */
        private static int height(Component child, int width) {
            if (!(child instanceof JEditorPane)) return child.getPreferredSize().height;
            JEditorPane text = (JEditorPane) child;
            Insets insets = text.getInsets();
            View root = text.getUI().getRootView(text);
            root.setSize(Math.max(width - insets.left - insets.right, 1), Integer.MAX_VALUE);
            return (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }
    }
}
