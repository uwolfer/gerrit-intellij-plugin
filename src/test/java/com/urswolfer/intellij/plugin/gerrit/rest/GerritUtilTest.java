/*
 * Copyright 2013 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.rest;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

/**
 * @author Urs Wolfer
 */
public class GerritUtilTest {

    private static final String PROJECT = "project";
    private static final String HTTP_GERRIT_SERVER = "http://gerrit.server";
    private static final String HTTP_GERRIT_SERVER_SLASH = "http://gerrit.server/";
    private static final String HTTP_GERRIT_SERVER_R = "http://gerrit.server/r";
    private static final String HTTP_GERRIT_SERVER_R_SLASH = "http://gerrit.server/r/";

    private GerritUtil gerritUtil;

    @BeforeMethod
    public void setup() {
        gerritUtil = GerritTestModule.getInstance(GerritUtil.class);
    }

    @Test
    public void testProjectNames() throws Exception {
        final Method getProjectName = GerritUtil.class.getDeclaredMethod("getProjectName",
                String.class, String.class);
        getProjectName.setAccessible(true);

        // Default set - test trailing / behaviour
        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER,
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_SLASH,
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER,
                        "http://gerrit.server/project/"
                ));

        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_SLASH,
                        "http://gerrit.server/project/"
                ));

        // Subdirectory set - test trailing / behaviour
        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_R,
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_R_SLASH,
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_R,
                        "http://gerrit.server/r/project/"
                ));

        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_R_SLASH,
                        "http://gerrit.server/r/project/"
                ));

        // Default set - test named .git
        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER,
                        "http://gerrit.server/project.git"
                ));

        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_SLASH,
                        "http://gerrit.server/project.git"
                ));


        // Subdirectory set - test named .git
        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_R,
                        "http://gerrit.server/r/project.git"
                ));

        Assert.assertEquals(PROJECT,
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_R_SLASH,
                        "http://gerrit.server/r/project.git"
                ));

        // Test some project names with / in them
        Assert.assertEquals("project/blah/test",
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_R,
                        "http://gerrit.server/r/project/blah/test"
                ));

        Assert.assertEquals("project/blah/test",
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER_R,
                        "http://gerrit.server/r/project/blah/test.git"
                ));

        // specific case where gerrit URL is provided via HTTP but git is configured to use ssh
        Assert.assertEquals("project/blah",
                getProjectName.invoke(gerritUtil,
                        "http://gerrit.server/gerrit",
                        "ssh://git@gerrit.server:29418/project/blah"
                ));

        // should not fail with an StringIndexOutOfBoundsException
        Assert.assertEquals("",
                getProjectName.invoke(gerritUtil,
                        HTTP_GERRIT_SERVER,
                        HTTP_GERRIT_SERVER
                ));
    }
}
