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

import com.urswolfer.intellij.plugin.gerrit.ui.ChangeListEmptyText.Kind;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ChangeListEmptyTextTest {

    @Test
    public void testEmptyAnswerIsBlamedOnTheFiltersOnlyWhenTheyAreNarrowed() {
        Assert.assertEquals(ChangeListEmptyText.of(false, true), Kind.NO_MATCH);
        Assert.assertEquals(ChangeListEmptyText.of(false, false), Kind.NO_CHANGES);
    }

    @Test
    public void testLookupIsNotBlamedOnTheFilters() {
        Assert.assertEquals(ChangeListEmptyText.of(true, false), Kind.NO_LOOKUP_RESULT);
        Assert.assertEquals(ChangeListEmptyText.of(true, true), Kind.NO_LOOKUP_RESULT);
    }

    @Test
    public void testShortReasonIsShown() {
        Assert.assertEquals(ChangeListEmptyText.reason(" Unauthorized\n"), "Unauthorized");
        Assert.assertEquals(ChangeListEmptyText.reason("Connection refused"), "Connection refused");
    }

    @Test
    public void testLongOrMultiLineReasonIsLeftToTheNotification() {
        Assert.assertNull(ChangeListEmptyText.reason(null));
        Assert.assertNull(ChangeListEmptyText.reason("  "));
        Assert.assertNull(ChangeListEmptyText.reason("first line\nsecond line"));
        Assert.assertNull(ChangeListEmptyText.reason("x".repeat(101)));
    }
}
