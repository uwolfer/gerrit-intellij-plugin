/*
 * Copyright 2000-2011 JetBrains s.r.o.
 * Copyright 2013 Urs Wolfer
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
package com.urswolfer.intellij.plugin.gerrit.extension;

import com.google.common.io.Files;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.HttpStatusException;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ProjectInfo;
import git4idea.actions.BasicAction;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.checkout.GitCloneDialog;
import git4idea.commands.Git;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Parts based on org.jetbrains.plugins.github.GithubCheckoutProvider
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class GerritCheckoutProvider implements CheckoutProvider {

    private static Logger LOG = GerritUtil.LOG;

    @Override
    public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
        if (!GerritUtil.testGitExecutable(project)) {
            return;
        }
        BasicAction.saveAll();
        List<ProjectInfo> availableProjects = null;
        try {
            availableProjects = GerritUtil.getAvailableProjects(project);
        } catch (Exception e) {
            LOG.info(e);
            GerritUtil.notifyError(project, "Couldn't get the list of Gerrit repositories", GerritUtil.getErrorTextFromException(e));
        }
        if (availableProjects == null) {
            return;
        }
        Collections.sort(availableProjects, new Comparator<ProjectInfo>() {
            @Override
            public int compare(final ProjectInfo p1, final ProjectInfo p2) {
                return p1.getId().compareTo(p2.getId());
            }
        });

        final GitCloneDialog dialog = new GitCloneDialog(project);
        // Add predefined repositories to history
        for (int i = availableProjects.size() - 1; i >= 0; i--) {
            dialog.prependToHistory(GerritSettings.getInstance().getHost() + '/' + availableProjects.get(i).getDecodedId());
        }
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }
        dialog.rememberSettings();
        final VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
        if (destinationParent == null) {
            return;
        }
        final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
        final String directoryName = dialog.getDirectoryName();
        final String parentDirectory = dialog.getParentDirectory();

        Git git = ServiceManager.getService(Git.class);

        Listener listenerWrapper = addCommitMsgHookListener(listener, directoryName, parentDirectory, project);

        GitCheckoutProvider.clone(project, git, listenerWrapper, destinationParent, sourceRepositoryURL, directoryName, parentDirectory);
    }

    @Override
    public String getVcsName() {
        return "Gerrit";
    }

    /*
     * Since this is a listener which needs to be executed in any case, it cannot be a normal checkout-listener.
     * Checkout-listeners only get executed when "previous" listener got not executed (returns false).
     * Example: If user decides to setup a new project from newly created checkout, our listener does not get executed.
     */
    private Listener addCommitMsgHookListener(final Listener listener, final String directoryName, final String parentDirectory, final Project project) {
        return new Listener() {
            @Override
            public void directoryCheckedOut(File directory, VcsKey vcs) {
                setupCommitMsgHook(parentDirectory, directoryName, project);

                if (listener != null) listener.directoryCheckedOut(directory, vcs);
            }

            @Override
            public void checkoutCompleted() {
                if (listener != null) listener.checkoutCompleted();
            }
        };
    }

    private void setupCommitMsgHook(String parentDirectory, String directoryName, Project project) {
        try {
            HttpMethod method = GerritApiUtil.doREST(
                    "/a/tools/hooks/commit-msg", null, Collections.<Header>emptyList(), GerritApiUtil.HttpVerb.GET);
            if (method.getStatusCode() != 200) {
                throw new HttpStatusException(method.getStatusCode(), method.getStatusText(), method.getStatusText());
            }
            File targetFile = new File(parentDirectory + '/' + directoryName + "/.git/hooks/commit-msg");
            Files.write(method.getResponseBody(), targetFile);
            targetFile.setExecutable(true);
        } catch (Exception e) {
            LOG.info(e);
            GerritUtil.notifyError(project, "Couldn't set up Gerrit Commit-Message Hook. Please do it manually.",
                    GerritUtil.getErrorTextFromException(e));
        }
    }
}
