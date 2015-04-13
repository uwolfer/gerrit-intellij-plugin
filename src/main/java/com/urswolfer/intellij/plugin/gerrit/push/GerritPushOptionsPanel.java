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
import git4idea.push.GitPushTagMode;
import git4idea.push.GitPushTagPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Wraps IntelliJ's GitPushTagPanel and Gerrit plugin push extension into one VcsPushOptionsPanel.
 *
 * @author Urs Wolfer
 */
public class GerritPushOptionsPanel extends VcsPushOptionsPanel {
    private final GerritPushExtensionPanel gerritPushExtensionPanel;
    private GitPushTagPanel gitPushTagPanel;

    public GerritPushOptionsPanel(boolean pushToGerrit) {
        gerritPushExtensionPanel = new GerritPushExtensionPanel(pushToGerrit);
    }

    @SuppressWarnings("UnusedDeclaration") // javassist call
    public void initPanel(@Nullable GitPushTagMode defaultMode, boolean followTagsSupported) {
        removeAll();
        gitPushTagPanel = new GitPushTagPanel(defaultMode, followTagsSupported);

        JPanel mainContainer = new JPanel();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.PAGE_AXIS));

        mainContainer.add(gerritPushExtensionPanel);
        mainContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        mainContainer.add(gitPushTagPanel);

        add(mainContainer, BorderLayout.CENTER);

        gerritPushExtensionPanel.initialized();
    }

    public VcsPushOptionValue getValue() {
        return gitPushTagPanel.getValue();
    }

    public GerritPushExtensionPanel getGerritPushExtensionPanel() {
        return gerritPushExtensionPanel;
    }
}
