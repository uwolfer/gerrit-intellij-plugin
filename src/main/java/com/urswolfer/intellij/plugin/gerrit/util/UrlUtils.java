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

package com.urswolfer.intellij.plugin.gerrit.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @author Urs Wolfer
 */
public class UrlUtils {

    private static final String GIT_EXTENSION = ".git";

    private UrlUtils() {}

    public static boolean urlHasSameHost(String url, String hostUrl) {
        String host = URI.create(hostUrl).getHost();
        String repositoryHost = UrlUtils.createUriFromGitConfigString(url).getHost();
        return repositoryHost != null && repositoryHost.equals(host);
    }

    public static URI createUriFromGitConfigString(String gitConfigUrl) {
        if (!gitConfigUrl.contains("://")) { // some urls do not contain a protocol; just add something so it will not fail with parsing
            gitConfigUrl = "git://" + gitConfigUrl;
        }
        gitConfigUrl = gitConfigUrl.replace(" ", "%20");
        gitConfigUrl = gitConfigUrl.replace("\\", "/");
        return URI.create(gitConfigUrl);
    }

    /**
     * Removes the ".git" some repositories end their name with. Only at the end: any other occurrence belongs to a
     * host or project name (e.g. "gerrit.gitlab.example.com" or "my.github-actions") and must be kept.
     */
    public static String stripGitExtension(String url) {
        String strippedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        if (strippedUrl.endsWith(GIT_EXTENSION)) {
            return strippedUrl.substring(0, strippedUrl.length() - GIT_EXTENSION.length());
        }
        return url;
    }

    public static String encodePatchSetDescription(String text) {
        // According to https://gerrit-review.googlesource.com/Documentation/user-upload.html#patch_set_description,
        // at least the chars %^@.~-+_:/! must be percent-encoded and the space character must be encoded as '+'
        return URLEncoder.encode(text, StandardCharsets.UTF_8)
            .replace(".", "%2E")
            .replace("-", "%2D")
            .replace("_", "%5F");
    }
}
