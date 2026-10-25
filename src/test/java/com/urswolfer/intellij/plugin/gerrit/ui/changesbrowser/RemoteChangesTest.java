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

import com.google.gerrit.extensions.common.FileInfo;
import com.intellij.openapi.util.Pair;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RemoteChangesTest {

    @Test
    public void testModifiedFileOnBothSides() {
        Assert.assertEquals(RemoteChanges.paths(Collections.singletonMap("dir/a.txt", file(null, null))),
            Collections.singletonList(Pair.create("dir/a.txt", "dir/a.txt")));
        Assert.assertEquals(RemoteChanges.paths(Collections.singletonMap("a.txt", file('W', null))),
            Collections.singletonList(Pair.create("a.txt", "a.txt")));
    }

    @Test
    public void testAddedAndDeletedFileOnOneSide() {
        Map<String, FileInfo> files = new LinkedHashMap<>();
        files.put("/COMMIT_MSG", file('A', null));
        files.put("gone.txt", file('D', null));
        Assert.assertEquals(RemoteChanges.paths(files),
            Arrays.asList(Pair.create(null, "/COMMIT_MSG"), Pair.create("gone.txt", null)));
    }

    @Test
    public void testRenamedAndCopiedFileUnderOldPathOnBase() {
        Map<String, FileInfo> files = new LinkedHashMap<>();
        files.put("new.txt", file('R', "old.txt"));
        files.put("copy.txt", file('C', "source.txt"));
        Assert.assertEquals(RemoteChanges.paths(files),
            Arrays.asList(Pair.create("old.txt", "new.txt"), Pair.create("source.txt", "copy.txt")));
    }

    private static FileInfo file(Character status, String oldPath) {
        FileInfo file = new FileInfo();
        file.status = status;
        file.oldPath = oldPath;
        return file;
    }
}
