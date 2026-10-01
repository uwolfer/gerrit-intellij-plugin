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
        String identifier = identifier(account);
        return LookupElementBuilder.create(identifier + insertSuffix)
            .withPresentableText(identifier)
            .withLookupStrings(alternativeLookupStrings(account));
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
