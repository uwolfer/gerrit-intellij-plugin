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

import com.google.common.base.CharMatcher;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.Nullable;

/**
 * Checks the values entered in the Gerrit push settings for what cannot be transported in the Git
 * reference the commits are pushed to (e.g. "refs/for/master%topic=my-topic"). Gerrit reads them
 * from that reference without decoding them; the patch set description is the only exception, see
 * {@link com.urswolfer.intellij.plugin.gerrit.util.UrlUtils#encodePatchSetDescription(String)}.
 *
 * Characters which are invalid in a reference name are already rejected by the push target panel,
 * which validates the assembled reference with git4idea. Only the cases it cannot report properly
 * are handled here: whitespace (what users actually run into, and the reference name error does not
 * tell which of the values caused it), and the two characters which Git accepts but Gerrit reads as
 * syntax of the reference.
 */
public class PushOptionValidator {

    private static final CharMatcher WHITESPACE = CharMatcher.whitespace();

    /** A comma separates the Gerrit push options from each other. */
    private static final CharMatcher INVALID_OPTION_CHARS = WHITESPACE.or(CharMatcher.is(','));

    /** Gerrit handles everything after the first percent sign of a reference as push options. */
    private static final CharMatcher INVALID_BRANCH_CHARS = WHITESPACE.or(CharMatcher.is('%'));

    private PushOptionValidator() {}

    /**
     * Removes the whitespace which this class rejects from both ends of a value. String#trim() is not
     * enough: it stops at U+0020, which would leave e.g. a pasted no-break space to be rejected instead
     * of being trimmed away.
     */
    public static String trim(String value) {
        return WHITESPACE.trimFrom(value);
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
     * What this rejects (e.g. a branch ending with a slash) is reported by the push target itself, which
     * validates the assembled ref - it just does not tell which of the values it is caused by.
     */
    public static boolean isUsableAsBranchName(String value) {
        return value.isEmpty() || GitRefNameValidator.getInstance().checkInput(value);
    }

    @Nullable
    private static String validate(String label, String value, CharMatcher invalidChars) {
        int index = invalidChars.indexIn(value);
        if (index < 0) {
            return null;
        }
        char invalidChar = value.charAt(index);
        if (WHITESPACE.matches(invalidChar)) {
            // name the character for whitespace which cannot be seen in the text field
            String whitespace = invalidChar == ' ' ? "spaces" : String.format("whitespace (U+%04X)", (int) invalidChar);
            return label + " must not contain " + whitespace + " (Gerrit reads it from the push reference, "
                + "and Git reference names cannot contain spaces).";
        }
        return label + " must not contain the character '" + invalidChar + "'.";
    }
}
