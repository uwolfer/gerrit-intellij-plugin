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
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
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

    /**
     * Two controls of one form on the same Alt letter leave one of them unreachable, and nothing else fails. The
     * push options share their dialog with the buttons of the IDE, which take P for Push.
     */
    @Test
    public void testMnemonicsOfAFormAreUnique() throws IOException {
        Properties english = load("GerritBundle.properties");
        List<String> wrong = new ArrayList<>(mnemonicsBroken(english, null));
        for (String file : translations()) {
            for (String problem : mnemonicsBroken(load(file), english)) {
                wrong.add(file + ": " + problem);
            }
        }
        Assert.assertEquals(wrong, new ArrayList<String>());
    }

    /**
     * A translation which marks another letter than the English text shares a form with the English texts it lacks,
     * and falls back to, so it keeps the letters. East Asian texts put the marker in parentheses after the word.
     *
     * @param english null for the English bundle itself
     */
    private List<String> mnemonicsBroken(Properties bundle, Properties english) {
        List<String> wrong = new ArrayList<>();
        List<List<String>> forms = List.of(
            List.of("push.enabled", "push.private", "push.unmarkPrivate", "push.wip", "push.ready",
                "push.publishComments", "push.draft", "push.submit", "push.field.branch", "push.field.topic",
                "push.field.hashtag", "push.field.description", "push.field.reviewers", "push.field.cc"),
            List.of("review.notify", "review.submit"),
            List.of("diff.resolved", "diff.save"),
            List.of("settings.project.enabled", "settings.notifications.review", "settings.notifications.refresh",
                "settings.list.all", "settings.git.push", "settings.list.avatars", "settings.git.comments",
                "settings.git.comments.inline"),
            List.of("rebase.onBranch", "rebase.other", "rebase.conflicts"),
            List.of("rebase.onTarget", "rebase.other", "rebase.conflicts"),
            List.of("merge.label.project", "merge.label.source", "merge.label.target", "merge.label.subject",
                "merge.label.topic", "merge.label.reviewers", "merge.label.ccs"),
            List.of("assignee.label"),
            List.of("account.label.url", "account.label.login", "account.label.password",
                "account.label.cloneBaseUrl", "account.label.gitilesUrl"),
            List.of("clone.url.label", "clone.directory.label"),
            // the menu of the change list, in which an action takes its mnemonic from an underscore
            List.of("action.Gerrit.CompareBranch.text", "action.Gerrit.CheckoutAction.text",
                "action.Gerrit.InspectChange.text", "action.Gerrit.CherryPickCommit.text",
                "action.Gerrit.CherryPick.text", "action.Gerrit.Reset.text", "action.Gerrit.Star.text",
                "action.Gerrit.AddReviewers.text", "action.Gerrit.SetAssignee.text", "action.Gerrit.Publish.text",
                "action.Gerrit.Delete.text", "group.Gerrit.Review.text", "action.Gerrit.Reply.text",
                "action.Gerrit.Submit.text", "action.Gerrit.Abandon.text", "action.Gerrit.Rebase.text",
                "action.Gerrit.Restore.text", "action.Gerrit.Revert.text", "action.Gerrit.Copy.text",
                "action.Gerrit.CopyUrl.text", "action.Gerrit.RefreshFeatureMergePatchSet.text",
                "action.Gerrit.OpenInBrowser.text"));
        for (List<String> form : forms) {
            Map<Character, String> letters = new TreeMap<>();
            for (String key : form) {
                String text = bundle.getProperty(key);
                if (english != null && text == null) {
                    continue; // falls back to English
                }
                Assert.assertNotNull(text, key + " is not in the bundle");
                char marker = key.startsWith("action.") || key.startsWith("group.") ? '_' : '&';
                Matcher matcher = Pattern.compile(Pattern.quote(String.valueOf(marker)) + "([A-Za-z])").matcher(text);
                if (!matcher.find()) {
                    wrong.add(key + ": no mnemonic, marked with " + marker);
                    continue;
                }
                char letter = Character.toLowerCase(matcher.group(1).charAt(0));
                String holder = letters.put(letter, key);
                if (holder != null) {
                    wrong.add(key + ": the mnemonic " + letter + " is taken by " + holder);
                }
                if (english != null) {
                    Matcher original = Pattern.compile(Pattern.quote(String.valueOf(marker)) + "([A-Za-z])")
                        .matcher(english.getProperty(key));
                    if (original.find() && Character.toLowerCase(original.group(1).charAt(0)) != letter) {
                        wrong.add(key + ": the mnemonic " + letter + " is not the " + original.group(1) + " of English");
                    }
                }
                // Push, Push tags, Run Git hooks, Edit all targets and Force push
                if ("pthaf".indexOf(letter) >= 0 && key.startsWith("push.")) {
                    wrong.add(key + ": " + letter + " belongs to the push dialog of the IDE");
                }
            }
        }
        return wrong;
    }

    private List<String> translations() throws IOException {
        List<String> files = new ArrayList<>();
        try (Stream<Path> paths = Files.list(Paths.get("src/main/resources/messages"))) {
            paths.map(path -> path.getFileName().toString())
                .filter(name -> name.matches("GerritBundle_[A-Za-z_]+\\.properties"))
                .sorted()
                .forEach(files::add);
        }
        Assert.assertFalse(files.isEmpty(), "no translation found, is the test run from the project directory?");
        return files;
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
