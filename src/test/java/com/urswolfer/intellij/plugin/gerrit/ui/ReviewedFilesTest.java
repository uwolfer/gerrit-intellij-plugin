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

package com.urswolfer.intellij.plugin.gerrit.ui;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class ReviewedFilesTest {

    private static ReviewedFiles loaded(String change, String revision, String... reviewed) {
        ReviewedFiles files = new ReviewedFiles();
        files.startLoading(change, revision);
        Assert.assertTrue(files.loaded(change, revision, Set.of(reviewed)));
        return files;
    }

    @Test
    public void testLoadedFilesAreReviewed() {
        ReviewedFiles files = loaded("c1", "r1", "a.txt");

        Assert.assertTrue(files.isReviewed("a.txt"));
        Assert.assertFalse(files.isReviewed("b.txt"));
    }

    @Test
    public void testMarkOnSelectedPatchSet() {
        ReviewedFiles files = loaded("c1", "r1");

        Assert.assertTrue(files.mark("c1", "r1", "a.txt", true));
        Assert.assertTrue(files.isReviewed("a.txt"));
        Assert.assertTrue(files.mark("c1", "r1", "a.txt", false));
        Assert.assertFalse(files.isReviewed("a.txt"));
    }

    @Test
    public void testMarkOfOtherPatchSetOrChangeIsDropped() {
        ReviewedFiles files = loaded("c1", "r1");

        Assert.assertFalse(files.mark("c1", "r0", "a.txt", true));
        Assert.assertFalse(files.mark("c2", "r1", "a.txt", true));
        Assert.assertFalse(files.mark(null, null, "a.txt", true));
        Assert.assertFalse(files.isReviewed("a.txt"));
    }

    @Test
    public void testMarkAfterSelectionMovedOnIsDropped() {
        ReviewedFiles files = loaded("c1", "r1");
        files.startLoading("c1", "r2");

        Assert.assertFalse(files.mark("c1", "r1", "a.txt", true));
        Assert.assertFalse(files.isReviewed("a.txt"));
    }

    @Test
    public void testNothingIsSelectedBeforeLoadingOrAfterClear() {
        ReviewedFiles files = new ReviewedFiles();
        Assert.assertFalse(files.mark("c1", "r1", "a.txt", true));

        files = loaded("c1", "r1", "a.txt");
        files.clear();
        Assert.assertFalse(files.isReviewed("a.txt"));
        Assert.assertFalse(files.mark("c1", "r1", "a.txt", true));
        Assert.assertFalse(files.isSelected(null, null));
    }

    @Test
    public void testLoadForOtherPatchSetIsDropped() {
        ReviewedFiles files = new ReviewedFiles();
        files.startLoading("c1", "r2");

        Assert.assertFalse(files.loaded("c1", "r1", Set.of("a.txt")));
        Assert.assertFalse(files.isReviewed("a.txt"));
    }

    @Test
    public void testLoadIsTakenOnce() {
        ReviewedFiles files = loaded("c1", "r1", "a.txt");

        Assert.assertFalse(files.loaded("c1", "r1", Set.of("b.txt")));
        Assert.assertTrue(files.isReviewed("a.txt"));
        Assert.assertFalse(files.isReviewed("b.txt"));
    }

    @Test
    public void testMarkWhileLoadingSurvivesAnOlderAnswer() {
        ReviewedFiles files = new ReviewedFiles();
        files.startLoading("c1", "r1");
        files.mark("c1", "r1", "a.txt", true);
        files.mark("c1", "r1", "b.txt", false);

        Assert.assertTrue(files.loaded("c1", "r1", Set.of("b.txt")));
        Assert.assertTrue(files.isReviewed("a.txt"));
        Assert.assertFalse(files.isReviewed("b.txt"));
    }

    @Test
    public void testReloadOfSamePatchSetKeepsTicks() {
        ReviewedFiles files = loaded("c1", "r1", "a.txt");
        files.startLoading("c1", "r1");

        Assert.assertTrue(files.isReviewed("a.txt"));
    }

    @Test
    public void testOtherPatchSetStartsEmpty() {
        ReviewedFiles files = loaded("c1", "r1", "a.txt");
        files.startLoading("c1", "r2");

        Assert.assertFalse(files.isReviewed("a.txt"));
    }

    @Test
    public void testToggleMarksUnlessAllAreReviewed() {
        ReviewedFiles files = loaded("c1", "r1", "a.txt");

        Assert.assertTrue(files.toggleTarget(Arrays.asList("a.txt", "b.txt")));
        Assert.assertTrue(files.toggleTarget(Collections.singletonList("b.txt")));
        Assert.assertFalse(files.toggleTarget(Collections.singletonList("a.txt")));
    }

    @Test
    public void testFailedLoadKeepsTheTicksAndTakesNoLaterAnswer() {
        ReviewedFiles files = loaded("c1", "r1", "a.txt");
        files.startLoading("c1", "r1");
        files.loadFailed("c1", "r1");

        Assert.assertTrue(files.isReviewed("a.txt"));
        Assert.assertFalse(files.loaded("c1", "r1", Set.of()));
    }
}
