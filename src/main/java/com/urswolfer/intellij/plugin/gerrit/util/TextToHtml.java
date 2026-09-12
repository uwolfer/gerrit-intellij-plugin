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

import com.urswolfer.intellij.plugin.gerrit.util.safehtml.SafeHtmlBuilder;

/**
 * @author Urs Wolfer
 */
public final class TextToHtml {

    private TextToHtml() {}

    /**
     * Converts plain text formatted with wiki-like syntax to HTML.
     */
    public static String textToHtml(String text) {
        // SafeHtml handles the text it is given as HTML which is escaped already. A comment or a change message
        // is plain text, so it needs to be escaped first; otherwise it is rendered as markup (and the quote
        // handling of wikify(), which looks for an escaped "&gt; ", never matches).
        String escapedText = new SafeHtmlBuilder().append(text).toSafeHtml().asString();
        if (!escapedText.contains("\n")) {
            return SafeHtmlBuilder.asis(escapedText).linkify().asString();
        }
        String html = SafeHtmlBuilder.asis(escapedText).wikify().asString();
        return html.replace("</p><p>", "</p><br/><p>"); // otherwise paragraph breaks are not visible in IntelliJ...
    }
}
