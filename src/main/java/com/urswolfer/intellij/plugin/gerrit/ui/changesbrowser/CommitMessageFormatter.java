/*
 * Copyright 2013 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.intellij.vcs.log.Hash;
import git4idea.GitCommit;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * This class formats the commit message as similarly as possible to how Gerrit formats it.
 * It mainly needs to have the same content on the same line, in order for comments to be displayed and posted correctly.
 * The subject line for parent commit(s) is missing, to avoid fetching more commits from git at this stage. It might be
 * implemented later.
 *
 * The main reason for this class to be necessary is the fact that the REST endpoint to retrieve a file's content does
 * not support requesting the commit message's content. It could be constructed using the diff endpoint, but this seemed
 * more complex and would result in unnecessary REST calls.
 *
 * However, this class might be re-implemented at a later time.
 *
 * @author Thomas Forrer
 */
public class CommitMessageFormatter {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss Z";

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat;
        }
    };
    private static final String PARENT_PATTERN = "Parent:     %s\n";
    private static final String MERGE_PATTERN =
            "Merge Of:   %s\n";
    private static final String MERGE_PATTERN_DELIMITER =
            "\n            ";
    private static final String PATTERN =
            "%s" +
            "Author:     %s <%s>\n" +
            "AuthorDate: %s\n" +
            "Commit:     %s <%s>\n" +
            "CommitDate: %s\n" +
            "\n" +
            "%s\n";

    private final GitCommit gitCommit;

    public CommitMessageFormatter(GitCommit gitCommit) {
        this.gitCommit = gitCommit;
    }

    public String getLongCommitMessage() {
        return String.format(PATTERN,
                getParentLine(),
                gitCommit.getAuthor().getName(), gitCommit.getAuthor().getEmail(),
                DATE_FORMAT.get().format(new Date(gitCommit.getAuthorTime())),
                gitCommit.getCommitter().getName(), gitCommit.getCommitter().getEmail(),
                DATE_FORMAT.get().format(gitCommit.getCommitTime()),
                gitCommit.getFullMessage()
        );
    }

    private String getParentLine() {
        List<Hash> parents = gitCommit.getParents();
        if (parents.size() == 1) {
            Hash parent = Iterables.getOnlyElement(parents);
            return String.format(PARENT_PATTERN, parent.asString());
        } else if (parents.size() > 1) {
            String allParents = Joiner.on(MERGE_PATTERN_DELIMITER).join(parents);
            return String.format(MERGE_PATTERN, allParents);
        } else {
            return "";
        }
    }
}
