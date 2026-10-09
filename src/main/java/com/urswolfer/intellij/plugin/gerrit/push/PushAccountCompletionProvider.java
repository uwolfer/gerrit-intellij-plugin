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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.google.gerrit.extensions.common.AccountInfo;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.openapi.project.Project;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import com.urswolfer.intellij.plugin.gerrit.ui.action.AccountCompletion;
import com.urswolfer.intellij.plugin.gerrit.ui.action.AccountLookup;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggests the accounts for the reviewers and CC fields of the push dialog and of the dialog which creates a feature
 * merge change. There is no change yet to ask Gerrit for its reviewer suggestions, so the accounts come from the
 * account suggestions of the server.
 *
 * Groups are not suggested: Gerrit resolves the reviewers of a push reference to accounts only.
 */
public class PushAccountCompletionProvider extends TextFieldCompletionProviderDumbAware {
    private static final String SEPARATOR = ",";

    private final Project project;

    private final AccountCompletion completion = new AccountCompletion();

    public PushAccountCompletionProvider(Project project) {
        super(true);
        this.project = project;
    }

    @NotNull
    @Override
    protected String getPrefix(@NotNull String currentTextPrefix) {
        return AccountCompletion.prefix(currentTextPrefix, SEPARATOR);
    }

    @Nullable
    @Override
    public CharFilter.Result acceptChar(char c) {
        return AccountCompletion.acceptChar(c, SEPARATOR);
    }

    @Override
    protected void addCompletionVariants(@NotNull String text,
                                         int offset,
                                         @NotNull String prefix,
                                         @NotNull CompletionResultSet result) {
        if (project.isDisposed()) {
            return;
        }
        // read per call: the project can be bound to another account while the dialog is open
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        if (account == null) {
            return;
        }
        List<AccountInfo> accounts = completion.fetch(prefix, result, query ->
            GerritApiProvider.getInstance().get(account).accounts().suggestAccounts(query).withLimit(20).get());
        for (AccountInfo suggestion : accounts) {
            result.addElement(AccountLookup.lookupElementInserting(suggestion, pushIdentifier(suggestion) + SEPARATOR));
        }
    }

    /**
     * The value a push reference can carry for the account: no whitespace, no comma, nothing a Git reference name
     * rejects - which rules out "Name &lt;email&gt;". The username comes first, as it names one account; an email can
     * be shared. A numeric username is skipped, Gerrit takes it for an account id. The account id itself is the last
     * resort, valid always but meaningless to read.
     */
    static String pushIdentifier(AccountInfo account) {
        List<String> candidates = new ArrayList<>();
        if (account.username != null && !account.username.chars().allMatch(Character::isDigit)) {
            candidates.add(account.username);
        }
        if (account.email != null) {
            candidates.add(account.email);
        }
        for (String candidate : candidates) {
            if (!candidate.isEmpty() && PushOptionValidator.isValidOption(candidate)
                    && GitRefNameValidator.getInstance().checkInput("refs/for/master%r=" + candidate)) {
                return candidate;
            }
        }
        return String.valueOf(account._accountId);
    }
}
