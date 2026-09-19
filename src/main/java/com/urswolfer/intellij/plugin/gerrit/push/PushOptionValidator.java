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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the values entered in the Gerrit push settings for what cannot be transported in the Git
 * reference the commits are pushed to (e.g. "refs/for/master%topic=my-topic"). Gerrit reads them
 * from that reference without decoding them; the patch set description is the only exception, see
 * {@link com.urswolfer.intellij.plugin.gerrit.util.UrlUtils#encodePatchSetDescription(String)}.
 *
 * The assembled reference is validated with git4idea as well, but that error does not tell which of
 * the values caused it - and it does not catch the characters which Git accepts while Gerrit reads
 * them as syntax of the reference.
 */
public class PushOptionValidator {

    /** A comma separates the Gerrit push options from each other, the rest cannot be part of a ref name. */
    private static final Pattern INVALID_OPTION_CHARS =
        Pattern.compile("[" + Whitespace.REGEX_CLASS + ",~^:?*\\[\\\\]");

    /** Gerrit handles everything after the first percent sign of a reference as push options. */
    private static final Pattern INVALID_BRANCH_CHARS = Pattern.compile("[" + Whitespace.REGEX_CLASS + "%]");

    private PushOptionValidator() {}

    /**
     * Removes the whitespace which this class rejects from both ends of a value, so that a pasted
     * no-break space is trimmed away instead of being reported.
     */
    public static String trim(String value) {
        return Whitespace.trim(value);
    }

    /**
     * Returns an error message for the push destination branch, or {@code null} if it can be used.
     */
    @Nullable
    public static String validateBranch(String label, String value) {
        return validate(label, value, INVALID_BRANCH_CHARS);
    }

    /**
     * Returns an error message for a push option value (topic, hashtag, user name, ...), or
     * {@code null} if it can be used.
     */
    @Nullable
    public static String validateOption(String label, String value) {
        return validate(label, value, INVALID_OPTION_CHARS);
    }

    /**
     * Tells whether the branch can be part of a ref name. An empty value can: the branch of the push
     * target is used then.
     *
     * What this rejects (e.g. a branch ending with a slash) is reported for the assembled ref, which is
     * validated as a whole - that check just does not tell which of the values it is caused by.
     */
    public static boolean isUsableAsBranchName(String value) {
        return value.isEmpty() || GitRefNameValidator.getInstance().checkInput(value);
    }

    @Nullable
    private static String validate(String label, String value, Pattern invalidChars) {
        Matcher matcher = invalidChars.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        char invalidChar = value.charAt(matcher.start());
        if (Whitespace.isWhitespace(invalidChar)) {
            // name the character for whitespace which cannot be seen in the text field
            String whitespace = invalidChar == ' ' ? "spaces" : String.format("whitespace (U+%04X)", (int) invalidChar);
            return label + " must not contain " + whitespace + " (Gerrit reads it from the push reference, "
                + "and Git reference names cannot contain spaces).";
        }
        return label + " must not contain the character '" + invalidChar + "'.";
    }
}
