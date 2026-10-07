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

import org.testng.Assert;
import org.testng.annotations.Test;

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

    private static GerritPushExtensionPanel panel(boolean pushToGerritByDefault, String projectKey) {
        return new GerritPushExtensionPanel(pushToGerritByDefault, projectKey, null);
    }
}
