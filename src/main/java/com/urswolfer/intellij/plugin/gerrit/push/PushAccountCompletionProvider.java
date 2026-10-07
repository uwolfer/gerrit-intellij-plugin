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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import com.urswolfer.intellij.plugin.gerrit.ui.action.AccountLookup;
import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Suggests the accounts for the reviewers and CC fields of the push dialog. There is no change yet to ask Gerrit for
 * its reviewer suggestions, so the accounts come from the account suggestions of the server.
 *
 * Groups are not suggested: Gerrit resolves the reviewers of a push reference to accounts only.
 */
class PushAccountCompletionProvider extends TextFieldCompletionProviderDumbAware {
    private static final Logger LOG = Logger.getInstance(PushAccountCompletionProvider.class);

    private static final String SEPARATOR = ",";

    /** A keystroke within this time cancels the completion, so that a name typed in one go is asked for once. */
    private static final long DEBOUNCE_MILLIS = 150;

    private final Project project;

    private volatile boolean unexpectedFailureLogged;

    PushAccountCompletionProvider(Project project) {
        super(true);
        this.project = project;
    }

    @NotNull
    @Override
    protected String getPrefix(@NotNull String currentTextPrefix) {
        int separator = currentTextPrefix.lastIndexOf(SEPARATOR);
        String prefix = separator == -1 ? currentTextPrefix : currentTextPrefix.substring(separator + 1);
        // only the leading whitespace: the lookup replaces as many characters before the caret as the prefix has,
        // and a full name typed with a space still matches
        return Whitespace.trimLeading(prefix);
    }

    /**
     * Names and emails are typed into the lookup, so none of their characters picks a suggestion, as a dot or a
     * space otherwise would. A comma ends the account typed by hand and keeps it as it is.
     */
    @Nullable
    @Override
    public CharFilter.Result acceptChar(char c) {
        return SEPARATOR.charAt(0) == c ? CharFilter.Result.HIDE_LOOKUP : CharFilter.Result.ADD_TO_PREFIX;
    }

    @Override
    protected void addCompletionVariants(@NotNull String text,
                                         int offset,
                                         @NotNull String prefix,
                                         @NotNull CompletionResultSet result) {
        // Gerrit returns the first 20 matches: typing on has to ask again rather than filter those
        result.restartCompletionOnAnyPrefixChange();
        if (prefix.isEmpty() || project.isDisposed()) {
            return;
        }
        // read per call: the project can be bound to another account while the dialog is open
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        if (account == null) {
            return;
        }
        sleepCancellably(DEBOUNCE_MILLIS);
        Future<List<AccountInfo>> request = ApplicationManager.getApplication().executeOnPooledThread(() ->
            GerritApiProvider.getInstance().get(account).accounts()
                .suggestAccounts(Whitespace.trim(prefix)).withLimit(20).get());
        List<AccountInfo> accounts;
        try {
            accounts = await(request);
        } catch (ExecutionException e) {
            // runs on every keystroke: without Gerrit there are just no suggestions, and an exception would be
            // reported as an IDE error each time
            if (e.getCause() instanceof RestApiException || unexpectedFailureLogged) {
                LOG.info("Failed to load suggestions: " + ExceptionUtil.getRootCause(e));
            } else {
                unexpectedFailureLogged = true;
                LOG.warn("Failed to load suggestions.", e.getCause());
            }
            return;
        }
        if (result.isStopped()) {
            return;
        }
        for (AccountInfo suggestion : accounts) {
            result.addElement(AccountLookup.lookupElementInserting(suggestion, pushIdentifier(suggestion) + SEPARATOR));
        }
    }

    private static void sleepCancellably(long millis) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < end) {
            ProgressManager.checkCanceled();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProcessCanceledException();
            }
        }
    }

    /**
     * Completion runs in a read action, which a slow or unreachable Gerrit would hold for as long as the HTTP timeout,
     * and with it every write action and the IDE waiting for one. Waiting here instead gives up as soon as the
     * completion is cancelled, by the next keystroke or by a pending write action.
     */
    private static <T> T await(Future<T> request) throws ExecutionException {
        try {
            while (true) {
                ProgressManager.checkCanceled();
                try {
                    return request.get(20, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                }
            }
        } catch (ProcessCanceledException e) {
            request.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            request.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException();
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
