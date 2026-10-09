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

import org.junit.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ResourceBundle;

/**
 * What Settings | Keymap shows of the actions comes from plugin.xml and the bundle.
 */
public class PluginActionsTest {

    @Test
    public void testRefreshUsesShortcutOfRefresh() throws Exception {
        Assert.assertEquals("Refresh", findAction("Gerrit.Refresh").getAttribute("use-shortcut-of"));
    }

    @Test
    public void testEveryActionHasTextAndDescription() throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("messages.GerritBundle");
        NodeList actions = pluginXml().getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            String id = action.getAttribute("id");
            if (!id.startsWith("Gerrit.") || action.hasAttribute("text")) {
                continue;
            }
            Assert.assertTrue(id + " has no text", bundle.containsKey("action." + id + ".text"));
            Assert.assertTrue(id + " has no description", bundle.containsKey("action." + id + ".description"));
        }
    }

    private static Element findAction(String id) throws Exception {
        NodeList actions = pluginXml().getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            if (id.equals(action.getAttribute("id"))) {
                return action;
            }
        }
        throw new AssertionError("no action " + id);
    }

    private static Element pluginXml() throws Exception {
        try (InputStream in = PluginActionsTest.class.getResourceAsStream("/META-INF/plugin.xml")) {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
        }
    }
}
