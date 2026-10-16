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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritChangeDetailsPanel;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The comments of one side in threads, as Gerrit's web UI puts them: a comment with the ones which reply to it, and
 * to those, under the line of the first. A reply to a comment which is not there, such as one on another patch set,
 * starts a thread of its own.
 */
final class CommentThreads {
    // as Gerrit's web UI sorts them: drafts last, as the reply being written, and by id, which saving one again keeps;
    // the published ones as they were written, those published at once by id
    private static final Comparator<Comment> ORDER = (a, b) -> {
        boolean draft = isDraft(a);
        if (draft != isDraft(b)) return draft ? 1 : -1;
        if (!draft) {
            int written = Comparator.nullsFirst(Comparator.<Date>naturalOrder()).compare(a.updated, b.updated);
            if (written != 0) return written;
        }
        return Comparator.nullsFirst(Comparator.<String>naturalOrder()).compare(a.id, b.id);
    };

    private CommentThreads() {
    }

    /** The order of the comments of a thread, and of threads by their first comments. */
    static Comparator<Comment> order() {
        return ORDER;
    }

    /**
     * @param of the comment an item stands for
     * @return the threads, each with its first comment first; sorted by {@link #order()} of their roots by the caller,
     *         who may have those of more than one side
     */
    static <T> List<Thread<T>> group(List<T> items, Function<T, Comment> of) {
        Map<String, T> byId = new HashMap<>();
        for (T item : items) {
            String id = of.apply(item).id;
            if (id != null) {
                byId.put(id, item);
            }
        }
        Map<T, List<T>> byRoot = new LinkedHashMap<>();
        for (T item : items) {
            byRoot.computeIfAbsent(root(item, byId, of), root -> new ArrayList<>()).add(item);
        }
        List<Thread<T>> threads = new ArrayList<>();
        byRoot.forEach((root, thread) -> threads.add(new Thread<>(root, repliesAfter(thread, of), of)));
        return threads;
    }

    /**
     * In order, but each after the comment it replies to: Gerrit keeps the time to the second, so a quick reply has
     * the time of its comment, and the id would decide.
     */
    private static <T> List<T> repliesAfter(List<T> thread, Function<T, Comment> of) {
        List<T> pending = new ArrayList<>(thread);
        pending.sort(Comparator.comparing(of, ORDER));
        Set<String> ids = new HashSet<>();
        for (T item : pending) {
            ids.add(of.apply(item).id);
        }
        List<T> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        while (!pending.isEmpty()) {
            T next = pending.stream()
                .filter(item -> {
                    String parent = of.apply(item).inReplyTo;
                    return parent == null || !ids.contains(parent) || placed.contains(parent);
                })
                .findFirst()
                .orElse(pending.get(0)); // a cycle, which Gerrit does not allow
            pending.remove(next);
            ordered.add(next);
            placed.add(of.apply(next).id);
        }
        return ordered;
    }

    private static <T> T root(T item, Map<String, T> byId, Function<T, Comment> of) {
        T root = item;
        Set<T> seen = new HashSet<>(); // Gerrit allows no cycle, but nothing would end one here
        while (seen.add(root)) {
            String parent = of.apply(root).inReplyTo;
            T parentItem = parent != null ? byId.get(parent) : null;
            if (parentItem == null) break;
            root = parentItem;
        }
        return root;
    }

    /** One line, the whitespace of the message collapsed. */
    static String excerpt(@Nullable String message) {
        return StringUtil.notNullize(message).trim().replaceAll("\\s+", " ");
    }

    /** None for a draft, which is the user's own. */
    @Nullable
    static AccountInfo author(Comment comment) {
        return comment instanceof CommentInfo ? ((CommentInfo) comment).author : null;
    }

    static boolean isDraft(Comment comment) {
        return comment instanceof CommentInfo && author(comment) == null;
    }

    /** As plain text: a draft is the user's own. */
    static String authorName(Comment comment) {
        AccountInfo author = author(comment);
        return author == null ? GerritBundle.message("diff.myself") : GerritChangeDetailsPanel.accountDisplayName(author);
    }

    static final class Thread<T> {
        final T root;
        final List<T> items;
        private final Function<T, Comment> of;

        private Thread(T root, List<T> items, Function<T, Comment> of) {
            this.root = root;
            this.items = items;
            this.of = of;
        }

        List<Comment> comments() {
            List<Comment> comments = new ArrayList<>();
            for (T item : items) {
                comments.add(of.apply(item));
            }
            return comments;
        }

        Comment last() {
            return of.apply(items.get(items.size() - 1));
        }

        /**
         * As the last comment says, a draft's included; one which does not say is resolved, as Gerrit's web UI has it
         * in comment-util, and as the comment form starts out.
         */
        boolean isUnresolved() {
            return Boolean.TRUE.equals(last().unresolved);
        }

        boolean hasDraft() {
            return isDraft(last());
        }

        /**
         * As Gerrit's web UI does when it shows a comment, which folds the published comments of a resolved thread, and
         * no comment it shows already.
         */
        boolean isFoldedAtFirst(Comment comment) {
            return !isDraft(comment) && !isUnresolved();
        }
    }
}
