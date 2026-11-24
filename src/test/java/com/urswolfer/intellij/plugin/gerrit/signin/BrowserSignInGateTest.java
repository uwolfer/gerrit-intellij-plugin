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

package com.urswolfer.intellij.plugin.gerrit.signin;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The browser of the IDE is only within reach of the plugin where {@code gerrit-jcef.xml} is loaded, 2026.2 and
 * later; an older IDE has its classes in the platform, but out of reach or, on some systems, not at all, and fails with
 * a NoClassDefFoundError on the first class which names one of them. So only the classes of the {@code signin.jcef}
 * package name the browser's, and only {@code gerrit-jcef.xml} names them in turn: then an older IDE never loads
 * one of them, whatever path the code takes.
 */
public class BrowserSignInGateTest {

    private static final String GATED = "com/urswolfer/intellij/plugin/gerrit/signin/jcef/";
    private static final String[] BROWSER = {"com/intellij/ui/jcef/", "org/cef/"};

    @Test
    public void testOnlyTheGatedPackageNamesTheBrowser() throws IOException, URISyntaxException {
        List<String> wrong = new ArrayList<>();
        int gatedUsingTheBrowser = 0;
        for (Path file : classFiles()) {
            String path = classesRoot().relativize(file).toString().replace('\\', '/');
            // class names in the constant pool are plain ASCII
            String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            boolean gated = path.startsWith(GATED);
            boolean namesBrowser = Stream.of(BROWSER).anyMatch(content::contains);
            if (gated) {
                gatedUsingTheBrowser += namesBrowser ? 1 : 0;
            } else if (namesBrowser) {
                wrong.add(path + " names a class of the browser");
            } else if (content.contains(GATED)) {
                wrong.add(path + " names a class of " + GATED);
            }
        }
        Assert.assertEquals(wrong, new ArrayList<String>());
        Assert.assertTrue(gatedUsingTheBrowser > 0, "no class of " + GATED + " found in " + classesRoot());
    }

    @Test
    public void testOnlyTheOptionalDescriptorRegistersTheGatedPackage() throws IOException {
        String pluginXml = resource("META-INF/plugin.xml");
        String gatedXml = resource("META-INF/gerrit-jcef.xml");

        Assert.assertTrue(pluginXml.contains(
            "<depends optional=\"true\" config-file=\"gerrit-jcef.xml\">com.intellij.modules.jcef</depends>"));
        Assert.assertFalse(pluginXml.contains(GATED.replace('/', '.')), "plugin.xml names a class of " + GATED);
        Assert.assertTrue(gatedXml.contains(GATED.replace('/', '.')));
    }

    private static List<Path> classFiles() throws IOException, URISyntaxException {
        try (Stream<Path> files = Files.walk(classesRoot().resolve("com/urswolfer"))) {
            return files.filter(file -> file.toString().endsWith(".class")).collect(Collectors.toList());
        }
    }

    private static Path classesRoot() throws URISyntaxException {
        Path root = Paths.get(BrowserSignIn.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Assert.assertTrue(Files.isDirectory(root), "the plugin's classes are expected in a directory: " + root);
        return root;
    }

    private String resource(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            Assert.assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
