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

package com.urswolfer.intellij.plugin.gerrit.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Urs Wolfer
 */
public class TextToHtmlTest {

    @DataProvider(name = "textToHtml")
    public Object[][] createData1() {
        return new Object[][] {
                { "Text single line", "Text single line" },
                { "Text\non\nnew\nline", "<p>Text\non\nnew\nline</p>"},
                { "Test\n\n* list 1\n* list 2\n* list 3\n\nEnd line",
                        "<p>Test</p><ul><li>list 1</li><li>list 2</li><li>list 3</li></ul><p>End line</p>"},
                { "Test\n\n  code line 1\n  code line 2\n  code line 3\n    code line 4 more indented\n\nEnd line",
                        "<p>Test</p><pre>  code line 1<br />  code line 2<br />  code line 3<br />    code line 4 more indented<br /></pre><p>End line</p>"},
        };
    }

    @Test(dataProvider = "textToHtml")
    public void testTextToHtml(String text, String expectedHtml) throws Exception {
        Assert.assertEquals(TextToHtml.textToHtml(text), expectedHtml);
    }
}
