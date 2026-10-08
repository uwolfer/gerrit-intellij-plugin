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

package com.urswolfer.intellij.plugin.gerrit.push;

import com.intellij.openapi.project.Project;
import org.easymock.EasyMock;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class GerritPushOptionsPanelTest {

    @AfterMethod
    public void forgetProjectSettings() {
        GerritPushOptionsPanel.setEnabledForProject(null);
    }

    @Test
    public void testProjectWithoutGerritGetsNoGerritOptions() {
        GerritPushOptionsPanel.setEnabledForProject(project -> false);

        Assert.assertFalse(new GerritPushOptionsPanel(true, project()).showsGerritOptions());
    }

    @Test
    public void testProjectWithGerritGetsGerritOptions() {
        GerritPushOptionsPanel.setEnabledForProject(project -> true);

        Assert.assertTrue(new GerritPushOptionsPanel(true, project()).showsGerritOptions());
    }

    @Test
    public void testEveryProjectGetsGerritOptionsWhenTheSettingWasNotHandedOver() {
        Assert.assertTrue(new GerritPushOptionsPanel(true, project()).showsGerritOptions());
    }

    private static Project project() {
        Project project = EasyMock.createNiceMock(Project.class);
        EasyMock.expect(project.getLocationHash()).andStubReturn("demo");
        EasyMock.replay(project);
        return project;
    }
}
