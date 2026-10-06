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

import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class GerritBundleTest {

    /**
     * The platform finds an action's text by the key {@code action.<id>.text}, so a renamed id or a missing key
     * leaves a blank menu item, and nothing else fails.
     */
    @Test
    public void testEveryRegisteredActionHasTextAndDescription() throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("messages.GerritBundle");
        List<String> missing = new ArrayList<>();
        Document pluginXml;
        try (InputStream in = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
            pluginXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
        for (String tag : new String[] {"action", "group"}) {
            NodeList elements = pluginXml.getElementsByTagName(tag);
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                if (!element.hasAttribute("class")) {
                    continue; // a bare group is only a place to add actions to
                }
                for (String suffix : new String[] {"text", "description"}) {
                    String key = tag + "." + element.getAttribute("id") + "." + suffix;
                    if (!bundle.containsKey(key)) {
                        missing.add(key);
                    }
                }
            }
        }
        Assert.assertEquals(missing, new ArrayList<String>());
    }
}
