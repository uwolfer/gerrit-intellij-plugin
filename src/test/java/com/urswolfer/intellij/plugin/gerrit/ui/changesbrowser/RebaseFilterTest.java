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

package com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import org.easymock.EasyMock;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Predicate;

/**
 * @author Urs Wolfer
 */
public class RebaseFilterTest {
    private static final Predicate<Change> FILTER =
        RebaseFilter.keepListed(new HashSet<>(Arrays.asList("a.txt", "dir/b.txt", "old.txt", "new.txt")), "/repo");

    @Test
    public void testKeepsListedFile() {
        Assert.assertTrue(FILTER.test(modified("/repo/a.txt")));
        Assert.assertTrue(FILTER.test(modified("/repo/dir/b.txt")));
    }

    @Test
    public void testDropsFileNotListed() {
        Assert.assertFalse(FILTER.test(modified("/repo/rebase.txt")));
        Assert.assertFalse(FILTER.test(modified("/repo/b.txt")));
    }

    @Test
    public void testKeepsAddedAndDeletedFile() {
        Assert.assertTrue(FILTER.test(change(null, revision("/repo/a.txt"))));
        Assert.assertTrue(FILTER.test(change(revision("/repo/a.txt"), null)));
    }

    @Test
    public void testKeepsRenameListedUnderEitherPath() {
        Assert.assertTrue(FILTER.test(change(revision("/repo/old.txt"), revision("/repo/renamed.txt"))));
        Assert.assertTrue(FILTER.test(change(revision("/repo/renamed.txt"), revision("/repo/new.txt"))));
    }

    @Test
    public void testKeepsFileOutsideRepository() {
        Assert.assertTrue(FILTER.test(modified("/other/rebase.txt")));
        Assert.assertTrue(FILTER.test(modified("/repoa.txt")));
    }

    @Test
    public void testAcceptsRootWithTrailingSlash() {
        Predicate<Change> filter = RebaseFilter.keepListed(Collections.singleton("a.txt"), "/repo/");
        Assert.assertTrue(filter.test(modified("/repo/a.txt")));
        Assert.assertFalse(filter.test(modified("/repo/b.txt")));
    }

    private static Change modified(String path) {
        return change(revision(path), revision(path));
    }

    // the constructor of Change needs a running application
    private static Change change(ContentRevision before, ContentRevision after) {
        Change change = EasyMock.createMock(Change.class);
        EasyMock.expect(change.getBeforeRevision()).andReturn(before).anyTimes();
        EasyMock.expect(change.getAfterRevision()).andReturn(after).anyTimes();
        EasyMock.replay(change);
        return change;
    }

    private static ContentRevision revision(String path) {
        FilePath filePath = EasyMock.createMock(FilePath.class);
        EasyMock.expect(filePath.getPath()).andReturn(path).anyTimes();
        ContentRevision revision = EasyMock.createMock(ContentRevision.class);
        EasyMock.expect(revision.getFile()).andReturn(filePath).anyTimes();
        EasyMock.replay(filePath, revision);
        return revision;
    }
}
