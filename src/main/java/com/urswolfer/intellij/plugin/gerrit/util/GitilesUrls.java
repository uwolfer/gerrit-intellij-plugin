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

package com.urswolfer.intellij.plugin.gerrit.util;

import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds links into Gitiles, which addresses everything as {@code <base>/<project>/+/<revision>/<path>}.
 *
 * @author Urs Wolfer
 */
public final class GitilesUrls {

    /**
     * Where Gerrit serves its bundled Gitiles plugin, relative to the Gerrit URL.
     */
    public static final String PLUGIN_PATH = "/plugins/gitiles";

    private GitilesUrls() {}

    /**
     * @return the configured Gitiles URL, or the bundled plugin's on the Gerrit host; empty while neither is known
     */
    public static String getBaseUrl(@Nullable String gitilesUrl, @Nullable String gerritUrl) {
        if (gitilesUrl != null && !gitilesUrl.trim().isEmpty()) {
            return stripTrailingSlashes(gitilesUrl.trim());
        }
        if (gerritUrl == null || gerritUrl.trim().isEmpty()) {
            return "";
        }
        return stripTrailingSlashes(gerritUrl.trim()) + PLUGIN_PATH;
    }

    public static String getCommitUrl(String baseUrl, String project, String revision) {
        return String.format("%s/%s/+/%s", stripTrailingSlashes(baseUrl), encodePath(project), encodePath(revision));
    }

    /**
     * @param path relative to the repository root; empty for the root itself
     * @param line 1-based, or {@code null} for no line
     */
    public static String getFileUrl(String baseUrl, String project, String revision, String path,
                                    @Nullable Integer line) {
        // without the trailing slash Gitiles would show the commit rather than the root tree
        String url = getCommitUrl(baseUrl, project, revision) + "/" + encodePath(path);
        return line != null ? url + "#" + line : url;
    }

    private static String stripTrailingSlashes(String url) {
        String stripped = url;
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    /**
     * Project names and file paths keep their slashes, everything else in a segment is percent-encoded. URLEncoder
     * writes a space as '+', which Gitiles would read as a plus sign in a path.
     */
    private static String encodePath(String path) {
        return Arrays.stream(path.split("/", -1))
            .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
            .collect(Collectors.joining("/"));
    }
}
