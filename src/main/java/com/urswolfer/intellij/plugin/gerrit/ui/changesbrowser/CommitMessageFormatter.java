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
import git4idea.history.browser.GitHeavyCommit;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
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
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
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

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final GitHeavyCommit gitCommit;

    public CommitMessageFormatter(GitHeavyCommit gitCommit) {
        this.gitCommit = gitCommit;
    }

    public String getLongCommitMessage() {
        return String.format(PATTERN,
                getParentLine(),
                gitCommit.getAuthor(), gitCommit.getAuthorEmail(),
                DATE_FORMAT.format(new Date(gitCommit.getAuthorTime())),
                gitCommit.getCommitter(), gitCommit.getCommitterEmail(),
                DATE_FORMAT.format(gitCommit.getDate()),
                gitCommit.getDescription()
        );
    }

    private String getParentLine() {
        Set<String> parents = gitCommit.getParentsHashes();
        if (parents.size() == 1) {
            String parent = Iterables.getFirst(parents, null);
            return String.format(PARENT_PATTERN, parent);
        } else if (parents.size() > 1) {
            String allParents = Joiner.on(MERGE_PATTERN_DELIMITER).join(parents);
            return String.format(MERGE_PATTERN, allParents);
        } else {
            return "";
        }
    }
}
