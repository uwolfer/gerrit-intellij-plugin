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

import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.ReviewerState;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MergeChangeReviewersTest {

    @Test
    public void testInputsSplitOnCommaAndKeepBlanksInNames() {
        List<ReviewerInput> inputs = MergeChangeReviewers.inputs(" jdoe ,Jane Doe <jane@example.com>,, ", "");

        Assert.assertEquals(inputs.size(), 2);
        Assert.assertEquals(inputs.get(0).reviewer, "jdoe");
        Assert.assertEquals(inputs.get(1).reviewer, "Jane Doe <jane@example.com>");
        Assert.assertEquals(inputs.get(0).state, ReviewerState.REVIEWER);
    }

    @Test
    public void testInputsTrimPastedWhitespace() {
        List<ReviewerInput> inputs = MergeChangeReviewers.inputs(" jdoe ", "");

        Assert.assertEquals(inputs.get(0).reviewer, "jdoe");
    }

    @Test
    public void testInputsCcsAfterReviewersWithoutDuplicates() {
        List<ReviewerInput> inputs = MergeChangeReviewers.inputs("a,b,a", "c,b");

        Assert.assertEquals(inputs.size(), 3);
        Assert.assertEquals(inputs.get(2).reviewer, "c");
        Assert.assertEquals(inputs.get(2).state, ReviewerState.CC);
        Assert.assertEquals(inputs.get(1).state, ReviewerState.REVIEWER);
    }

    @Test
    public void testInputsEmpty() {
        Assert.assertTrue(MergeChangeReviewers.inputs("", "  ").isEmpty());
    }

    @Test
    public void testCreatedTextWithoutFailures() {
        Assert.assertEquals(MergeChangeReviewers.createdText("7", "A <b>", Collections.<String, String>emptyMap()),
                "Created change 7: A &lt;b&gt;");
    }

    @Test
    public void testCreatedTextNamesWhoCouldNotBeAdded() {
        Map<String, String> failures = new LinkedHashMap<>();
        failures.put("nobody", "Account not found");
        failures.put("<x>", "no permission");

        String text = MergeChangeReviewers.createdText("7", "Merge", failures);

        Assert.assertTrue(text.startsWith("Created change 7: Merge<br>"), text);
        Assert.assertTrue(text.contains("nobody: Account not found"), text);
        Assert.assertTrue(text.contains("&lt;x&gt;: no permission"), text);
    }

    @Test
    public void testReasonIsTheErrorOfGerritsAnswer() {
        Assert.assertEquals(MergeChangeReviewers.reason("Request not successful. Message: Bad Request. Status-Code: 400. "
                + "Content: )]}'\n{\"input\":\"x\",\"error\":\"Account 'x' not found\\nx does not identify a registered user\"}."),
                "Account 'x' not found. x does not identify a registered user");
        Assert.assertEquals(MergeChangeReviewers.reason("Content: {\"error\":\"No such account\"}. {more}"),
                "No such account");
        Assert.assertEquals(MergeChangeReviewers.reason("Connection refused"), "Connection refused");
        Assert.assertEquals(MergeChangeReviewers.reason("broken {json"), "broken {json");
    }
}
