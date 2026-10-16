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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.ui.avatar.AvatarIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Components under lines of an editor, as wide as the text area shows, up to a width which still reads well.
 *
 * <p>
 * The platform sizes an embedded component as its preferred size says, and the height of wrapped text depends on its
 * width. So each component sits in a scroll pane of its own, which lays it out whenever it is revalidated, and the
 * components are told when the width of the editor changes. The GitHub plugin of 2020.3 did the same. A component which
 * changed its height tells the platform: 2020.3 follows the resizing of the component it was given, newer IDEs the
 * layout of their own panel around it.
 *
 * <p>
 * {@link EditorEmbeddedComponentManager} is neither internal nor experimental, and its methods used here are the same
 * from 2020.3 to 2026.2; the platform lists it as API it has not reviewed yet.
 *
 * TODO once the minimum IDE has com.intellij.openapi.editor.ComponentInlayKt.addComponentInlay as public API (2020.3
 * has not, 2026.2 has it experimental): add each component with ComponentInlayAlignment.FIT_VIEWPORT_WIDTH, which
 * follows the width of the editor, and drop this class.
 */
final class CommentInlays implements Disposable {
    private static final Key<CommentInlays> KEY = Key.create("gerrit.CommentInlays");
    // the content of a comment, in characters of the UI font, as the review in the editor of collaboration-tools has
    private static final int TEXT_EMS = 42;

    private final EditorEx editor;
    private final List<Wrapper> wrappers = new ArrayList<>();
    private int width;
    @Nullable
    private AvatarIcons avatarIcons;

    private CommentInlays(EditorEx editor) {
        this.editor = editor;
        JViewport viewport = editor.getScrollPane().getViewport();
        ComponentAdapter widthWatcher = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateWidth();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                updateWidth();
            }
        };
        viewport.addComponentListener(widthWatcher);
        Disposer.register(this, () -> viewport.removeComponentListener(widthWatcher));
        EditorUtil.disposeWithEditor(editor, this);
    }

    static CommentInlays of(EditorEx editor) {
        CommentInlays inlays = editor.getUserData(KEY);
        if (inlays == null) {
            inlays = new CommentInlays(editor);
            editor.putUserData(KEY, inlays);
        }
        return inlays;
    }

    /** Once the editor shows no more comments, for as long as it stays open. */
    static void release(EditorEx editor) {
        CommentInlays inlays = editor.getUserData(KEY);
        if (inlays != null) {
            Disposer.dispose(inlays);
        }
    }

    /** On the event dispatch thread; the inlay goes with the text at the end of the line. */
    @Nullable
    Inlay<?> insertAfter(int line, JComponent component, int priority) {
        return insert(editor.getDocument().getLineEndOffset(line), false, priority, component);
    }

    /** Above the first line, as Gerrit shows the comments on a file. */
    @Nullable
    Inlay<?> insertAtTop(JComponent component, int priority) {
        return insert(0, true, priority, component);
    }

    @Nullable
    private Inlay<?> insert(int offset, boolean above, int priority, JComponent component) {
        if (editor.isDisposed()) return null;
        Wrapper wrapper = new Wrapper(component);
        Inlay<?> inlay = EditorEmbeddedComponentManager.getInstance().addComponent(editor, wrapper,
            new EditorEmbeddedComponentManager.Properties(EditorEmbeddedComponentManager.ResizePolicy.none(), null,
                !above, above, priority, offset));
        if (inlay != null) {
            wrappers.add(wrapper);
            Disposer.register(inlay, () -> wrappers.remove(wrapper));
        }
        return inlay;
    }

    /** Shared by the components of the editor, so that those built anew show the images loaded already. */
    @Nullable
    AvatarIcons avatarIcons() {
        if (!GerritSettings.getInstance().getShowAvatars()) return null;
        if (avatarIcons == null) {
            avatarIcons = CommentThreadPanel.createAvatarIcons(editor.getContentComponent());
        }
        return avatarIcons;
    }

    /** What a component is wide, the margin to the scroll bar left out. */
    int width() {
        JScrollPane scrollPane = editor.getScrollPane();
        int visible = scrollPane.getViewport().getWidth() - scrollPane.getVerticalScrollBar().getWidth()
            - JBUI.scale(8);
        int max = UIUtil.getLabelFont().getSize() * TEXT_EMS + CommentThreadPanel.contentShift();
        // before the editor is laid out, as the wide one it most likely becomes
        return visible <= 0 ? max : Math.min(visible, max);
    }

    private void updateWidth() {
        int newWidth = width();
        if (newWidth == width) return;
        width = newWidth;
        for (Wrapper wrapper : new ArrayList<>(wrappers)) {
            // invalidated from the inside, as a valid container keeps the preferred size it measured at the old width
            wrapper.content.invalidate();
            wrapper.dispatchEvent(new ComponentEvent(wrapper, ComponentEvent.COMPONENT_RESIZED));
            wrapper.revalidate();
        }
    }

    @Override
    public void dispose() {
        editor.putUserData(KEY, null);
    }

    private final class Wrapper extends JBScrollPane {
        final JComponent content;
        final JComponent view;

        Wrapper(JComponent content) {
            this(content, new View(content));
        }

        private Wrapper(JComponent content, View view) {
            super(view);
            this.content = content;
            this.view = view;
            setOpaque(false);
            getViewport().setOpaque(false);
            setBorder(JBUI.Borders.empty());
            setViewportBorder(JBUI.Borders.empty());
            // a scroll pane for its layout only, which takes no wheel: that scrolls the editor
            setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            setWheelScrollingEnabled(false);
            view.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    // 2020.3 follows the size of the component it was given, this one
                    dispatchEvent(new ComponentEvent(Wrapper.this, ComponentEvent.COMPONENT_RESIZED));
                    // newer IDEs size their panel around it, and the inlay with it, as they lay it out
                    Container panel = getParent();
                    if (panel != null) {
                        panel.revalidate();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width(), view.getPreferredSize().height);
        }
    }

    /**
     * Scrollable, as the viewport would otherwise keep a component which got shorter as tall as it is, and so the
     * component would not tell that it got shorter.
     */
    private static final class View extends JPanel implements Scrollable {
        View(JComponent component) {
            super(new BorderLayout());
            setOpaque(false);
            add(component, BorderLayout.CENTER);
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
}
