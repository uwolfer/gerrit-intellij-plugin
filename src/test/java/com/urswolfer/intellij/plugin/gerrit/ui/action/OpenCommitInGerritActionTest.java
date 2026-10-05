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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author Urs Wolfer
 */
public class OpenCommitInGerritActionTest {
    private static final String HASH_1 = "1111111111111111111111111111111111111111";
    private static final String HASH_2 = "2222222222222222222222222222222222222222";

    @Test
    public void testSingleCommit() {
        Assert.assertEquals(OpenCommitInGerritAction.getQuery(Collections.singletonList(HASH_1)), "commit:" + HASH_1);
    }

    @Test
    public void testCommits() {
        Assert.assertEquals(OpenCommitInGerritAction.getQuery(Arrays.asList(HASH_1, HASH_2, HASH_1)),
            "commit:" + HASH_1 + " OR commit:" + HASH_2);
    }
}
