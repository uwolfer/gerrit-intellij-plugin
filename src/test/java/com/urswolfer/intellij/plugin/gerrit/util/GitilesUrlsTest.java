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

/**
 * @author Urs Wolfer
 */
public class GitilesUrlsTest {

    private static final String BASE = "https://gerrit.example.com/plugins/gitiles";
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    @Test
    public void testBaseUrlDefaultsToThePluginOnTheGerritHost() {
        Assert.assertEquals(GitilesUrls.getBaseUrl("", "https://gerrit.example.com"), BASE);
        Assert.assertEquals(GitilesUrls.getBaseUrl(null, "https://gerrit.example.com/"), BASE);
        Assert.assertEquals(GitilesUrls.getBaseUrl("", "https://example.com/r"), "https://example.com/r/plugins/gitiles");
    }

    @Test
    public void testConfiguredBaseUrlWins() {
        Assert.assertEquals(GitilesUrls.getBaseUrl(" https://example.googlesource.com/ ", "https://example-review.googlesource.com"),
            "https://example.googlesource.com");
    }

    @Test
    public void testNoBaseUrlWithoutAnyUrl() {
        Assert.assertEquals(GitilesUrls.getBaseUrl("", ""), "");
        Assert.assertEquals(GitilesUrls.getBaseUrl(null, null), "");
    }

    @Test
    public void testCommitUrl() {
        Assert.assertEquals(GitilesUrls.getCommitUrl(BASE + "/", "tools/gerrit", SHA),
            BASE + "/tools/gerrit/+/" + SHA);
    }

    @Test
    public void testFileUrl() {
        Assert.assertEquals(GitilesUrls.getFileUrl(BASE, "project", SHA, "src/Main.java", null),
            BASE + "/project/+/" + SHA + "/src/Main.java");
    }

    @Test
    public void testFileUrlWithLine() {
        Assert.assertEquals(GitilesUrls.getFileUrl(BASE, "project", SHA, "src/Main.java", 42),
            BASE + "/project/+/" + SHA + "/src/Main.java#42");
    }

    @Test
    public void testRootDirectoryKeepsTheTrailingSlash() {
        Assert.assertEquals(GitilesUrls.getFileUrl(BASE, "project", SHA, "", null),
            BASE + "/project/+/" + SHA + "/");
    }

    @Test
    public void testPathSegmentsAreEncoded() {
        Assert.assertEquals(GitilesUrls.getFileUrl(BASE, "my project", SHA, "docs/a b+c#d.md", null),
            BASE + "/my%20project/+/" + SHA + "/docs/a%20b%2Bc%23d.md");
    }
}
