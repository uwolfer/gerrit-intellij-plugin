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

/**
 * @author Urs Wolfer
 */
public class UrlUtils {

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

    public static String stripGitExtension(String url) {
        return url.replace(".git", ""); // some repositories end their name with ".git"
    }
}
