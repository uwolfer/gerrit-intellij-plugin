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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.client.Comment;
import org.junit.Assert;
import org.testng.annotations.Test;

public class RangeUtilsTest {

    public static final StringBuilder STRING = new StringBuilder("short text with\nline break");

    @Test
    public void testTextOffsetToRangeSingleLine() throws Exception {
        Comment.Range range = RangeUtils.textOffsetToRange(STRING, 22, 27); // word "break"

        Assert.assertEquals(2, range.startLine);
        Assert.assertEquals(6, range.startCharacter);
        Assert.assertEquals(2, range.endLine);
        Assert.assertEquals(11, range.endCharacter);
    }

    @Test
    public void testTextOffsetToRangeMultiLine() throws Exception {
        Comment.Range range = RangeUtils.textOffsetToRange(STRING, 11, 20); // "with\nline"

        Assert.assertEquals(1, range.startLine);
        Assert.assertEquals(11, range.startCharacter);
        Assert.assertEquals(2, range.endLine);
        Assert.assertEquals(4, range.endCharacter);
    }

    @Test
    public void testTextOffsetToRangeStartingAtLineStart() throws Exception {
        Comment.Range range = RangeUtils.textOffsetToRange(STRING, 16, 26); // line 2, selected as a whole line

        Assert.assertEquals(2, range.startLine);
        Assert.assertEquals(0, range.startCharacter);
        Assert.assertEquals(2, range.endLine);
        Assert.assertEquals(10, range.endCharacter);
    }

    @Test
    public void testTextOffsetToRangeStartingAtTextStart() throws Exception {
        Comment.Range range = RangeUtils.textOffsetToRange(STRING, 0, 5); // word "short"

        Assert.assertEquals(1, range.startLine);
        Assert.assertEquals(0, range.startCharacter);
        Assert.assertEquals(1, range.endLine);
        Assert.assertEquals(5, range.endCharacter);
    }
}
