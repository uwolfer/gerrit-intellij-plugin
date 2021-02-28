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

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.io.ByteStreams;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.inject.Inject;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.intellij.plugin.gerrit.GerritModule;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.checkout.GitCloneDialog;
import git4idea.commands.Git;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Parts based on org.jetbrains.plugins.github.GithubCheckoutProvider
 *
 * @author oleg
 * @author Urs Wolfer
 */
public class GerritCheckoutProvider implements CheckoutProvider {

    private static final Function<ProjectInfo, String> GET_ID_FUNCTION = new Function<ProjectInfo, String>() {
        public String apply(ProjectInfo from) {
            return from.id;
        }
    };
    private static final Ordering<ProjectInfo> ID_REVERSE_ORDERING = Ordering.natural().onResultOf(GET_ID_FUNCTION).reverse();

    @Inject
    private LocalFileSystem localFileSystem;
    @Inject
    private GerritUtil gerritUtil;
    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private Logger log;
    @Inject
    private NotificationService notificationService;
    @Inject
    private GerritRestApi gerritApi;

    @Override
    public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
        if (!gerritUtil.testGitExecutable(project)) {
            return;
        }
        FileDocumentManager.getInstance().saveAllDocuments();
        List<ProjectInfo> availableProjects = null;
        try {
            availableProjects = gerritUtil.getAvailableProjects(project);
        } catch (Exception e) {
            log.info(e);
            NotificationBuilder notification = new NotificationBuilder(
                    project,
                    "Couldn't get the list of Gerrit repositories",
                    gerritUtil.getErrorTextFromException(e));
            notificationService.notifyError(notification);
        }
        if (availableProjects == null) {
            return;
        }
        ImmutableSortedSet<ProjectInfo> orderedProjects =
            ImmutableSortedSet.orderedBy(ID_REVERSE_ORDERING).addAll(availableProjects).build();

        String url = getCloneBaseUrl();

        final GitCloneDialog dialog = new GitCloneDialog(project);
        for (ProjectInfo projectInfo : orderedProjects) {
            dialog.prependToHistory(url + '/' + Url.decode(projectInfo.id));
        }
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }
        dialog.rememberSettings();
        final VirtualFile destinationParent = localFileSystem.findFileByIoFile(new File(dialog.getParentDirectory()));
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

    /**
     * If set, return the clone base url from the preferences. Otherwise, try to determine the Git clone url by
     * fetching a random change and processing its fetch url. If it fails, fall back to Gerrit host url config.
     *
     * This can be cleaned up once https://code.google.com/p/gerrit/issues/detail?id=2208 is implemented.
     */
    private String getCloneBaseUrl() {
        if (!Strings.isNullOrEmpty(gerritSettings.getCloneBaseUrl())) {
            return gerritSettings.getCloneBaseUrl();
        }
        String url = gerritSettings.getHost();
        try {
            List<ChangeInfo> changeInfos = gerritApi.changes().query()
                .withLimit(1)
                .withOption(ListChangesOption.CURRENT_REVISION)
                .get();
            if (changeInfos.isEmpty()) {
                log.info("ChangeInfo list is empty.");
                return url;
            }
            ChangeInfo changeInfo = Iterables.getOnlyElement(changeInfos);
            FetchInfo fetchInfo = gerritUtil.getFirstFetchInfo(changeInfo);
            if (fetchInfo != null) {
                String projectName = changeInfo.project;
                url = fetchInfo.url.replaceAll("/" + projectName + "$", "");
            }
        } catch (RestApiException e) {
            log.info(e);
        }
        return url;
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
            InputStream commitMessageHook = gerritApi.tools().getCommitMessageHook();
            File targetFile = new File(parentDirectory + '/' + directoryName + "/.git/hooks/commit-msg");
            ByteStreams.copy(commitMessageHook, new FileOutputStream(targetFile));
            //noinspection ResultOfMethodCallIgnored
            targetFile.setExecutable(true);

            NotificationBuilder notification = new NotificationBuilder(
                project,
                "Gerrit Checkout done",
                "Commit-Message Hook has been set up.");
            notificationService.notify(notification);
        } catch (Exception e) {
            log.info(e);
            NotificationBuilder notification = new NotificationBuilder(
                    project,
                    "Couldn't set up Gerrit Commit-Message Hook. Please do it manually.",
                    gerritUtil.getErrorTextFromException(e));
            notificationService.notifyError(notification);
        }
    }

    public static final class Proxy implements CheckoutProvider {
        private final CheckoutProvider delegate;

        public Proxy() {
            delegate = GerritModule.getInstance(GerritCheckoutProvider.class);
        }

        @Override
        public void doCheckout(@NotNull Project project, @Nullable Listener listener) {
            delegate.doCheckout(project, listener);
        }

        @Override
        @NonNls
        public String getVcsName() {
            return delegate.getVcsName();
        }
    }
}
