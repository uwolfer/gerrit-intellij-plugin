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

package com.urswolfer.intellij.plugin.gerrit;

import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GerritProjectSettingsTest {

    @Test
    public void testProjectWithoutStateUsesGerrit() throws Exception {
        // every project opened before there was a choice
        Assert.assertTrue(deserialize("<component />").enabled);
    }

    @Test
    public void testProjectSwitchedOffStaysOff() throws Exception {
        GerritProjectSettings.ProjectState off = new GerritProjectSettings.ProjectState();
        off.enabled = false;

        Element written = XmlSerializer.serialize(off, new SkipDefaultsSerializationFilter());

        Assert.assertEquals(written.getAttributeValue("enabled"), "false");
        Assert.assertFalse(XmlSerializer.deserialize(written, GerritProjectSettings.ProjectState.class).enabled);
    }

    private static GerritProjectSettings.ProjectState deserialize(String xml) throws Exception {
        Element element = new org.jdom.input.SAXBuilder().build(new java.io.StringReader(xml)).getRootElement();
        return XmlSerializer.deserialize(element, GerritProjectSettings.ProjectState.class);
    }
}
