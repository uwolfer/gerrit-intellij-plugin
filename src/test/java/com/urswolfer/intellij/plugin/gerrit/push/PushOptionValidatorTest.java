/*
 * Copyright 2013-2014 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.push;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PushOptionValidatorTest {

    @Test
    public void testEmptyValueIsValid() throws Exception {
        Assert.assertNull(PushOptionValidator.validateOption("Topic", ""));
        Assert.assertNull(PushOptionValidator.validateBranch("Branch", ""));
    }

    @Test
    public void testTopicWithSlashIsValid() throws Exception {
        Assert.assertNull(PushOptionValidator.validateOption("Topic", "driver/i42"));
    }

    @Test
    public void testTopicWithSpace() throws Exception {
        String error = PushOptionValidator.validateOption("Topic", "Bug xy");
        Assert.assertNotNull(error);
        Assert.assertTrue(error.startsWith("Topic must not contain spaces"), error);
    }

    @Test
    public void testTopicWithTab() throws Exception {
        // whitespace which cannot be seen in the text field is named
        String error = PushOptionValidator.validateOption("Topic", "Bug\txy");
        Assert.assertNotNull(error);
        Assert.assertTrue(error.startsWith("Topic must not contain whitespace (U+0009)"), error);
    }

    @Test
    public void testTopicWithNoBreakSpace() throws Exception {
        String error = PushOptionValidator.validateOption("Topic", "Bug\u00A0xy");
        Assert.assertNotNull(error);
        Assert.assertTrue(error.startsWith("Topic must not contain whitespace (U+00A0)"), error);
    }

    @Test
    public void testTrimRemovesWhatIsRejected() throws Exception {
        // String#trim() stops at U+0020 and would leave these to be rejected instead of trimmed away
        Assert.assertEquals(PushOptionValidator.trim("\u00A0 my-topic \u3000"), "my-topic");
        Assert.assertNull(PushOptionValidator.validateOption("Topic", PushOptionValidator.trim(" my-topic ")));
    }

    @Test
    public void testTopicWithComma() throws Exception {
        // a comma would be handled as separator between two Gerrit push options
        Assert.assertEquals(PushOptionValidator.validateOption("Topic", "bug,fix"),
            "Topic must not contain the character ','.");
    }

    @Test
    public void testTopicWithCharacterWhichIsInvalidInRefName() throws Exception {
        // characters which cannot be part of a ref name are rejected by the push target panel,
        // which validates the assembled ref
        Assert.assertNull(PushOptionValidator.validateOption("Topic", "bug~1"));
    }

    @Test
    public void testTopicWithPercentSignIsValid() throws Exception {
        // only the first percent sign separates the branch name from the push options
        Assert.assertNull(PushOptionValidator.validateOption("Topic", "50%done"));
    }

    @Test
    public void testBranchWithPercentSign() throws Exception {
        // Gerrit handles everything after the first percent sign as push options
        Assert.assertEquals(PushOptionValidator.validateBranch("Branch", "mas%ter"),
            "Branch must not contain the character '%'.");
    }

    @Test
    public void testBranchWithSlashIsValid() throws Exception {
        Assert.assertNull(PushOptionValidator.validateBranch("Branch", "release/1.0"));
    }

    @Test
    public void testBranchWithCommaIsValid() throws Exception {
        // only the push options are separated by commas; the branch name is read up to the first
        // percent sign
        Assert.assertNull(PushOptionValidator.validateBranch("Branch", "fea,ture"));
    }

    @Test
    public void testUsableAsBranchName() throws Exception {
        Assert.assertTrue(PushOptionValidator.isUsableAsBranchName(""));
        Assert.assertTrue(PushOptionValidator.isUsableAsBranchName("release/1.0"));
    }

    @Test
    public void testBranchEndingWithSlashIsNotUsable() throws Exception {
        // happens while a branch name is typed; the push target reports it
        Assert.assertFalse(PushOptionValidator.isUsableAsBranchName("release/"));
    }

    @Test
    public void testUserNameWithSpace() throws Exception {
        String error = PushOptionValidator.validateOption("Reviewer name", "John Doe");
        Assert.assertNotNull(error);
        Assert.assertTrue(error.startsWith("Reviewer name must not contain spaces"), error);
    }

    @Test
    public void testMailAddressIsValid() throws Exception {
        Assert.assertNull(PushOptionValidator.validateOption("Reviewer name", "john.doe@example.com"));
    }
}
