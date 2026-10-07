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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import git4idea.push.GitPushOperation;
import javassist.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * The push dialog offers no entry point for adding the Gerrit push settings to it, so the panel with them is added
 * with byte-code modification with javassist:
 *
 * * GitPushSupport#createOptionsPanel is overwritten in order to return the Gerrit push settings panel.
 * * GerritPushExtensionPanel, GerritPushOptionsPanel and the classes they use get copied to the Git plugin class loader.
 * * The copied GerritPushExtensionPanel is handed the account completion, which stays in the Gerrit plugin class loader.
 *
 * The byte-code modifications are triggered by {@link #install()}, which {@link GerritPushExtensionStarter}
 * calls on application startup. They are applied at most once per application.
 *
 * @author Urs Wolfer
 */
public final class GerritPushExtension {
    private static final Logger LOG = Logger.getInstance(GerritPushExtension.class);

    static final List<String> CLASSES_FOR_GIT_PLUGIN = Arrays.asList(
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel",
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushTargetUpdater",
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel",
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$1",
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$ChangeActionListener",
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$ChangeTextActionListener",
            "com.urswolfer.intellij.plugin.gerrit.push.GerritPushExtensionPanel$SettingsStateActionListener",
            "com.urswolfer.intellij.plugin.gerrit.push.PushOptionValidator",
            "com.urswolfer.intellij.plugin.gerrit.util.UrlUtils",
            "com.urswolfer.intellij.plugin.gerrit.util.Whitespace");

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

            handOverAccountCompletion(gitIdeaPluginClassLoader);
        } catch (Exception e) {
            LOG.error("Failed to inject Gerrit push UI.", e);
        } catch (Error e) {
            LOG.error("Failed to inject Gerrit push UI.", e);
        }
    }

    private static void modifyGitBranchPanel(ClassPool classPool, ClassLoader classLoader) {
        try {
            CtClass gitPushSupportClass = classPool.get("git4idea.push.GitPushSupport");

            rewriteGitPushSupport(gitPushSupportClass, GerritSettings.getInstance().getPushToGerrit());

            gitPushSupportClass.toClass(classLoader, GitPushOperation.class.getProtectionDomain());
            gitPushSupportClass.detach();
        } catch (CannotCompileException e) {
            LOG.error("Failed to inject Gerrit push UI.", e);
        } catch (NotFoundException e) {
            LOG.error("Failed to inject Gerrit push UI.", e);
        }
    }

    /**
     * Kept apart from loading the class so that a test can compile it against the Git plugin of an IDE: the body
     * refers to private members of the platform, and javassist is the first to notice when one of them is renamed.
     */
    static void rewriteGitPushSupport(CtClass gitPushSupportClass, boolean pushToGerrit)
            throws CannotCompileException, NotFoundException {
        // A panel per push dialog, like the platform has it: a panel kept in a field would show the options
        // of the previous push (private, WIP, topic, ...) in every following dialog of the session.
        CtMethod createOptionsPanelMethod = gitPushSupportClass.getDeclaredMethod("createOptionsPanel");
        createOptionsPanelMethod.setBody(
            "{" +
                "com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel gerritPushOptionsPanel = new com.urswolfer.intellij.plugin.gerrit.push.GerritPushOptionsPanel(" + pushToGerrit + ", myVcs.getProject());" +
                "gerritPushOptionsPanel.initPanel(mySettings.getPushTagMode(), git4idea.config.GitVersionSpecialty.SUPPORTS_FOLLOW_TAGS.existsIn(myVcs.getVersion()), git4idea.config.GitVersionSpecialty.PRE_PUSH_HOOK.existsIn(myVcs.getVersion()));" +
                "return gerritPushOptionsPanel;" +
            "}"
        );
    }

    /**
     * Copies the Gerrit plugin classes which take part in the push dialog into the Git plugin class loader.
     *
     * Every class used by one of them must be listed as well: the Git plugin class loader does not know the
     * Gerrit plugin, so a class which is not copied ends up as a NoClassDefFoundError as soon as the copied
     * code touches it.
     */
    private static void copyGerritPluginClassesToGitPlugin(ClassPool classPool, ClassLoader targetClassLoader) {
        for (String className : CLASSES_FOR_GIT_PLUGIN) {
            loadClass(classPool, targetClassLoader, className);
        }
    }

    /**
     * The completion asks Gerrit through the REST client, which the Git plugin class loader cannot load, so it is
     * created here and handed to the copied panel as a {@link Function}: a type which both class loaders share. A
     * panel which does not get it shows plain text fields, so whatever goes wrong here must not cost the push
     * dialog integration: it runs last, and catches everything.
     */
    private static void handOverAccountCompletion(ClassLoader gitIdeaPluginClassLoader) {
        try {
            handOverAccountCompletion(
                Class.forName(GerritPushExtensionPanel.class.getName(), true, gitIdeaPluginClassLoader),
                PushAccountCompletionProvider::new);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("Failed to add account suggestions to Gerrit push UI.", e);
        }
    }

    static void handOverAccountCompletion(Class<?> panelClass, Function<Project, TextCompletionProvider> completion)
            throws ReflectiveOperationException {
        panelClass.getMethod("setAccountCompletion", Function.class).invoke(null, completion);
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
