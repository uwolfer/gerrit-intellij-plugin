/*
 * Copyright 2013-2014 Urs Wolfer
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

import com.google.inject.Inject;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import javassist.*;
import org.jetbrains.annotations.NotNull;

/**
 * Since there are no entry points for modifying the push dialog without copying a lot of source, some modifications
 * to the Git push setting panel (where you can set an alternative remote branch) are done with byte-code modification
 * with javassist:
 *
 * * The method GitManualPushToBranch#layoutComponents is extended by one component at the end which provides all the UI.
 * * GerritPushExtensionPanel (and some inner-classes) get copied to the Git plugin class loader.
 *
 * @author Urs Wolfer
 */
@SuppressWarnings("ComponentNotRegistered") // proxy class below is registered
public class GerritPushExtension implements ApplicationComponent {

    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private Logger log;

    public void initComponent() {
        ClassPool classPool = ClassPool.getDefault();

        /*
        ClassLoader gitIdeaPluginClassLoader = GitPusher.class.getClassLoader();
        ClassLoader gerritPluginClassLoader = GerritPushExtensionPanel.class.getClassLoader();
        classPool.appendClassPath(new LoaderClassPath(gitIdeaPluginClassLoader));
        classPool.appendClassPath(new LoaderClassPath(gerritPluginClassLoader));

        copyGerritPluginClassesToGitPlugin(classPool, gitIdeaPluginClassLoader);

        modifyGitBranchPanel(classPool, gitIdeaPluginClassLoader);
    }

    private void modifyGitBranchPanel(ClassPool classPool, ClassLoader classLoader) {
        try {
            boolean pushToGerrit = gerritSettings.getPushToGerrit();

            CtClass gitManualPushToBranchClass = classPool.get("git4idea.push.GitManualPushToBranch");

            CtMethod loadComponentsMethod = gitManualPushToBranchClass.getDeclaredMethod("layoutComponents");
            loadComponentsMethod.insertAfter(
                "add(" +
                    "new com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel(" +
                    pushToGerrit + ", myDestBranchTextField, myManualPush)," +
                    "java.awt.BorderLayout.SOUTH" +
                ");"
            );

            gitManualPushToBranchClass.toClass(classLoader, GitPusher.class.getProtectionDomain());
            gitManualPushToBranchClass.detach();
        } catch (CannotCompileException e) {
            log.error("Failed to inject Gerrit push UI.", e);
        } catch (NotFoundException e) {
            log.error("Failed to inject Gerrit push UI.", e);
        }
    }

    private void copyGerritPluginClassesToGitPlugin(ClassPool classPool, ClassLoader targetClassLoader) {
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$ChangeActionListener");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$ChangeTextActionListener");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$SettingsStateActionListener");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$LoadDestinationBranchListener");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$UpdateDestinationBranchRunnable");
    }

    private void loadClass(ClassPool classPool, ClassLoader targetClassLoader, String className) {
        try {
            CtClass loadedClass = classPool.get(className);
            loadedClass.toClass(targetClassLoader, GitPusher.class.getProtectionDomain());
            loadedClass.detach();
        } catch (CannotCompileException e) {
            log.error("Failed to load class required for Gerrit push UI injections.", e);
        } catch (NotFoundException e) {
            log.error("Failed to load class required for Gerrit push UI injections.", e);
        }
        */
    }

    public void disposeComponent() {}

    @NotNull
    public String getComponentName() {
        return "GerritPushExtension";
    }


    public static final class Proxy implements ApplicationComponent {
        private final GerritPushExtension delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(GerritPushExtension.class);
        }

        @Override
        public void initComponent() {
            delegate.initComponent();
        }

        @Override
        public void disposeComponent() {
            delegate.disposeComponent();
        }

        @NotNull
        @Override
        public String getComponentName() {
            return delegate.getComponentName();
        }
    }
}
