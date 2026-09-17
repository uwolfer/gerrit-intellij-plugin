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

import java.util.regex.Pattern;

/**
 * Whitespace as Unicode defines it, for the values users type or paste into the plugin's text
 * fields. The JDK shorthands are not enough for those: {@link String#trim()} stops at U+0020, and
 * {@link Character#isWhitespace(char)} says no to the non-breaking spaces U+00A0, U+2007 and U+202F
 * and to the next line U+0085 - all of which get pasted in, and none of which Gerrit or Git read as
 * anything but part of the value.
 */
public final class Whitespace {

    /** Matches one whitespace character; for building character classes such as {@code [\p{IsWhite_Space},]}. */
    public static final String REGEX_CLASS = "\\p{IsWhite_Space}";

    private static final Pattern SINGLE_CHARACTER = Pattern.compile(REGEX_CLASS);

    private static final Pattern SURROUNDING =
        Pattern.compile("^(?:" + REGEX_CLASS + ")+|(?:" + REGEX_CLASS + ")+$");

    private Whitespace() {}

    /**
     * Removes the whitespace from both ends of a value, all of it: what {@link String#trim()} leaves
     * behind ends up in a push reference or in a request to Gerrit.
     */
    public static String trim(String value) {
        return SURROUNDING.matcher(value).replaceAll("");
    }

    public static boolean isWhitespace(char character) {
        return SINGLE_CHARACTER.matcher(String.valueOf(character)).matches();
    }
}
