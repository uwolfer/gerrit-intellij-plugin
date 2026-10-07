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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.urswolfer.intellij.plugin.gerrit.util.Whitespace;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * What the text fields which suggest accounts from Gerrit share: they ask Gerrit on every keystroke, from within the
 * read action of the completion.
 */
public final class AccountCompletion {
    private static final Logger LOG = Logger.getInstance(AccountCompletion.class);

    /** A keystroke within this time cancels the completion, so that a name typed in one go is asked for once. */
    private static final long DEBOUNCE_MILLIS = 150;

    public interface Request<T> {
        List<T> get(String query) throws Exception;
    }

    private volatile boolean unexpectedFailureLogged;

    /**
     * The part of the text before the caret which the account is completed from: what follows the last separator, if
     * there is one. Only the leading whitespace is left out: the lookup replaces as many characters before the caret
     * as the prefix has, and a full name typed with a space still matches.
     */
    public static String prefix(String textBeforeCaret, String separator) {
        int index = separator.isEmpty() ? -1 : textBeforeCaret.lastIndexOf(separator);
        return Whitespace.trimLeading(index == -1 ? textBeforeCaret : textBeforeCaret.substring(index + separator.length()));
    }

    /**
     * Names and emails are typed into the lookup, so none of their characters picks a suggestion, as a dot or a space
     * otherwise would. The separator ends the account typed by hand and keeps it as it is.
     */
    public static CharFilter.Result acceptChar(char c, String separator) {
        return separator.indexOf(c) != -1 ? CharFilter.Result.HIDE_LOOKUP : CharFilter.Result.ADD_TO_PREFIX;
    }

    /**
     * Asks Gerrit for the suggestions of the prefix, and returns none when it cannot answer: there are just no
     * suggestions then, and an exception would be reported as an IDE error on every keystroke.
     *
     * Completion runs in a read action, which a slow or unreachable Gerrit would hold for as long as the HTTP request
     * takes, and with it every write action and the IDE waiting for one. The
     * request runs on a pooled thread instead, and waiting for it gives up as soon as the completion is cancelled, by
     * the next keystroke or by a pending write action.
     */
    public <T> List<T> fetch(String prefix, CompletionResultSet result, Request<T> request) {
        // Gerrit returns the first matches only: typing on has to ask again rather than filter those
        result.restartCompletionOnAnyPrefixChange();
        String query = Whitespace.trim(prefix);
        if (query.isEmpty()) {
            return Collections.emptyList();
        }
        sleepCancellably(DEBOUNCE_MILLIS);
        // not Application#executeOnPooledThread: it reports a failure as an IDE error and completes with null
        Future<List<T>> future = AppExecutorUtil.getAppExecutorService().submit(() -> request.get(query));
        List<T> suggestions;
        try {
            suggestions = await(future);
        } catch (ExecutionException e) {
            logFailure(e.getCause());
            return Collections.emptyList();
        }
        return result.isStopped() || suggestions == null ? Collections.<T>emptyList() : suggestions;
    }

    private void logFailure(Throwable failure) {
        if (failure instanceof RestApiException || unexpectedFailureLogged) {
            LOG.info("Failed to load suggestions: " + ExceptionUtil.getRootCause(failure));
        } else {
            // once per field: anything but a failed request is a bug, and it would come again on every keystroke
            unexpectedFailureLogged = true;
            LOG.warn("Failed to load suggestions.", failure);
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

    private static <T> T await(Future<T> future) throws ExecutionException {
        try {
            while (true) {
                ProgressManager.checkCanceled();
                try {
                    return future.get(20, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                }
            }
        } catch (ProcessCanceledException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException();
        }
    }
}
