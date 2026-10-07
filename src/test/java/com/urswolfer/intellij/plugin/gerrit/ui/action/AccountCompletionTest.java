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

import com.intellij.codeInsight.lookup.CharFilter;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AccountCompletionTest {

    @Test
    public void testPrefixIsWhatFollowsTheLastSeparator() {
        Assert.assertEquals(AccountCompletion.prefix("jdoe,  Rit", ","), "Rit");
        Assert.assertEquals(AccountCompletion.prefix("Rit", ","), "Rit");
    }

    @Test
    public void testPrefixKeepsTrailingWhitespace() {
        // the lookup replaces as many characters before the caret as the prefix has
        Assert.assertEquals(AccountCompletion.prefix("jdoe, Rita ", ","), "Rita ");
        Assert.assertEquals(AccountCompletion.prefix("jdoe, ", ","), "");
    }

    @Test
    public void testPrefixWithoutSeparatorIsTheWholeText() {
        Assert.assertEquals(AccountCompletion.prefix(" Rita, Re", ""), "Rita, Re");
    }

    @Test
    public void testCharactersOfNamesAndEmailsDoNotPickASuggestion() {
        for (char c : " .@-_+'".toCharArray()) {
            Assert.assertEquals(AccountCompletion.acceptChar(c, ","), CharFilter.Result.ADD_TO_PREFIX, "'" + c + "'");
            Assert.assertEquals(AccountCompletion.acceptChar(c, ""), CharFilter.Result.ADD_TO_PREFIX, "'" + c + "'");
        }
    }

    @Test
    public void testSeparatorClosesTheLookup() {
        Assert.assertEquals(AccountCompletion.acceptChar(',', ","), CharFilter.Result.HIDE_LOOKUP);
        Assert.assertEquals(AccountCompletion.acceptChar(',', ""), CharFilter.Result.ADD_TO_PREFIX);
    }
}
