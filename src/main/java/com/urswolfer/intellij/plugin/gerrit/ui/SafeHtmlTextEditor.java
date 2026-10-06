/*
 * Copyright 2013-2014 Urs Wolfer
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AdditionalPageAtBottomEditorCustomization;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldProvider;
import com.intellij.ui.SoftWrapsEditorCustomization;
import com.intellij.ui.TabbedPaneImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Urs Wolfer
 */
public class SafeHtmlTextEditor extends JPanel {
    private EditorTextField messageField;

    public SafeHtmlTextEditor(Project project) {
        super(new BorderLayout());
        TabbedPaneImpl tabbedPane = new TabbedPaneImpl(SwingConstants.TOP);
        tabbedPane.setKeyboardNavigation(TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS);

        messageField = createMessageField(project);
        messageField.setBorder(BorderFactory.createEmptyBorder());
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.add(messageField, BorderLayout.CENTER);
        JLabel markdownLinkLabel = new JLabel(
            "<html>Write your comment here. " +
            "You can use a <a href=\"\"> simple markdown-like syntax</a>.</html>");
        markdownLinkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        markdownLinkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                BrowserUtil.browse("https://gerrit-review.googlesource.com/Documentation/user-review-ui.html#summary-comment");
            }
        });
        messagePanel.add(markdownLinkLabel, BorderLayout.SOUTH);
        tabbedPane.addTab("Write", AllIcons.Actions.Edit, messagePanel);

        final JEditorPane previewEditorPane = new JEditorPane(UIUtil.HTML_MIME, "");
        previewEditorPane.setEditable(false);
        tabbedPane.addTab("Preview", AllIcons.Actions.Preview, previewEditorPane);

        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (((TabbedPaneImpl) e.getSource()).getSelectedComponent() == previewEditorPane) {
                    String content = String.format("<html><head>%s</head><body>%s</body></html>",
                            UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()),
                            TextToHtml.textToHtml(messageField.getText()));
                    previewEditorPane.setText(content);
                }
            }
        });

        add(tabbedPane, SwingConstants.CENTER);
    }

    /**
     * Like the commit message field, but without its right margin and inspections: the commit settings wrapped a
     * comment with hard line breaks while typing, which Gerrit shows as they are, and warned about a missing blank line
     * after the first one.
     */
    private static EditorTextField createMessageField(Project project) {
        List<EditorCustomization> features = new ArrayList<>();
        features.add(SoftWrapsEditorCustomization.ENABLED);
        features.add(AdditionalPageAtBottomEditorCustomization.DISABLED);
        ContainerUtil.addIfNotNull(features, SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization());
        // as the commit message field does: the field takes the background of the dialog, so with an editor scheme
        // of the other darkness than the UI theme, the text would be dark on dark or light on light
        features.add(editor -> {
            EditorColorsManager colorsManager = EditorColorsManager.getInstance();
            boolean sameDarkness = ColorUtil.isDark(UIUtil.getPanelBackground()) == colorsManager.isDarkEditor();
            editor.setBackgroundColor(null);
            editor.setColorsScheme(editor.createBoundColorSchemeDelegate(
                sameDarkness ? colorsManager.getGlobalScheme() : colorsManager.getSchemeForCurrentUITheme()));
        });
        EditorTextField field = EditorTextFieldProvider.getInstance()
            .getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, features);
        field.setFontInheritedFromLAF(false);
        return field;
    }

    public EditorTextField getMessageField() {
        return messageField;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(600, 400);
    }
}
