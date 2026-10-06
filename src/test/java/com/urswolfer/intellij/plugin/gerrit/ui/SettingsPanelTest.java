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

import org.junit.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.swing.*;

public class SettingsPanelTest {

    @DataProvider
    public Object[][] urls() {
        return new Object[][]{
            {"", ""},
            {"review.example.org", "https://review.example.org"},
            {"http://review.example.org/", "http://review.example.org"},
            {"  review.example.org/ ", "https://review.example.org"},
        };
    }

    @Test(dataProvider = "urls")
    public void fixUrl(String input, String expected) {
        JTextField textField = new JTextField(input);
        SettingsPanel.fixUrl(textField);
        Assert.assertEquals(expected, textField.getText());
    }
}
