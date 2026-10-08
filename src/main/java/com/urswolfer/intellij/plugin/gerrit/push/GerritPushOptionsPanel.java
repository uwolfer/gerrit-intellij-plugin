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

import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.openapi.project.Project;
import git4idea.push.GitPushOptionsPanel;
import git4idea.push.GitPushTagMode;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Predicate;

/**
 * Wraps IntelliJ's GitPushOptionsPanel and Gerrit plugin push extension into one VcsPushOptionsPanel.
 *
 * @author Urs Wolfer
 */
public class GerritPushOptionsPanel extends VcsPushOptionsPanel {
    /**
     * The "push to Gerrit by default" setting for the push dialogs opened from now on. The rewritten
     * {@code GitPushSupport} reads it from the copy of this class in the Git plugin class loader, which cannot see
     * the settings service, so {@link GerritPushExtension#setPushToGerritByDefault} calls
     * {@link #setPushToGerritByDefault} there.
     */
    public static volatile boolean pushToGerritByDefault;

    public static void setPushToGerritByDefault(boolean pushToGerrit) {
        if (pushToGerrit != pushToGerritByDefault) {
            GerritPushExtensionPanel.forgetClickedBoxes();
        }
        pushToGerritByDefault = pushToGerrit;
    }

    /**
     * Whether a project uses Gerrit. The copy in the Git plugin class loader cannot see the project settings either,
     * so {@link GerritPushExtension#install()} hands it over; until it has, every project does, as all did before
     * there was a choice.
     */
    private static volatile Predicate<Project> enabledForProject;

    public static void setEnabledForProject(Predicate<Project> enabledForProject) {
        GerritPushOptionsPanel.enabledForProject = enabledForProject;
    }

    /** {@code null} in a project without Gerrit, which gets the dialog of the platform, push targets untouched. */
    @Nullable
    private final GerritPushExtensionPanel gerritPushExtensionPanel;
    private GitPushOptionsPanel gitPushOptionsPanel;

    public GerritPushOptionsPanel(boolean pushToGerrit, Project project) {
        Predicate<Project> enabled = enabledForProject;
        gerritPushExtensionPanel = enabled == null || enabled.test(project)
            ? new GerritPushExtensionPanel(pushToGerrit, project.getLocationHash(), project)
            : null;
    }

    boolean showsGerritOptions() {
        return gerritPushExtensionPanel != null;
    }

    @SuppressWarnings("UnusedDeclaration") // javassist call
    public void initPanel(@Nullable GitPushTagMode defaultMode, boolean followTagsSupported, boolean showSkipHookOption) {
        removeAll();
        gitPushOptionsPanel = new GitPushOptionsPanel(defaultMode, followTagsSupported, showSkipHookOption);

        JPanel mainContainer = new JPanel();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.PAGE_AXIS));

        if (gerritPushExtensionPanel != null) {
            mainContainer.add(gerritPushExtensionPanel);
            mainContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        }
        mainContainer.add(gitPushOptionsPanel);

        add(mainContainer, BorderLayout.CENTER);
    }

    public VcsPushOptionValue getValue() {
        return gitPushOptionsPanel.getValue();
    }
}
