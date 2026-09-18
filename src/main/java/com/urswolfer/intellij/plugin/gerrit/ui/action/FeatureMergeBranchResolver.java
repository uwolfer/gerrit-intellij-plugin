/*
 * Copyright 2013-2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.urswolfer.intellij.plugin.gerrit.ui.action;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Branch-name rules used by the feature merge change action.
 *
 * Keeping these rules independent from the IntelliJ Git model makes the
 * important cases easy to verify without constructing IDE repository objects.
 */
final class FeatureMergeBranchResolver {
    static final String REFS_HEADS_PREFIX = "refs/heads/";

    private static final String REFS_REMOTES_PREFIX = "refs/remotes/";
    private static final Pattern REVIEW_CHANGE = Pattern.compile(
            "^review/[^/]+/(\\d+)(?:-patch\\d+)?(?:_\\d+)?$");

    private FeatureMergeBranchResolver() {
    }

    /**
     * Converts a Git remote branch name to the ref accepted by Gerrit.
     *
     * This method is for local-operation and fully-qualified forms. A value
     * returned by getNameForRemoteOperations() must be passed to normalizeBranch
     * so a legitimate branch whose name starts with the remote name is kept.
     */
    static String normalizeRemoteBranch(@Nullable String branchName, @Nullable String remoteName) {
        String branch = trimToEmpty(branchName);
        if (branch.isEmpty() || "HEAD".equals(branch)) {
            return "";
        }

        if (branch.startsWith(REFS_HEADS_PREFIX)) {
            return branch.length() == REFS_HEADS_PREFIX.length() ? "" : branch;
        }

        if (branch.startsWith(REFS_REMOTES_PREFIX)) {
            String remoteBranch = branch.substring(REFS_REMOTES_PREFIX.length());
            int separator = remoteBranch.indexOf('/');
            if (separator < 1 || separator == remoteBranch.length() - 1) {
                return "";
            }
            return REFS_HEADS_PREFIX + remoteBranch.substring(separator + 1);
        }

        if (remoteName != null && !remoteName.trim().isEmpty()) {
            String remotePrefix = remoteName.trim() + "/";
            if (branch.startsWith(remotePrefix) && branch.length() > remotePrefix.length()) {
                branch = branch.substring(remotePrefix.length());
            }
        }

        if (branch.startsWith("refs/")) {
            return "";
        }
        return REFS_HEADS_PREFIX + branch;
    }

    /** Converts a Gerrit branch or an explicitly entered branch to a head ref. */
    static String normalizeBranch(@Nullable String branchName) {
        String branch = trimToEmpty(branchName);
        if (branch.isEmpty() || "HEAD".equals(branch)) {
            return "";
        }
        if (branch.startsWith(REFS_HEADS_PREFIX)) {
            return branch.length() == REFS_HEADS_PREFIX.length() ? "" : branch;
        }
        if (branch.startsWith(REFS_REMOTES_PREFIX)) {
            String remoteBranch = branch.substring(REFS_REMOTES_PREFIX.length());
            int separator = remoteBranch.indexOf('/');
            if (separator < 1 || separator == remoteBranch.length() - 1) {
                return "";
            }
            return REFS_HEADS_PREFIX + remoteBranch.substring(separator + 1);
        }
        if (branch.startsWith("refs/")) {
            return "";
        }
        return REFS_HEADS_PREFIX + branch;
    }

    /** Gerrit's project HEAD is expected to resolve to a branch ref. */
    static String normalizeHead(@Nullable String head) {
        return normalizeBranch(head);
    }

    @Nullable
    static Integer reviewChangeNumber(@Nullable String branchName) {
        Matcher matcher = REVIEW_CHANGE.matcher(trimToEmpty(branchName));
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isDefaultBranch(String sourceBranch, String targetBranch) {
        return !sourceBranch.isEmpty() && sourceBranch.equals(targetBranch);
    }

    static String shortBranchName(String branchName) {
        String branch = trimToEmpty(branchName);
        return branch.startsWith(REFS_HEADS_PREFIX)
                ? branch.substring(REFS_HEADS_PREFIX.length())
                : branch;
    }

    static String defaultSubject(String sourceBranch) {
        String normalizedSource = normalizeBranch(sourceBranch);
        return normalizedSource.isEmpty()
                ? ""
                : "Merge request for " + shortBranchName(normalizedSource);
    }

    static String defaultBranchMessage(String targetBranch) {
        return "The current source is the default branch '" + shortBranchName(targetBranch)
                + "'. Change the target branch if you intentionally want to merge it elsewhere.";
    }

    static String detachedHeadMessage() {
        return "This checkout is in detached HEAD state. Check out a feature branch to create a merge request.";
    }

    static String missingUpstreamMessage() {
        return "This checkout has no upstream branch. Select the remote feature branch to use as the merge source.";
    }

    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
