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
        if (!text.contains("\n")) {
            return text;
        }
        text = SafeHtmlBuilder.asis(text).wikify().asString();
        text = text.replace("</p><p>", "</p><br/><p>"); // otherwise paragraph breaks are not visible in IntelliJ...
        return text;
    }
}
