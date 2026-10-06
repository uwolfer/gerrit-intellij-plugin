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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.gerrit.extensions.common.ChangeInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

public class OpenInBrowserActionTest {

    @Test
    public void testUrl() {
        Assert.assertEquals("https://gerrit.example.com/r/74", OpenInBrowserAction.getUrl("https://gerrit.example.com/r", change(74)));
    }

    @Test
    public void testUrlOfHostWithTrailingSlash() {
        Assert.assertEquals("https://gerrit.example.com/74", OpenInBrowserAction.getUrl("https://gerrit.example.com/", change(74)));
    }

    private static ChangeInfo change(int number) {
        ChangeInfo change = new ChangeInfo();
        change._number = number;
        return change;
    }
}
