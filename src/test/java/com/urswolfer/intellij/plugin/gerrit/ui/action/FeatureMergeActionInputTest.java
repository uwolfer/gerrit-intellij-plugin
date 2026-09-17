/*
 * Copyright 2013-2026 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.urswolfer.intellij.plugin.gerrit.ui.action;


import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.swing.JTextField;

public class FeatureMergeActionInputTest {

    @Test
    public void testCreateInput() {
        ChangeInput input = CreateFeatureMergeChangeAction.createInput(
                " project ", " feature/test ", " feature/test ", " main ", " Merge feature ", " topic-name ");

        Assert.assertEquals(input.project, "project");
        Assert.assertEquals(input.branch, "main");
        Assert.assertEquals(input.subject, "Merge feature");
        Assert.assertEquals(input.topic, "topic-name");
        Assert.assertTrue(input.workInProgress);
        Assert.assertEquals(input.merge.source, "refs/heads/feature/test");
        Assert.assertEquals(input.merge.sourceBranch, "refs/heads/feature/test");
        Assert.assertFalse(input.merge.allowConflicts);
    }

    @Test
    public void testCreateInputOmitsBlankTopic() {
        ChangeInput input = CreateFeatureMergeChangeAction.createInput(
                "project", "feature/test", "feature/test", "main", "Merge feature", "  ");

        Assert.assertNull(input.topic);
    }

    @Test
    public void testCreateInputCanonicalizesRemoteMergeBranches() {
        ChangeInput input = CreateFeatureMergeChangeAction.createInput(
                "project", "refs/remotes/origin/feature/test", "refs/remotes/origin/feature/test",
                "main", "Merge feature", "");

        Assert.assertEquals(input.merge.source, "refs/heads/feature/test");
        Assert.assertEquals(input.merge.sourceBranch, "refs/heads/feature/test");
    }

    @Test
    public void testDefaultSubject() {
        Assert.assertEquals(CreateFeatureMergeChangeAction.defaultSubject("refs/heads/review/jdoe/123"),
                "Merge request for review/jdoe/123");
        Assert.assertEquals(CreateFeatureMergeChangeAction.defaultSubject(""), "");
    }

    @Test
    public void testDefaultSubjectUsesFeatureBranch() {
        Assert.assertEquals(CreateFeatureMergeChangeAction.defaultSubject("refs/heads/FEATURE"),
                "Merge request for FEATURE");
    }

    @Test
    public void testReviewCheckoutNamesResolveNumericChangeIds() {
        Assert.assertEquals(FeatureMergeBranchResolver.reviewChangeNumber("review/jdoe/123").intValue(), 123);
        Assert.assertEquals(FeatureMergeBranchResolver.reviewChangeNumber("review/jdoe/123_2").intValue(), 123);
        Assert.assertEquals(FeatureMergeBranchResolver.reviewChangeNumber("review/jdoe/123-patch2").intValue(), 123);
        Assert.assertEquals(FeatureMergeBranchResolver.reviewChangeNumber("review/jdoe/123-patch2_1").intValue(), 123);
        Assert.assertNull(FeatureMergeBranchResolver.reviewChangeNumber("review/jdoe/123_2_3"));
        Assert.assertNull(FeatureMergeBranchResolver.reviewChangeNumber("review/jdoe/123-2"));
        Assert.assertNull(FeatureMergeBranchResolver.reviewChangeNumber("review/jdoe/topic-name"));
        Assert.assertNull(FeatureMergeBranchResolver.reviewChangeNumber("review/jdoe/999999999999999999999"));
    }

    @Test
    public void testRemoteBranchesAreNormalizedToHeadRefs() {
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeRemoteBranch("FEATURE", "origin"),
                "refs/heads/FEATURE");
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeRemoteBranch("origin/feature/topic", "origin"),
                "refs/heads/feature/topic");
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeRemoteBranch("refs/remotes/origin/feature/topic", "origin"),
                "refs/heads/feature/topic");
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeRemoteBranch("refs/heads/feature/topic", "origin"),
                "refs/heads/feature/topic");
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeBranch("origin/FEATURE"),
                "refs/heads/origin/FEATURE");
    }

    @Test
    public void testDefaultBranchesAndDetachedCheckoutsHaveExplicitOutcomes() {
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeHead("refs/heads/master"), "refs/heads/master");
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeHead("main"), "refs/heads/main");
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeBranch("refs/heads/"), "");
        Assert.assertEquals(FeatureMergeBranchResolver.normalizeBranch("refs/tags/v1"), "");
        Assert.assertTrue(FeatureMergeBranchResolver.isDefaultBranch("refs/heads/master", "refs/heads/master"));
        Assert.assertTrue(FeatureMergeBranchResolver.isDefaultBranch("refs/heads/main", "refs/heads/main"));
        Assert.assertFalse(FeatureMergeBranchResolver.isDefaultBranch("refs/heads/FEATURE", "refs/heads/master"));
        Assert.assertTrue(FeatureMergeBranchResolver.detachedHeadMessage().contains("detached HEAD"));
        Assert.assertTrue(FeatureMergeBranchResolver.missingUpstreamMessage().contains("no upstream"));
    }

    @Test
    public void testAutomaticSubjectUpdatesSafelyAndPreservesManualText() {
        JTextField sourceBranch = new JTextField("refs/heads/FEATURE");
        JTextField subject = new JTextField("Merge request for FEATURE");
        FeatureMergeSubjectUpdater updater = new FeatureMergeSubjectUpdater(sourceBranch, subject);

        sourceBranch.setText("refs/heads/OTHER");
        updater.run();
        Assert.assertEquals(subject.getText(), "Merge request for OTHER");

        subject.setText("Custom merge subject");
        sourceBranch.setText("refs/heads/FINAL");
        updater.run();
        Assert.assertEquals(subject.getText(), "Custom merge subject");
    }

    @Test
    public void testRefreshInput() {
        MergePatchSetInput input = RefreshFeatureMergePatchSetAction.createInput(" feature/test ");

        Assert.assertFalse(input.inheritParent);
        Assert.assertEquals(input.merge.source, "feature/test");
        Assert.assertEquals(input.merge.sourceBranch, "feature/test");
        Assert.assertFalse(input.merge.allowConflicts);
    }
}
