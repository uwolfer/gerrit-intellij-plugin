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

package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import org.testng.Assert;
import org.testng.annotations.Test;

public class FulltextFilterTest {

    @Test
    public void testEncodeSpecialCharacters() throws Exception {
        Assert.assertEquals(FulltextFilter.specialEncodeFulltextQuery("{merged:1day}"), "%7Bmerged:1day%7D");
        Assert.assertEquals(FulltextFilter.specialEncodeFulltextQuery("a+b"), "a%2Bb");
        Assert.assertEquals(FulltextFilter.specialEncodeFulltextQuery("message:\"fix\""), "message:%22fix%22");
        Assert.assertEquals(FulltextFilter.specialEncodeFulltextQuery("a\\b"), "a%5Cb");
        Assert.assertEquals(FulltextFilter.specialEncodeFulltextQuery("a<b>c^d"), "a%3Cb%3Ec%5Ed");
    }

    @Test
    public void testEncodePercentOnlyOnce() throws Exception {
        Assert.assertEquals(FulltextFilter.specialEncodeFulltextQuery("50%"), "50%25");
        // the percent signs of the encoded characters must not be encoded again
        Assert.assertEquals(FulltextFilter.specialEncodeFulltextQuery("100% {done}"), "100%25+%7Bdone%7D");
    }

    @Test
    public void testEncodeSpaceAsPlus() throws Exception {
        Assert.assertEquals(FulltextFilter.specialEncodeFulltextQuery("owner:self status:open"), "owner:self+status:open");
    }
}
