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

import com.google.common.collect.Iterables;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ProjectInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;

/**
 * @author Urs Wolfer
 */
public class GerritUtilTest {

    @Test
    public void testProjectNames() throws Exception {
        final Method getProjectName = GerritUtil.class.getDeclaredMethod("getProjectName",
                String.class, String.class);
        getProjectName.setAccessible(true);

        // Default set - test trailing / behaviour
        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server",
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/",
                        "http://gerrit.server/project"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server",
                        "http://gerrit.server/project/"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/",
                        "http://gerrit.server/project/"
                ));

        // Subdirectory set - test trailing / behaviour
        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r/",
                        "http://gerrit.server/r/project"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project/"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r/",
                        "http://gerrit.server/r/project/"
                ));

        // Default set - test named .git
        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server",
                        "http://gerrit.server/project.git"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/",
                        "http://gerrit.server/project.git"
                ));


        // Subdirectory set - test named .git
        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project.git"
                ));

        Assert.assertEquals("project",
                getProjectName.invoke(null,
                        "http://gerrit.server/r/",
                        "http://gerrit.server/r/project.git"
                ));

        // Test some project names with / in them
        Assert.assertEquals("project/blah/test",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project/blah/test"
                ));

        Assert.assertEquals("project/blah/test",
                getProjectName.invoke(null,
                        "http://gerrit.server/r",
                        "http://gerrit.server/r/project/blah/test.git"
                ));
    }

    @Test
    public void testParseProjectInfos() throws Exception {
        URL url = GerritUtilTest.class.getResource("/com/urswolfer/intellij/plugin/gerrit/rest/projects.json");
        File file = new File(url.toURI());
        JsonElement jsonElement = new JsonParser().parse(new FileReader(file));
        JsonObject firstElement = (JsonObject) Iterables.get(jsonElement.getAsJsonObject().entrySet(), 0, null).getValue();

        final Method parseSingleRepositoryInfo = GerritUtil.class.getDeclaredMethod("parseSingleRepositoryInfo", JsonObject.class);
        parseSingleRepositoryInfo.setAccessible(true);

        ProjectInfo projectInfo = (ProjectInfo) parseSingleRepositoryInfo.invoke(null, firstElement);
        Assert.assertEquals("gerritcodereview#project", projectInfo.getKind());
        Assert.assertEquals("packages%2Ftest", projectInfo.getId());
        Assert.assertEquals("packages/test", projectInfo.getDecodedId());
    }


    @Test
    public void testParseChanges() throws Exception {
        URL url = GerritUtilTest.class.getResource("/com/urswolfer/intellij/plugin/gerrit/rest/changes.json");
        File file = new File(url.toURI());
        JsonElement jsonElement = new JsonParser().parse(new FileReader(file));

        final Method parseChangeInfos = GerritUtil.class.getDeclaredMethod("parseChangeInfos", JsonElement.class);
        parseChangeInfos.setAccessible(true);

        List<ChangeInfo> changeInfos = (List<ChangeInfo>) parseChangeInfos.invoke(null, jsonElement);
        Assert.assertEquals(3, changeInfos.size());

        ChangeInfo firstChangeInfo = changeInfos.get(0);

        Assert.assertEquals(1375080914000l, firstChangeInfo.getUpdated().getTime()); // verify that the date parser uses correct format and UTC for parsing

        Assert.assertEquals("Urs Wolfer", firstChangeInfo.getLabels().get("Code-Review").getApproved().getName());
    }

}
