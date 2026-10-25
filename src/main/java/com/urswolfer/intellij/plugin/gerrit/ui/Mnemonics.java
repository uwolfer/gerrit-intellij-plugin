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

import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.event.ActionEvent;

/**
 * Gives a control the Alt mnemonic its bundle text marks with an ampersand, as the platform's own forms do. Not for
 * the push dialog panels, which run in the class loader of the Git plugin: see {@code GerritPushExtensionPanel}.
 */
public final class Mnemonics {
    private static final char MARKER = '&';
    private static final String PLACEHOLDER = "\u0001";

    private Mnemonics() {}

    // TODO once the minimum IDE keeps the ampersand in bundle texts (2020.3 has not, 2026.2 has): drop normalize, and
    //  mnemonicText in GerritPushExtensionPanel with it. 2020.3 hands out the text with the marker already turned into
    //  the platform's own character.
    private static String normalize(String text) {
        return text.replace(UIUtil.MNEMONIC, MARKER);
    }

    public static <T extends AbstractButton> T button(T button, String text) {
        button.setText(normalize(text));
        DialogUtil.registerMnemonic(button, MARKER);
        return button;
    }

    /**
     * The text of a marked bundle text as shown: without the marker, and without the letter an East Asian text adds
     * in parentheses, which a text shown as it is has no use for.
     */
    public static String plain(String text) {
        return UIUtil.removeMnemonic(normalize(text)).replaceAll("\\([A-Za-z]\\)\\s*$", "");
    }

    /**
     * For a text with a value of the user's in it, such as a branch name: only the text of the bundle is searched for
     * the marker, as an ampersand in the value would otherwise be read as one.
     */
    public static <T extends AbstractButton> T button(T button, String key, String value) {
        button(button, GerritBundle.message(key, PLACEHOLDER));
        int at = button.getText().indexOf(PLACEHOLDER);
        int index = button.getDisplayedMnemonicIndex();
        button.setText(button.getText().replace(PLACEHOLDER, value));
        if (index >= 0) { // setText has put the underline on the first match of the letter
            button.setDisplayedMnemonicIndex(at >= 0 && index > at ? index + value.length() - PLACEHOLDER.length() : index);
        }
        return button;
    }

    /** Binds the label to its field, so that the mnemonic moves the focus there and screen readers name the field. */
    public static <T extends JLabel> T label(T label, String text, JComponent field) {
        label.setText(normalize(text));
        DialogUtil.registerMnemonic(label, field, MARKER);
        // registerMnemonic leaves the label unbound on 2026.2, and Alt+letter then does not reach the field
        label.setLabelFor(field);
        return label;
    }

    /**
     * For a text field the label's own focus move does not reach. Swing's mnemonic action hands the focus to the
     * first child of the field in the focus order, and a text field with an extension, such as the browse icon of a
     * directory field, has one: the icon takes the focus, not the text. The action is Swing's "press", and
     * {@code smoke.sh} is what notices a platform which names it otherwise.
     */
    public static <T extends JLabel> T textFieldLabel(T label, String text, JComponent textField) {
        label(label, text, textField);
        label.getActionMap().put("press", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                textField.requestFocusInWindow();
            }
        });
        return label;
    }
}
