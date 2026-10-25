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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GerritPushExtensionPanelTest {

    @Test
    public void testSettingDecidesUntilTheBoxIsClicked() {
        Assert.assertFalse(panel(false, "default-off").getPushToGerritCheckBox().isSelected());
        Assert.assertTrue(panel(true, "default-on").getPushToGerritCheckBox().isSelected());
    }

    @Test
    public void testChoiceIsKeptForTheNextDialogOfTheProject() {
        panel(false, "ticked").getPushToGerritCheckBox().doClick();
        Assert.assertTrue(panel(false, "ticked").getPushToGerritCheckBox().isSelected());

        panel(true, "unticked").getPushToGerritCheckBox().doClick();
        Assert.assertFalse(panel(true, "unticked").getPushToGerritCheckBox().isSelected());
    }

    @Test
    public void testChoiceStaysInItsProject() {
        panel(false, "gerrit-project").getPushToGerritCheckBox().doClick();

        Assert.assertFalse(panel(false, "plain-git-project").getPushToGerritCheckBox().isSelected());
    }

    @Test
    public void testNewDefaultWinsOverClickedBoxes() {
        boolean before = GerritPushOptionsPanel.pushToGerritByDefault;
        try {
            GerritPushOptionsPanel.setPushToGerritByDefault(false);
            // ticked and unticked again: remembered as off, which is what the new default must not be held to
            panel(false, "clicked-before").getPushToGerritCheckBox().doClick();
            panel(false, "clicked-before").getPushToGerritCheckBox().doClick();

            GerritPushOptionsPanel.setPushToGerritByDefault(false); // unchanged: nothing is forgotten
            Assert.assertFalse(panel(false, "clicked-before").getPushToGerritCheckBox().isSelected());

            GerritPushOptionsPanel.setPushToGerritByDefault(true);
            Assert.assertTrue(panel(true, "clicked-before").getPushToGerritCheckBox().isSelected());
        } finally {
            GerritPushOptionsPanel.setPushToGerritByDefault(before);
        }
    }

    @Test
    public void testEveryOptionHasItsOwnMnemonic() {
        assertMnemonics(panel(false, "mnemonics"));
    }

    /** What the IDE hands the panels: the texts of GerritBundle, whose marker 2020.3 has already replaced. */
    @Test
    public void testMnemonicsOfTheTextsOfTheBundle() {
        PushMessages.setProvider(GerritBundle::message);
        try {
            assertMnemonics(panel(false, "bundle-mnemonics"));
        } finally {
            PushMessages.setProvider(null);
        }
    }

    private static void assertMnemonics(GerritPushExtensionPanel panel) {
        List<Component> components = new ArrayList<>();
        collect(panel, components);
        Set<Integer> seen = new HashSet<>();
        int options = 0;
        for (Component component : components) {
            int mnemonic = 0;
            if (component instanceof JCheckBox) {
                mnemonic = ((JCheckBox) component).getMnemonic();
            } else if (component instanceof JLabel && ((JLabel) component).getText() != null
                    && !((JLabel) component).getText().isEmpty()) {
                mnemonic = ((JLabel) component).getDisplayedMnemonic();
            } else {
                continue;
            }
            options++;
            String text = component instanceof JCheckBox ? ((JCheckBox) component).getText() : ((JLabel) component).getText();
            Assert.assertFalse(text.contains("&") || text.contains("\u001B"), "marker left in: " + text);
            Assert.assertNotEquals(mnemonic, 0, "no mnemonic: " + component);
            Assert.assertTrue(seen.add(mnemonic), "mnemonic used twice: " + (char) mnemonic);
            if (component instanceof JLabel) {
                Assert.assertNotNull(((JLabel) component).getLabelFor(), "no field for " + component);
            }
        }
        Assert.assertEquals(options, 14); // 8 check boxes and 6 labelled fields
    }

    private static void collect(Container container, List<Component> into) {
        for (Component child : container.getComponents()) {
            into.add(child);
            if (child instanceof Container) {
                collect((Container) child, into);
            }
        }
    }

    private static GerritPushExtensionPanel panel(boolean pushToGerritByDefault, String projectKey) {
        return new GerritPushExtensionPanel(pushToGerritByDefault, projectKey, null);
    }
}
