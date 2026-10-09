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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
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
    public void testValuesAreWrittenForTheirFormat() throws IOException {
        Assert.assertEquals(valueRulesBroken("GerritBundle.properties"), new ArrayList<String>());
    }

    /**
     * A translation which has a key the English bundle lacks is never shown, one with other parameters than the
     * English message shows a wrong text or fails at run time, and one which misses a key falls back to English.
     */
    @Test
    public void testTranslationsMatchTheEnglishBundle() throws IOException {
        Properties english = load("GerritBundle.properties");
        List<String> wrong = new ArrayList<>();
        List<String> files = new ArrayList<>();
        try (Stream<Path> paths = Files.list(Paths.get("src/main/resources/messages"))) {
            paths.map(path -> path.getFileName().toString())
                .filter(name -> name.matches("GerritBundle_[A-Za-z_]+\\.properties"))
                .sorted()
                .forEach(files::add);
        }
        Assert.assertFalse(files.isEmpty(), "no translation found, is the test run from the project directory?");
        for (String file : files) {
            Properties translation = load(file);
            for (String key : translation.stringPropertyNames()) {
                String englishValue = english.getProperty(key);
                if (englishValue == null) {
                    wrong.add(file + ": " + key + " is not a key of the English bundle");
                    continue;
                }
                Set<String> expected = placeholders(englishValue);
                Set<String> actual = placeholders(translation.getProperty(key));
                // a choice format of the English text is a plural which a language without one writes as one text
                boolean plural = englishValue.contains(",choice,");
                if (plural ? !expected.containsAll(actual) : !expected.equals(actual)) {
                    wrong.add(file + ": " + key + " has the parameters " + actual + " instead of " + expected);
                }
            }
            for (String problem : valueRulesBroken(file)) {
                wrong.add(file + ": " + problem);
            }
        }
        Assert.assertEquals(wrong, new ArrayList<String>());
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new TreeSet<>();
        Matcher matcher = Pattern.compile("\\{(\\d+)").matcher(value);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/messages/" + file)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private List<String> valueRulesBroken(String file) throws IOException {
        List<String> wrong = new ArrayList<>();
        Properties properties = load(file);
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            String value = properties.getProperty(key);
            boolean formatted = value.contains("{");
            if (formatted && value.replace("''", "").contains("'")) {
                wrong.add(key + ": a parameter message needs '' for an apostrophe");
            }
            if (!formatted && value.contains("''")) {
                wrong.add(key + ": a message without parameters is not a MessageFormat, so '' stays doubled");
            }
        }
        // the loaded value has lost such a blank already, so the lines are read as they are written
        try (InputStream in = getClass().getResourceAsStream("/messages/" + file)) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.startsWith("#") && line.matches("[^=:\\s]+\\s*[=:].*\\s")) {
                    wrong.add(line.split("[=:\\s]", 2)[0] + ": a blank at the end is trimmed by editors, add it in the code");
                }
                if (!line.startsWith("#") && line.matches("[^=:\\s]+\\s*[=:]\\s+\\S.*")) {
                    wrong.add(line.split("[=:\\s]", 2)[0] + ": a blank after the separator is dropped, add it in the code");
                }
            }
        }
        return wrong;
    }
}
