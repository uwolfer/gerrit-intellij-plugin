/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.repo.ClonePathProvider;
import com.intellij.dvcs.ui.CloneDvcsValidationUtils;
import com.intellij.dvcs.ui.SelectChildTextFieldWithBrowseButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.ui.VcsCloneComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboBoxCompositeEditor;
import com.intellij.ui.JBColor;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.git.GerritCommitMsgHook;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritRemotes;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import git4idea.GitUtil;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import git4idea.remote.GitRememberedInputs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Gerrit specific panel of the "Get from Version Control" dialog. It offers all projects of the configured Gerrit
 * instance as clone URLs and sets up the Gerrit commit-message hook once a project has been cloned.
 *
 * Parts based on git4idea.ui.GitCloneDialogComponent
 *
 * @author Urs Wolfer
 */
public class GerritCloneComponent implements VcsCloneComponent {
    private static final Logger LOG = Logger.getInstance(GerritCloneComponent.class);

    private static final Comparator<ProjectInfo> ID_ORDERING =
        Comparator.comparing(projectInfo -> projectInfo.id);

    private final Project project;
    private final ModalityState modalityState;
    private final VcsCloneDialogComponentStateListener dialogStateListener;

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final NotificationService notificationService = NotificationService.getInstance();

    private final DvcsRememberedInputs rememberedInputs = GitRememberedInputs.getInstance();
    private final CollectionComboBoxModel<String> urlModel = new CollectionComboBoxModel<>();
    private final ComboBox<String> urlComboBox = new ComboBox<>();
    private final TextFieldWithAutoCompletion<String> urlField;
    private final JLabel urlSpinner = new JLabel(new AnimatedIcon.Default());
    private final SelectChildTextFieldWithBrowseButton directoryField;
    private final JBLabel statusLabel = new JBLabel();
    private final JPanel mainPanel;

    // filled on a pooled thread and read on the event dispatch thread, so replaced whole rather than changed
    private volatile Map<String, GerritAccount> accountByRepositoryUrl = Collections.emptyMap();

    private boolean projectsRequested;
    private boolean itemSetFromPopup;
    private boolean disposed;

    public GerritCloneComponent(Project project,
                                ModalityState modalityState,
                                VcsCloneDialogComponentStateListener dialogStateListener) {
        this.project = project;
        this.modalityState = modalityState;
        this.dialogStateListener = dialogStateListener;

        directoryField = new SelectChildTextFieldWithBrowseButton(
            ClonePathProvider.defaultParentDirectoryPath(project, rememberedInputs));
        FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        fileChooserDescriptor.setShowFileSystemRoots(true);
        fileChooserDescriptor.setHideIgnored(false);
        directoryField.addBrowseFolderListener("Destination Directory",
            "Select the directory the Gerrit project gets cloned into", project, fileChooserDescriptor);

        // an editor with completion lets the offered Gerrit projects be filtered by typing a part of their name
        urlField = TextFieldWithAutoCompletion.create(project, urlModel.getItems(), false, "");
        urlField.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
                if (!itemSetFromPopup) {
                    urlComboBox.setPopupVisible(false);
                }
                directoryField.trySetChildPath(defaultDirectoryPath(getUrl()));
                updateOkActionState();
            }
        });
        urlSpinner.setVisible(false);
        ComboBoxCompositeEditor<String, TextFieldWithAutoCompletion<String>> urlEditor =
            ComboBoxCompositeEditor.withComponents(urlField, urlSpinner);
        urlEditor.onSetItem(new BiConsumer<String, TextFieldWithAutoCompletion<String>>() {
            @Override
            public void accept(String item, TextFieldWithAutoCompletion<String> field) {
                // same as the default handler, it just needs to be told apart from the user typing
                itemSetFromPopup = true;
                try {
                    field.setText(item == null ? "" : item);
                } finally {
                    itemSetFromPopup = false;
                }
            }
        });
        urlComboBox.setEditable(true);
        urlComboBox.setEditor(urlEditor);
        urlComboBox.setModel(urlModel);
        // the popup of the combo box lists all projects; while typing, the filtered completion popup is the one to show
        urlComboBox.setKeySelectionManager(new JComboBox.KeySelectionManager() {
            @Override
            public int selectionForKey(char key, ComboBoxModel model) {
                return -1;
            }
        });
        // the combo box would size itself to its longest entry otherwise
        urlComboBox.setMinimumAndPreferredWidth(JBUI.scale(400));

        statusLabel.setForeground(JBColor.RED);
        statusLabel.setVisible(false);

        // FormBuilder does not stretch combo boxes, so the combo box gets wrapped into a panel which it does stretch;
        // its label needs to be built here, the one of FormBuilder would point at the wrapper instead of the combo box
        JBLabel urlLabel = new JBLabel("URL:");
        urlLabel.setLabelFor(urlComboBox);

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(urlLabel, JBUI.Panels.simplePanel(urlComboBox))
            .addLabeledComponent("Directory:", directoryField)
            .addComponentToRightColumn(statusLabel)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
        Insets insets = UIUtil.PANEL_REGULAR_INSETS;
        mainPanel.setBorder(JBUI.Borders.empty(insets.top / 2, insets.left, insets.bottom, insets.right));
    }

    @Override
    public JComponent getView() {
        return mainPanel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return urlField;
    }

    @Override
    public String getOkButtonText() {
        return VcsBundle.message("clone.dialog.clone.button");
    }

    @Override
    public boolean isOkEnabled() {
        return !getUrl().isEmpty();
    }

    @Override
    public List<ValidationInfo> doValidateAll() {
        List<ValidationInfo> validationInfos = new ArrayList<>();
        ValidationInfo directoryValidation =
            CloneDvcsValidationUtils.checkDirectory(directoryField.getText(), directoryField.getTextField());
        if (directoryValidation != null) {
            validationInfos.add(directoryValidation);
        }
        ValidationInfo urlValidation = CloneDvcsValidationUtils.checkRepositoryURL(urlComboBox, getUrl());
        if (urlValidation != null) {
            validationInfos.add(urlValidation);
        }
        return validationInfos;
    }

    @Override
    public void onComponentSelected(@NotNull VcsCloneDialogComponentStateListener dialogStateListener) {
        dialogStateListener.onOkActionEnabled(isOkEnabled());
        loadAvailableProjects();
    }

    @Override
    public void doClone(@NotNull Project project, @NotNull CheckoutProvider.Listener listener) {
        if (!GerritGitUtil.getInstance().testGitExecutable(project)) {
            return;
        }
        FileDocumentManager.getInstance().saveAllDocuments();

        Path directory = Paths.get(getDirectory()).toAbsolutePath();
        Path parent = directory.getParent();
        if (parent == null) {
            notifyCloneError(project, "Destination directory has no parent directory: " + directory);
            return;
        }
        ValidationInfo destinationValidation = CloneDvcsValidationUtils.createDestination(parent.toString());
        if (destinationValidation != null) {
            notifyCloneError(project, destinationValidation.message);
            return;
        }

        LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
        VirtualFile destinationParent = localFileSystem.findFileByIoFile(parent.toFile());
        if (destinationParent == null) {
            destinationParent = localFileSystem.refreshAndFindFileByIoFile(parent.toFile());
        }
        if (destinationParent == null) {
            notifyCloneError(project, "Destination directory does not exist: " + parent);
            return;
        }

        String sourceRepositoryUrl = getUrl();
        String directoryName = directory.getFileName().toString();
        String parentDirectory = parent.toString();

        CheckoutProvider.Listener listenerWrapper =
            addCommitMsgHookListener(listener, directoryName, parentDirectory, project,
                accountFor(sourceRepositoryUrl));
        GitCheckoutProvider.clone(project, Git.getInstance(), listenerWrapper, destinationParent,
            sourceRepositoryUrl, directoryName, parentDirectory);

        rememberedInputs.addUrl(sourceRepositoryUrl);
        rememberedInputs.setCloneParentDir(parentDirectory);
    }

    /**
     * {@link VcsCloneComponent} dropped the project parameter of {@code doClone} with 2022.1. Both signatures are
     * implemented, so that the one declared by the platform in use gets called.
     */
    public void doClone(@NotNull CheckoutProvider.Listener listener) {
        doClone(project, listener);
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    private String getUrl() {
        // the validation accepts a pasted clone command and strips it, but its sanitizing is not public API; without
        // doing the same here, such an entry would be passed to Git as the repository to clone
        String url = StringUtil.trimStart(urlField.getText().trim(), "git clone").trim();
        return StringUtil.trimStart(url, "hg clone").trim();
    }

    private String getDirectory() {
        return directoryField.getText().trim();
    }

    private String defaultDirectoryPath(String url) {
        return StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, url), GitUtil.DOT_GIT);
    }

    private void updateOkActionState() {
        dialogStateListener.onOkActionEnabled(isOkEnabled());
    }

    /**
     * Offers all Gerrit projects as clone URLs. The projects are loaded in background since the dialog can get opened
     * (and this component can get selected) without any user interaction with Gerrit.
     */
    private void loadAvailableProjects() {
        if (projectsRequested) {
            return;
        }
        projectsRequested = true;
        if (getAccountsWithHost().isEmpty()) {
            setErrorText("Gerrit is not set up; the repository URL needs to be entered manually.");
            return;
        }
        showSpinner(true);
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> unreachable = new ArrayList<>();
                    final List<String> repositoryUrls = getRepositoryUrls(unreachable);
                    invokeLaterIfNotDisposed(new Runnable() {
                        @Override
                        public void run() {
                            // keep what has been entered so far, filling the model resets the editor otherwise
                            urlComboBox.setSelectedItem(urlField.getText());
                            urlModel.replaceAll(repositoryUrls);
                            urlField.setVariants(urlModel.getItems());
                            // the projects of the others are still worth offering, but the missing ones must not
                            // look as though they did not exist
                            setErrorText(unreachable.isEmpty() ? null
                                : "Couldn't reach " + String.join(", ", unreachable));
                            showSpinner(false);
                        }
                    });
                } catch (Exception e) {
                    LOG.info(e);
                    final String errorText = gerritUtil.getErrorTextFromException(e);
                    invokeLaterIfNotDisposed(new Runnable() {
                        @Override
                        public void run() {
                            setErrorText("Couldn't get the list of Gerrit repositories: " + errorText);
                            showSpinner(false);
                        }
                    });
                }
            }
        });
    }

    /**
     * Offers the projects of every account rather than of one of them: which account a clone belongs to follows
     * from the repository the user picks, and there is no project yet whose account could be asked.
     */
    private List<String> getRepositoryUrls(List<String> unreachable) throws RestApiException {
        Map<String, GerritAccount> accountByUrl = new LinkedHashMap<>();
        RestApiException lastError = null;
        for (GerritAccount account : getAccountsWithHost()) {
            try {
                String url = getCloneBaseUrl(account);
                List<ProjectInfo> orderedProjects =
                    new ArrayList<>(GerritApiProvider.getInstance().get(account).projects().list().get());
                orderedProjects.sort(ID_ORDERING);
                for (ProjectInfo projectInfo : orderedProjects) {
                    // two logins on one instance list the same projects; the first account listing one keeps it
                    accountByUrl.putIfAbsent(url + '/' + Url.decode(projectInfo.id), account);
                }
            } catch (RestApiException e) { // one unreachable instance must not hide the projects of the others
                LOG.info("Could not list the projects of " + account, e);
                unreachable.add(account.toString()); // the reason is logged: the error text is cut at one line
                lastError = e;
            }
        }
        if (accountByUrl.isEmpty() && lastError != null) {
            throw lastError;
        }
        accountByRepositoryUrl = accountByUrl;
        return new ArrayList<>(accountByUrl.keySet());
    }

    /**
     * An account seeded from a login without a url has no instance to list projects of.
     */
    private static List<GerritAccount> getAccountsWithHost() {
        List<GerritAccount> accounts = new ArrayList<>(GerritAccounts.getInstance().getAccounts());
        accounts.removeIf(account -> account.host.isEmpty());
        return accounts;
    }

    /**
     * @return the account the repository url was offered for; for a typed url an account on its instance, any of
     *         them as the hook is the same whoever fetches it, or the only account there is, as before there were
     *         several; {@code null} rather than a guess
     */
    @Nullable
    private GerritAccount accountFor(String repositoryUrl) {
        GerritAccount account = accountByRepositoryUrl.get(repositoryUrl);
        if (account != null) {
            return account;
        }
        List<GerritAccount> accounts = getAccountsWithHost();
        return accounts.stream().filter(candidate -> candidate.isOnInstance(repositoryUrl)).findFirst()
            .orElse(accounts.size() == 1 ? accounts.get(0) : null);
    }

    /**
     * The clone base url of the preferences, else the one the server advertises, else the host.
     */
    private String getCloneBaseUrl(GerritAccount account) {
        String cloneBaseUrl = account.cloneBaseUrl;
        if (cloneBaseUrl != null && !cloneBaseUrl.isEmpty()) {
            return cloneBaseUrl;
        }
        try {
            ServerInfo info = GerritApiProvider.getInstance().get(account).config().server().getInfo();
            String url = GerritRemotes.getCloneBaseUrl(info != null && info.download != null ? info.download.schemes : null);
            if (url != null) {
                return url;
            }
        } catch (RestApiException e) {
            LOG.info(e);
        }
        return account.host;
    }

    /*
     * Since this is a listener which needs to be executed in any case, it cannot be a normal checkout-listener.
     * Checkout-listeners only get executed when "previous" listener got not executed (returns false).
     * Example: If user decides to setup a new project from newly created checkout, our listener does not get executed.
     */
    private CheckoutProvider.Listener addCommitMsgHookListener(final CheckoutProvider.Listener listener,
                                                               final String directoryName,
                                                               final String parentDirectory,
                                                               final Project project,
                                                               final GerritAccount account) {
        return new CheckoutProvider.Listener() {
            @Override
            public void directoryCheckedOut(File directory, VcsKey vcs) {
                if (ApplicationManager.getApplication().isDispatchThread()) {
                    // Up to 2022.1 the platform calls this on the event dispatch thread. The hook must be in place
                    // before the project opens, so the download is waited for, but behind a progress dialog which
                    // can be cancelled: the hook is still written once it arrives.
                    Future<?> hook = ApplicationManager.getApplication().executeOnPooledThread(
                        () -> setupCommitMsgHook(parentDirectory, directoryName, project, account));
                    // the progress dialog cannot be registered with a project closed during the clone
                    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> awaitCancellably(hook),
                        "Setting Up Gerrit Commit-Message Hook...", true, project.isDisposed() ? null : project);
                } else {
                    setupCommitMsgHook(parentDirectory, directoryName, project, account);
                }

                listener.directoryCheckedOut(directory, vcs);
            }

            @Override
            public void checkoutCompleted() {
                listener.checkoutCompleted();
            }
        };
    }

    private static void awaitCancellably(Future<?> future) {
        while (true) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
                return;
            } catch (TimeoutException e) {
                ProgressManager.checkCanceled();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                // setupCommitMsgHook reports what it expects itself, anything else would get lost here
                LOG.error(e.getCause());
                return;
            }
        }
    }

    /**
     * @param account resolved while the dialog was still up: the checkout finishes long after it has closed, and
     *                the url it was cloned from is no longer on screen to be asked
     */
    private void setupCommitMsgHook(String parentDirectory, String directoryName, Project project,
                                    @Nullable GerritAccount account) {
        if (account == null) {
            notificationService.notifyError(new NotificationBuilder(project,
                "Couldn't set up Gerrit Commit-Message Hook. Please do it manually.",
                "The repository is on none of the Gerrit instances set up."));
            return;
        }
        try {
            GerritCommitMsgHook.getInstance().install(
                Paths.get(parentDirectory, directoryName, ".git", "hooks", "commit-msg"), account);

            NotificationBuilder notification = new NotificationBuilder(
                project,
                "Gerrit Checkout done",
                "Commit-Message Hook has been set up.");
            notificationService.notify(notification);
        } catch (Exception e) {
            LOG.info(e);
            NotificationBuilder notification = new NotificationBuilder(
                    project,
                    "Couldn't set up Gerrit Commit-Message Hook. Please do it manually.",
                    gerritUtil.getErrorTextFromException(e));
            notificationService.notifyError(notification);
        }
    }

    private void notifyCloneError(Project project, String message) {
        LOG.info("Gerrit clone failed: " + message);
        NotificationBuilder notification = new NotificationBuilder(project, "Couldn't clone Gerrit repository", message);
        notificationService.notifyError(notification);
    }

    private void showSpinner(boolean visible) {
        urlSpinner.setVisible(visible);
        urlComboBox.revalidate();
        urlComboBox.repaint();
    }

    private void setErrorText(String text) {
        if (text == null) {
            statusLabel.setVisible(false);
            return;
        }
        statusLabel.setText(StringUtil.shortenTextWithEllipsis(text, 120, 0, true));
        statusLabel.setVisible(true);
    }

    private void invokeLaterIfNotDisposed(final Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                if (disposed) {
                    return;
                }
                runnable.run();
            }
        }, modalityState);
    }
}
