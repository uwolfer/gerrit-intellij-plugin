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

import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.event.KeyEvent;

public class MnemonicsTest {

    @Test
    public void testButtonTakesItsMnemonicFromTheMarker() {
        JCheckBox box = Mnemonics.button(new JCheckBox(), "Show a&vatars");

        Assert.assertEquals(box.getText(), "Show avatars");
        Assert.assertEquals(box.getMnemonic(), KeyEvent.VK_V);
        Assert.assertEquals(box.getDisplayedMnemonicIndex(), 6);
    }

    @Test
    public void testEastAsianMarkerInParentheses() {
        JCheckBox box = Mnemonics.button(new JCheckBox(), "アバターを表示(&V)");

        Assert.assertEquals(box.getText(), "アバターを表示(V)");
        Assert.assertEquals(box.getMnemonic(), KeyEvent.VK_V);
        Assert.assertEquals(box.getDisplayedMnemonicIndex(), 8);
    }

    @Test
    public void testBundleTextIsReadWhateverMarkerTheIDEHandsOut() {
        JButton button = Mnemonics.button(new JButton(), GerritBundle.message("diff.save"));

        Assert.assertEquals(button.getText(), "Save");
        Assert.assertEquals(button.getMnemonic(), KeyEvent.VK_S);
        Assert.assertEquals(UIUtil.removeMnemonic(GerritBundle.message("diff.resolved")), "Resolved");
    }

    @Test
    public void testPlainTextHasNeitherMarkerNorEastAsianLetter() {
        Assert.assertEquals(Mnemonics.plain("&Resolved"), "Resolved");
        Assert.assertEquals(Mnemonics.plain("解決済み(&R)"), "解決済み");
        Assert.assertEquals(Mnemonics.plain(GerritBundle.message("diff.resolved")), "Resolved");
    }

    @Test
    public void testAnAmpersandOfAValueIsNoMarker() {
        JCheckBox box = Mnemonics.button(new JCheckBox(), "rebase.onBranch", "a&xb");

        Assert.assertEquals(box.getText(), "Rebase on top of the a&xb branch");
        Assert.assertEquals(box.getMnemonic(), KeyEvent.VK_T);
        Assert.assertEquals(box.getDisplayedMnemonicIndex(), 10);
    }

    @Test
    public void testLabelIsBoundToItsField() {
        JTextField field = new JTextField();
        JLabel label = Mnemonics.label(new JLabel(), "&Topic:", field);

        Assert.assertEquals(label.getText(), "Topic:");
        Assert.assertEquals(label.getDisplayedMnemonic(), KeyEvent.VK_T);
        Assert.assertSame(label.getLabelFor(), field);
    }
}
