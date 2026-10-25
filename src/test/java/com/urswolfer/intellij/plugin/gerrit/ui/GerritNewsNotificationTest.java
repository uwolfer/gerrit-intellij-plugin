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

import org.testng.Assert;
import org.testng.annotations.Test;

public class GerritNewsNotificationTest {

    @Test
    public void testShownOnceAndAgainOnlyForNewerNews() {
        Assert.assertTrue(GerritNewsNotification.isDue(0, 1));
        Assert.assertFalse(GerritNewsNotification.isDue(1, 1));
        Assert.assertTrue(GerritNewsNotification.isDue(1, 2));
        Assert.assertFalse(GerritNewsNotification.isDue(2, 1));
    }
}
