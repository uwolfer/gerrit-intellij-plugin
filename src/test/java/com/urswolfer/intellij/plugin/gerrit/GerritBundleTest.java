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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    /**
     * A key which the bundle lacks only fails when the code asking for it runs, so a typo hides in a dialog which
     * nobody opens.
     */
    @Test
    public void testEveryKeyAskedForExists() throws IOException {
        ResourceBundle bundle = ResourceBundle.getBundle("messages.GerritBundle");
        Pattern call = Pattern.compile("(?:GerritBundle|PushMessages)\\.message\\(\"([^\"]+)\"");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher matcher = call.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                while (matcher.find()) {
                    if (!bundle.containsKey(matcher.group(1))) {
                        missing.add(file.getFileName() + ": " + matcher.group(1));
                    }
                }
            }
        }
        Assert.assertEquals(missing, new ArrayList<String>());
    }

    /**
     * A properties value loses its leading blanks, and a message with parameters is a MessageFormat, which takes a
     * single quote for the start of quoted text: both change the text without failing anything.
     */
    @Test
    public void testValuesAreWrittenForTheirFormat() {
        ResourceBundle bundle = ResourceBundle.getBundle("messages.GerritBundle");
        List<String> wrong = new ArrayList<>();
        for (String key : Collections.list(bundle.getKeys())) {
            String value = bundle.getString(key);
            boolean formatted = value.contains("{");
            if (formatted && value.replace("''", "").contains("'")) {
                wrong.add(key + ": a parameter message needs '' for an apostrophe");
            }
            if (!formatted && value.contains("''")) {
                wrong.add(key + ": a message without parameters is not a MessageFormat, so '' stays doubled");
            }
        }
        // the loaded value has lost such a blank already, so the lines are read as they are written
        try (InputStream in = getClass().getResourceAsStream("/messages/GerritBundle.properties")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.startsWith("#") && line.matches("[^=:\\s]+\\s*[=:].*\\s")) {
                    wrong.add(line.split("[=:\\s]", 2)[0] + ": a blank at the end is trimmed by editors, add it in the code");
                }
                if (!line.startsWith("#") && line.matches("[^=:\\s]+\\s*[=:]\\s+\\S.*")) {
                    wrong.add(line.split("[=:\\s]", 2)[0] + ": a blank after the separator is dropped, add it in the code");
                }
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        Assert.assertEquals(wrong, new ArrayList<String>());
    }
}
