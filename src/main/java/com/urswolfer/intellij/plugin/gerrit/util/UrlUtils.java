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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Urs Wolfer
 */
public class UrlUtils {

    private static final String GIT_EXTENSION = ".git";

    // git's scp-like syntax "[user@]host:path"; a single letter before the colon is a Windows drive, and digits up to
    // the next slash are kept as a port, which is how createUriFromGitConfigString has always read "host:29418/x"
    private static final Pattern SCP_LIKE_URL = Pattern.compile("^((?:[^@/:]+@)?[^@/:\\\\]{2,}):(?!\\d+(?:/|$))(.*)$");

    private UrlUtils() {}

    public static boolean urlHasSameHost(String url, String hostUrl) {
        String host = URI.create(hostUrl).getHost();
        String repositoryHost = UrlUtils.createUriFromGitConfigString(url).getHost();
        return repositoryHost != null && repositoryHost.equalsIgnoreCase(host); // host names are case insensitive
    }

    /**
     * @return the url as typed, without a trailing slash and with https where it names no scheme
     */
    public static String normalizeTypedUrl(String url) {
        String text = url.trim();
        if (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (!text.isEmpty() && !text.contains("://")) {
            text = "https://" + text;
        }
        return text;
    }

    /**
     * @return whether the two urls are of the same Gerrit instance: the same host, port and path, whichever scheme,
     *         as http and https on one host are the same server; a url without a scheme, as typed, counts as https
     */
    public static boolean isSameInstance(String url, String otherUrl) {
        try {
            URI uri = URI.create(normalizeTypedUrl(url));
            URI other = URI.create(normalizeTypedUrl(otherUrl));
            return uri.getHost() != null && uri.getHost().equalsIgnoreCase(other.getHost())
                && portOrDefault(uri) == portOrDefault(other)
                && trimSlashes(uri.getPath()).equals(trimSlashes(other.getPath()));
        } catch (IllegalArgumentException e) { // not a url, so no instance either
            return false;
        }
    }

    /**
     * @return the port, or -1 for either default port, which is all a url whose scheme is not to be told can say
     */
    public static int portOrDefault(URI uri) {
        return uri.getPort() == 80 || uri.getPort() == 443 ? -1 : uri.getPort();
    }

    private static String trimSlashes(String path) {
        String trimmed = path == null ? "" : path;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Rewrites git's scp-like "user@host:path" to "ssh://user@host/path", which {@link java.net.URI} can take apart.
     * Anything else is returned unchanged.
     */
    public static String normalizeScpLikeUrl(String url) {
        if (url.contains("://")) {
            return url;
        }
        Matcher matcher = SCP_LIKE_URL.matcher(url);
        if (!matcher.matches()) {
            return url;
        }
        String path = matcher.group(2);
        return "ssh://" + matcher.group(1) + (path.startsWith("/") ? "" : "/") + path;
    }

    public static URI createUriFromGitConfigString(String gitConfigUrl) {
        gitConfigUrl = normalizeScpLikeUrl(gitConfigUrl);
        if (!gitConfigUrl.contains("://")) { // some urls do not contain a protocol; just add something so it will not fail with parsing
            gitConfigUrl = "git://" + gitConfigUrl;
        }
        gitConfigUrl = gitConfigUrl.replace(" ", "%20");
        gitConfigUrl = gitConfigUrl.replace("\\", "/");
        return URI.create(gitConfigUrl);
    }

    public static String stripAuthenticationPrefix(String url, String projectName) {
        // over HTTP Gerrit reserves "/a/" for authenticated access, so there it is never part of a project name
        String lowerCaseUrl = url.toLowerCase(Locale.ROOT);
        boolean http = lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://");
        return http && projectName.startsWith("a/") ? projectName.substring(2) : projectName;
    }

    /**
     * Removes trailing slashes and the ".git" some repositories end their name with. Only at the end: any other
     * ".git" belongs to a host or project name (e.g. "gerrit.gitlab.example.com" or "my.github-actions") and must
     * be kept.
     */
    public static String stripGitExtension(String url) {
        String strippedUrl = url;
        while (strippedUrl.endsWith("/")) {
            strippedUrl = strippedUrl.substring(0, strippedUrl.length() - 1);
        }
        if (strippedUrl.endsWith(GIT_EXTENSION)) {
            return strippedUrl.substring(0, strippedUrl.length() - GIT_EXTENSION.length());
        }
        return strippedUrl;
    }

    public static String encodePatchSetDescription(String text) {
        // According to https://gerrit-review.googlesource.com/Documentation/user-upload.html#patch_set_description,
        // at least the chars %^@.~-+_:/! must be percent-encoded and the space character must be encoded as '+'
        // the encoded description ends up in a ref name, so nothing URLEncoder leaves as it is may stay behind
        return URLEncoder.encode(text, StandardCharsets.UTF_8)
            .replace(".", "%2E")
            .replace("-", "%2D")
            .replace("_", "%5F")
            .replace("*", "%2A");
    }
}
