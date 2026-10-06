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

import javassist.ClassPool;
import javassist.CtClass;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Both tests fail where {@link GerritPushExtension} would otherwise only log an error on IDE startup.
 */
public class GerritPushExtensionTest {
    // the plugin and the Gerrit REST client it bundles: the Git plugin class loader knows neither
    private static final String OWN_PACKAGE = "com.urswolfer.";

    @Test
    public void testGitPushSupportRewriteCompilesAgainstGitPlugin() throws Exception {
        ClassPool classPool = new ClassPool(true);
        CtClass gitPushSupport = classPool.get("git4idea.push.GitPushSupport");
        CtClass gerritPushOptionsPanel = classPool.get(GerritPushOptionsPanel.class.getName());

        GerritPushExtension.rewriteGitPushSupport(gitPushSupport, gerritPushOptionsPanel, true);
    }

    @Test
    public void testCopiedClassesUseOnlyCopiedPluginClasses() throws Exception {
        ClassPool classPool = new ClassPool(true);
        Set<String> copied = new HashSet<>(GerritPushExtension.CLASSES_FOR_GIT_PLUGIN);

        List<String> missing = new ArrayList<>();
        for (String className : GerritPushExtension.CLASSES_FOR_GIT_PLUGIN) {
            for (Object used : classPool.get(className).getRefClasses()) {
                String usedName = (String) used;
                if (usedName.startsWith(OWN_PACKAGE) && !copied.contains(usedName)) {
                    missing.add(className + " uses " + usedName);
                }
            }
        }

        Assert.assertTrue(missing.isEmpty(), "not copied to the Git plugin class loader: " + missing);
    }
}
