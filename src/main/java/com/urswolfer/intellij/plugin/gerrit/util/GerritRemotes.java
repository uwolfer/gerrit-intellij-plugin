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

import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
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
import java.util.Set;

/**
 * Finds the remote of a repository which leads to a project on the configured Gerrit.
 *
 * @author Urs Wolfer
 */
public final class GerritRemotes {

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

        GerritSettings settings = GerritSettings.getInstance();
        Set<String> gerritUrls = new LinkedHashSet<>();
        for (String gerritUrl : new String[]{settings.getHost(), settings.getCloneBaseUrlOrHost(), extraUrl}) {
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
                String projectName = GerritUtil.getProjectName(gerritUrl, null, url);
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

    public static final class GerritProject {
        public final GitRemote remote;
        public final String name;

        GerritProject(GitRemote remote, String name) {
            this.remote = remote;
            this.name = name;
        }
    }
}
