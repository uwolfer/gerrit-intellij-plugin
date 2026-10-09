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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RevertActionTest {
    @Test
    public void messageFollowsGerritsWebUi() {
        assertEquals(RevertAction.defaultMessage("Add hello", "abc123"),
            "Revert \"Add hello\"\n\nThis reverts commit abc123.\n\nReason for revert: <INSERT REASONING HERE>\n");
        assertTrue(RevertAction.defaultMessage("Revert \"Add hello\"", "abc").startsWith("Revert^2 \"Add hello\"\n"));
        assertTrue(RevertAction.defaultMessage("Revert^2 \"Add hello\"", "abc").startsWith("Revert^3 \"Add hello\"\n"));
        assertFalse(RevertAction.defaultMessage("Add hello", null).contains("This reverts"));
    }

    @Test
    public void messageSurvivesOddSubjects() {
        assertTrue(RevertAction.defaultMessage(null, "abc").startsWith("Revert \"\"\n"));
        // a count too large to parse is no count: the subject is quoted as it is
        assertTrue(RevertAction.defaultMessage("Revert^99999999999 \"x\"", "abc")
            .startsWith("Revert \"Revert^99999999999 \"x\"\"\n"));
    }

    @Test
    public void queryOfTheRevertingChangeDecodesItsId() {
        assertEquals(RevertAction.decoded("a%2Fb~master~I123"), "a/b~master~I123");
        assertEquals(RevertAction.decoded("demo~7"), "demo~7");
        assertEquals(RevertAction.decoded("100%"), "100%");
    }
}
