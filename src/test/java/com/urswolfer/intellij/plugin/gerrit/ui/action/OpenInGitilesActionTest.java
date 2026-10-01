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
public class OpenInGitilesActionTest {

    @Test
    public void testRemoteBelowTheGerritPathIgnoresAnSshCloneBase() {
        Assert.assertEquals(OpenInGitilesAction.getProjectName("https://host/r/project",
            Arrays.asList("https://host/r", "ssh://host:29418", "https://host/r/plugins/gitiles")), "project");
    }

    @Test
    public void testRemoteBelowTheCloneBasePath() {
        Assert.assertEquals(OpenInGitilesAction.getProjectName("https://host/git/team/app.git",
            Arrays.asList("https://host", "https://host/git")), "team/app");
    }

    @Test
    public void testSshRemote() {
        Assert.assertEquals(OpenInGitilesAction.getProjectName("ssh://jdoe@host:29418/team/app",
            Arrays.asList("https://host/r", "ssh://host:29418")), "team/app");
        Assert.assertEquals(OpenInGitilesAction.getProjectName("jdoe@host:team/app",
            Collections.singletonList("https://host")), "team/app");
    }

    @Test
    public void testAuthenticatedHttpRemote() {
        Assert.assertEquals(OpenInGitilesAction.getProjectName("https://host/r/a/team/app",
            Collections.singletonList("https://host/r")), "team/app");
    }

    @Test
    public void testRemoteOnAnotherHost() {
        Assert.assertNull(OpenInGitilesAction.getProjectName("https://github.com/team/app",
            Collections.singletonList("https://host")));
    }
}
