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
                { "Use List<String> & Map<K,V> here", "Use List&lt;String&gt; &amp; Map&lt;K,V&gt; here"},
                { "Use List<String> here\nand Map<K,V> there", "<p>Use List&lt;String&gt; here\nand Map&lt;K,V&gt; there</p>"},
                { "> quoted previous comment\n> second line\n\nmy reply",
                        "<blockquote>quoted previous comment\nsecond line</blockquote><p>my reply</p>"},
                { "Better:\n```java\nif (a < b) {\n\n    run();\n}\n```\nthen\n\nmore",
                        "<p>Better:</p><pre>if (a &lt; b) {<br /><br />    run();<br />}<br /></pre><p>then</p><br/><p>more</p>"},
                { "> quoted\n\n```\ncode\n```", "<blockquote>quoted</blockquote><pre>code<br /></pre>"},
                { "* item\n\n~~~~\n```\n~~~~\n\n* other",
                        "<ul><li>item</li></ul><pre>```<br /></pre><ul><li>other</li></ul>"},
                { "  ```java\nint i;\n```", "<pre>int i;<br /></pre>"},
                { "Release notes\n~~~~~~~~\n\nfoo", "<p>Release notes\n~~~~~~~~</p><br/><p>foo</p>"},
                { "a\r\n```\r\nint i;\r\n```\r\n\r\nb", "<p>a</p><pre>int i;<br /></pre><p>b</p>"},
                { "```a``` is\ninline", "<p>```a``` is\ninline</p>"},
                { "```md\n    ```\n```\nafter", "<pre>    ```<br /></pre><p>after</p>"},
                { "see http://example.com/x for details",
                        "see <a href=\"http://example.com/x\" target=\"_blank\" rel=\"nofollow\">http://example.com/x</a> for details"},
        };
    }

    @Test
    public void testNullMessage() {
        Assert.assertEquals(TextToHtml.textToHtml(null), "");
    }

    @Test(dataProvider = "textToHtml")
    public void testTextToHtml(String text, String expectedHtml) throws Exception {
        Assert.assertEquals(TextToHtml.textToHtml(text), expectedHtml);
    }
}
