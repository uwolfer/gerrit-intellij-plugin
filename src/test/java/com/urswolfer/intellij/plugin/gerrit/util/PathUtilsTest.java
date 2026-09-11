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

import org.testng.Assert;
import org.testng.annotations.Test;

public class PathUtilsTest {

    @Test
    public void testPathInsideRepositoryRoot() throws Exception {
        Assert.assertFalse(PathUtils.leavesRepositoryRoot("src/main/java/Foo.java"));
        Assert.assertFalse(PathUtils.leavesRepositoryRoot("src\\main\\java\\Foo.java"));
        Assert.assertFalse(PathUtils.leavesRepositoryRoot("Foo.java"));
        Assert.assertFalse(PathUtils.leavesRepositoryRoot("..foo/Foo.java"));
    }

    @Test
    public void testPathOutsideRepositoryRoot() throws Exception {
        Assert.assertTrue(PathUtils.leavesRepositoryRoot("../other-repository/Foo.java"));
        Assert.assertTrue(PathUtils.leavesRepositoryRoot("..\\other-repository\\Foo.java"));
        Assert.assertTrue(PathUtils.leavesRepositoryRoot("../../Foo.java"));
        Assert.assertTrue(PathUtils.leavesRepositoryRoot(".."));
        // conservative: a ".." segment anywhere makes the plugin fall back to the absolute path
        Assert.assertTrue(PathUtils.leavesRepositoryRoot("src/../main/Foo.java"));
    }
}
