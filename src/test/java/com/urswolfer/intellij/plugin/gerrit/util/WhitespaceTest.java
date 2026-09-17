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

package com.urswolfer.intellij.plugin.gerrit.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class WhitespaceTest {

    @Test
    public void testTrimRemovesWhatStringTrimDoesNot() throws Exception {
        // String#trim() stops at U+0020 and leaves all of these in the value
        Assert.assertEquals(Whitespace.trim(" my-topic "), "my-topic");
        Assert.assertEquals(Whitespace.trim(" my-topic "), "my-topic");
        Assert.assertEquals(Whitespace.trim(" my-topic "), "my-topic");
        Assert.assertEquals(Whitespace.trim("my-topic"), "my-topic");
        Assert.assertEquals(Whitespace.trim("　my-topic　"), "my-topic");
    }

    @Test
    public void testTrimRemovesMixedWhitespaceFromBothEnds() throws Exception {
        Assert.assertEquals(Whitespace.trim(" \t my-topic　\n "), "my-topic");
    }

    @Test
    public void testTrimKeepsWhatIsNotAtTheEnds() throws Exception {
        Assert.assertEquals(Whitespace.trim("my topic"), "my topic");
        Assert.assertEquals(Whitespace.trim("my topic"), "my topic");
    }

    @Test
    public void testTrimOfValuesWithoutContent() throws Exception {
        Assert.assertEquals(Whitespace.trim(""), "");
        Assert.assertEquals(Whitespace.trim("  　"), "");
    }

    @Test
    public void testIsWhitespace() throws Exception {
        Assert.assertTrue(Whitespace.isWhitespace(' '));
        Assert.assertTrue(Whitespace.isWhitespace('\t'));
        Assert.assertTrue(Whitespace.isWhitespace(' '));
        Assert.assertTrue(Whitespace.isWhitespace(' '));
        Assert.assertFalse(Whitespace.isWhitespace('a'));
        Assert.assertFalse(Whitespace.isWhitespace('-'));
    }
}
