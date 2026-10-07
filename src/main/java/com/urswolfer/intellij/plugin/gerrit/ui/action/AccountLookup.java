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

import com.google.gerrit.extensions.common.AccountInfo;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccountLookup {

    private AccountLookup() {}

    /**
     * Gerrit resolves an account from "Name &lt;email&gt;" and from "Name (id)", so the value stays unambiguous
     * when it is sent back.
     */
    public static String identifier(AccountInfo account) {
        if (account.email != null) {
            return account.name != null ? String.format("%s <%s>", account.name, account.email) : account.email;
        }
        if (account.name != null) {
            return String.format("%s (%s)", account.name, account._accountId);
        }
        return account.username != null ? account.username : String.valueOf(account._accountId);
    }

    static LookupElementBuilder lookupElement(AccountInfo account, String insertSuffix) {
        return lookupElementInserting(account, insertedIdentifier(account, insertSuffix) + insertSuffix);
    }

    /**
     * An element which shows the account as {@link #identifier(AccountInfo)} and is found by the parts of it, but
     * inserts the given text.
     */
    public static LookupElementBuilder lookupElementInserting(AccountInfo account, String insertedText) {
        String identifier = identifier(account);
        List<String> lookupStrings = alternativeLookupStrings(account);
        // an account inserted by its id is still found by what was typed of its name, "van der" included
        lookupStrings.add(identifier);
        return LookupElementBuilder.create(insertedText)
            .withPresentableText(identifier)
            .withLookupStrings(lookupStrings);
    }

    /**
     * The suffix separates the accounts of a list which is split on it again, so an identifier containing it, as
     * of the name "Doe, John", would turn into two accounts. The account id is what Gerrit tries first, and the
     * only value which names exactly one account: an email can be shared, and a username can be numeric.
     */
    static String insertedIdentifier(AccountInfo account, String separator) {
        String identifier = identifier(account);
        return separator.isEmpty() || !identifier.contains(separator) ? identifier : String.valueOf(account._accountId);
    }

    /**
     * Gerrit suggests an account when any part of its name matches, but the completion popup only keeps an element
     * whose lookup strings start with what was typed - without these a search for a surname shows nothing.
     */
    static List<String> alternativeLookupStrings(AccountInfo account) {
        List<String> lookupStrings = new ArrayList<>();
        if (account.name != null) {
            Collections.addAll(lookupStrings, account.name.split(Whitespace.REGEX_CLASS + "+"));
        }
        if (account.email != null) {
            lookupStrings.add(account.email);
        }
        if (account.username != null) {
            lookupStrings.add(account.username);
        }
        lookupStrings.removeIf(String::isEmpty);
        return lookupStrings;
    }
}
