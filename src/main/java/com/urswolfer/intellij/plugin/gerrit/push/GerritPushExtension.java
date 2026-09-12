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

import com.intellij.openapi.diagnostic.Logger;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import git4idea.push.GitPushOperation;
import javassist.*;

/**
 * Since there are no entry points for modifying the push dialog without copying a lot of source, some modifications
 * to the Git push setting panel (where you can set an alternative remote branch) are done with byte-code modification
 * with javassist:
 *
 * * Some methods of GitPushSupport are overwritten in order to inject Gerrit push support.
 * * GerritPushExtensionPanel, GerritPushOptionsPanel and GerritPushTargetPanel get copied to the Git plugin class loader.
 *
 * The byte-code modifications are triggered by {@link #install()}, which {@link GerritPushExtensionStarter}
 * calls on application startup. They are applied at most once per application.
 *
 * @author Urs Wolfer
 */
public final class GerritPushExtension {
    private static final Logger LOG = Logger.getInstance(GerritPushExtension.class);

    private static boolean installed = false;

    private GerritPushExtension() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            ClassPool classPool = ClassPool.getDefault();

            ClassLoader gitIdeaPluginClassLoader = GitPushOperation.class.getClassLoader(); // it must be a class which is not modified (loaded) by javassist later on
            ClassLoader gerritPluginClassLoader = GerritPushExtensionPanel.class.getClassLoader();
            classPool.appendClassPath(new LoaderClassPath(gitIdeaPluginClassLoader));
            classPool.appendClassPath(new LoaderClassPath(gerritPluginClassLoader));

            copyGerritPluginClassesToGitPlugin(classPool, gitIdeaPluginClassLoader);

            modifyGitBranchPanel(classPool, gitIdeaPluginClassLoader);
        } catch (Exception e) {
            LOG.error("Failed to inject Gerrit push UI.", e);
        } catch (Error e) {
            LOG.error("Failed to inject Gerrit push UI.", e);
        }
    }

    private static void modifyGitBranchPanel(ClassPool classPool, ClassLoader classLoader) {
        try {
            boolean pushToGerrit = GerritSettings.getInstance().getPushToGerrit();

            CtClass gitPushSupportClass = classPool.get("git4idea.push.GitPushSupport");
            CtClass gerritPushOptionsPanelClass = classPool.get("com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel");

            gitPushSupportClass.addField(new CtField(gerritPushOptionsPanelClass, "gerritPushOptionsPanel", gitPushSupportClass),
                    "new com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel(" + pushToGerrit + ");");

            CtMethod createOptionsPanelMethod = gitPushSupportClass.getDeclaredMethod("createOptionsPanel");
            createOptionsPanelMethod.setBody(
                "{" +
                    "gerritPushOptionsPanel.initPanel(mySettings.getPushTagMode(), git4idea.config.GitVersionSpecialty.SUPPORTS_FOLLOW_TAGS.existsIn(myVcs.getVersion()), git4idea.config.GitVersionSpecialty.PRE_PUSH_HOOK.existsIn(myVcs.getVersion()));" +
                    "return gerritPushOptionsPanel;" +
                "}"
            );

            CtMethod createTargetPanelMethod = gitPushSupportClass.getDeclaredMethod("createTargetPanel");
            // GitPushSupport#createTargetPanel signature change in: https://github.com/JetBrains/intellij-community/commit/1ab27885afa82e46eba4715829c88f0de494b652
            if (createTargetPanelMethod.getLongName().equals("git4idea.push.GitPushSupport.createTargetPanel(git4idea.repo.GitRepository,git4idea.push.GitPushTarget)")) {
                createTargetPanelMethod.setBody(
                    "{" +
                        "return new com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel(this, $1, $2, gerritPushOptionsPanel);" +
                    "}"
                );
            } else if (createTargetPanelMethod.getLongName().equals("git4idea.push.GitPushSupport.createTargetPanel(git4idea.repo.GitRepository,git4idea.push.GitPushSource,git4idea.push.GitPushTarget)")) {
                createTargetPanelMethod.setBody(
                    "{" +
                        "return new com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel(this, $1, $3, gerritPushOptionsPanel);" +
                    "}"
                );
            }

            gitPushSupportClass.toClass(classLoader, GitPushOperation.class.getProtectionDomain());
            gitPushSupportClass.detach();
        } catch (CannotCompileException e) {
            LOG.error("Failed to inject Gerrit push UI.", e);
        } catch (NotFoundException e) {
            LOG.error("Failed to inject Gerrit push UI.", e);
        }
    }

    /**
     * Copies the Gerrit plugin classes which take part in the push dialog into the Git plugin class loader.
     *
     * Every class used by one of them must be listed as well: the Git plugin class loader does not know the
     * Gerrit plugin, so a class which is not copied ends up as a NoClassDefFoundError as soon as the copied
     * code touches it.
     */
    private static void copyGerritPluginClassesToGitPlugin(ClassPool classPool, ClassLoader targetClassLoader) {
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetPanel$1");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$1");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$ChangeActionListener");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$ChangeTextActionListener");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$SettingsStateActionListener");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.push.PushOptionValidator");
        loadClass(classPool, targetClassLoader, "com.urswolfer.intellij.plugin.gerrit.util.UrlUtils");
    }

    private static void loadClass(ClassPool classPool, ClassLoader targetClassLoader, String className) {
        try {
            CtClass loadedClass = classPool.get(className);
            loadedClass.toClass(targetClassLoader, GitPushOperation.class.getProtectionDomain());
            loadedClass.detach();
        } catch (CannotCompileException e) {
            LOG.error("Failed to load class required for Gerrit push UI injections.", e);
        } catch (NotFoundException e) {
            LOG.error("Failed to load class required for Gerrit push UI injections.", e);
        }
    }
}
