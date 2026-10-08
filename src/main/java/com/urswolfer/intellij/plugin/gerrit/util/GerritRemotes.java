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

import com.google.gerrit.extensions.common.DownloadSchemeInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import git4idea.GitUtil;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the remotes of a repository to the projects on the Gerrit its project talks to.
 *
 * @author Urs Wolfer
 */
public final class GerritRemotes {

    private static final String PROJECT_PLACEHOLDER = "${project}";

    private GerritRemotes() {}

    /**
     * Only remotes on the configured Gerrit lead to a project there; a repository can just as well have a mirror or
     * a fork among its remotes, or fetch from a mirror and push to Gerrit. The remote the current branch tracks goes
     * first, as the one pushed to.
     *
     * @param extraUrl another URL the Gerrit projects are served below, such as the Gitiles one, or {@code null}
     */
    @Nullable
    public static GerritProject getGerritProject(GitRepository repository, @Nullable String extraUrl) {
        Set<GitRemote> remotes = new LinkedHashSet<>();
        GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
        if (trackInfo != null) {
            remotes.add(trackInfo.getRemote());
        }
        remotes.addAll(repository.getRemotes());

        GerritProjectAccount account = GerritProjectAccount.getInstance(repository.getProject());
        Set<String> gerritUrls = new LinkedHashSet<>();
        for (String gerritUrl : new String[]{account.getHost(), account.getCloneBaseUrlOrHost(), extraUrl}) {
            if (gerritUrl != null && !gerritUrl.isEmpty()) {
                gerritUrls.add(gerritUrl);
            }
        }
        for (GitRemote remote : remotes) {
            // fetch URLs first: the tracked branch, and with it the revision a file is linked at, comes from there
            List<String> remoteUrls = new ArrayList<>(remote.getUrls());
            remoteUrls.addAll(remote.getPushUrls());
            for (String remoteUrl : remoteUrls) {
                String projectName = getProjectName(remoteUrl, gerritUrls);
                if (projectName != null) {
                    return new GerritProject(remote, projectName);
                }
            }
        }
        return null;
    }

    /**
     * The Gerrit host, the clone base URL and the Gitiles URL can share a host and still differ in their path, so a
     * remote is resolved against each one it lives on. The longest path it lives below leaves the shortest name; the
     * others leave part of that path in front of the project.
     */
    @VisibleForTesting
    @Nullable
    static String getProjectName(String remoteUrl, Collection<String> gerritUrls) {
        String url = UrlUtils.stripGitExtension(UrlUtils.normalizeScpLikeUrl(remoteUrl));
        String best = null;
        for (String gerritUrl : gerritUrls) {
            try {
                if (!UrlUtils.urlHasSameHost(url, gerritUrl)) {
                    continue;
                }
                String projectName = getProjectName(gerritUrl, null, url);
                if (projectName == null || projectName.isEmpty() || !url.endsWith(projectName)) {
                    continue;
                }
                projectName = UrlUtils.stripAuthenticationPrefix(url, projectName);
                if (best == null || projectName.length() < best.length()) {
                    best = projectName;
                }
            } catch (IllegalArgumentException e) { // a url which is not a URI does not point to Gerrit either
            }
        }
        return best;
    }

    public static List<String> getProjectNames(Project project, Collection<GitRemote> remotes) {
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        return account != null ? getProjectNames(remotes, account.host, account.cloneBaseUrl)
            : getProjectNames(remotes, "", "");
    }

    /**
     * A remote on another host, such as a mirror on GitHub, would add its path as a project of the same name. Such
     * remotes only count when no remote is on the Gerrit host, as one reached through an SSH alias looks the same.
     * A remote on the host is resolved like the other remotes there, against the host and the clone base URL alike.
     */
    @VisibleForTesting
    static List<String> getProjectNames(Collection<GitRemote> remotes, String host, @Nullable String cloneBaseUrl) {
        List<String> gerritUrls = new ArrayList<>();
        for (String gerritUrl : new String[]{host, cloneBaseUrl}) {
            if (gerritUrl != null && !gerritUrl.isEmpty()) {
                gerritUrls.add(gerritUrl);
            }
        }
        List<String> onGerritHost = new ArrayList<>();
        List<String> elsewhere = new ArrayList<>();
        for (GitRemote remote : remotes) {
            for (String remoteUrl : remote.getUrls()) {
                if (isOnHost(remoteUrl, host) || isOnHost(remoteUrl, cloneBaseUrl)) {
                    addProjectName(onGerritHost, getProjectName(remoteUrl, gerritUrls));
                } else {
                    addProjectName(elsewhere, getRemoteProjectName(remoteUrl, host, cloneBaseUrl));
                }
            }
            // a remote can fetch from a mirror and push to Gerrit
            for (String pushUrl : remote.getPushUrls()) {
                if (isOnHost(pushUrl, host) || isOnHost(pushUrl, cloneBaseUrl)) {
                    addProjectName(onGerritHost, getProjectName(pushUrl, gerritUrls));
                }
            }
        }
        return onGerritHost.isEmpty() ? elsewhere : onGerritHost;
    }

    private static void addProjectName(List<String> projectNames, @Nullable String projectName) {
        if (projectName != null) {
            projectNames.add(projectName);
        }
    }

    private static boolean isOnHost(String remoteUrl, @Nullable String hostUrl) {
        if (hostUrl == null || hostUrl.isEmpty()) {
            return false;
        }
        try {
            return UrlUtils.urlHasSameHost(remoteUrl, hostUrl);
        } catch (IllegalArgumentException e) { // a url which is not a URI is on no host
            return false;
        }
    }

    /**
     * @return the project a remote url on another host would be on the configured Gerrit, or {@code null}
     */
    @Nullable
    private static String getRemoteProjectName(String remoteUrl, String host, @Nullable String cloneBaseUrl) {
        String strippedUrl = UrlUtils.stripGitExtension(remoteUrl);
        String projectName;
        try {
            projectName = getProjectName(host, cloneBaseUrl, strippedUrl);
        } catch (IllegalArgumentException e) { // java.net.URI rejects some remotes git accepts, such as "/repos/[old]"
            return null;
        }
        if (projectName == null || projectName.isEmpty() || !strippedUrl.endsWith(projectName)) {
            return null;
        }
        return UrlUtils.stripAuthenticationPrefix(strippedUrl, projectName);
    }

    /**
     * @return the part of the first http download scheme in front of the project, {@code null} if there is none.
     *         A scheme which needs no login is preferred, as its url is the one to type; the {@code /a} of the
     *         authenticated one is only the prefix Gerrit serves its authenticated endpoints under. Gerrit puts the
     *         name of the requesting user into that url, which the clone urls must not carry.
     */
    @Nullable
    public static String getCloneBaseUrl(@Nullable Map<String, DownloadSchemeInfo> schemes) {
        if (schemes == null) {
            return null;
        }
        String authenticated = null;
        for (DownloadSchemeInfo scheme : schemes.values()) {
            if (scheme.url == null || !scheme.url.startsWith("http") || !scheme.url.contains(PROJECT_PLACEHOLDER)) {
                continue;
            }
            String base = StringUtil.trimEnd(scheme.url.substring(0, scheme.url.indexOf(PROJECT_PLACEHOLDER)), "/")
                .replaceFirst("^(https?://)[^/]*@", "$1");
            if (!Boolean.TRUE.equals(scheme.isAuthRequired)) {
                return base;
            }
            if (authenticated == null) {
                authenticated = StringUtil.trimEnd(base, "/a");
            }
        }
        return authenticated;
    }

    public static String getProjectName(String gerritUrl, String gerritCloneBaseUrl, String url) {
        String baseUrl = gerritCloneBaseUrl == null || gerritCloneBaseUrl.isEmpty() ? gerritUrl : gerritCloneBaseUrl;
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        String basePath = UrlUtils.createUriFromGitConfigString(baseUrl).getPath();
        String path = UrlUtils.createUriFromGitConfigString(url).getPath();

        if (path.length() >= basePath.length() && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }

        path = UrlUtils.stripGitExtension(path);

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        // gerrit project names usually don't start with a slash
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return path;
    }

    public static final class GerritProject {
        public final GitRemote remote;
        public final String name;

        GerritProject(GitRemote remote, String name) {
            this.remote = remote;
            this.name = name;
        }
    }
}
