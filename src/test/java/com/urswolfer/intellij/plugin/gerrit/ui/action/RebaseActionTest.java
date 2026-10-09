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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class RebaseActionTest {

    @Test
    public void theTipOfTheBranchIsAnEmptyBase() {
        // not null, which Gerrit takes to mean the parent change of a stacked change
        assertEquals(RebaseAction.base(false, "ignored"), "");
    }

    @Test
    public void anotherBaseIsTrimmed() {
        assertEquals(RebaseAction.base(true, " 1234 \u00a0"), "1234");
        assertEquals(RebaseAction.base(true, "refs/changes/34/1234/1"), "refs/changes/34/1234/1");
    }
}
