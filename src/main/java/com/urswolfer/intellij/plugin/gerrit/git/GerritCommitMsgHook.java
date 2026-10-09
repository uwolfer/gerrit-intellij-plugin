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

package com.urswolfer.intellij.plugin.gerrit.git;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The Gerrit commit-msg hook of a repository, which adds the Change-Id to every commit message.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.APP)
public final class GerritCommitMsgHook {
    private static final String HOOK_NAME = "commit-msg";

    public static GerritCommitMsgHook getInstance() {
        return ApplicationManager.getApplication().getService(GerritCommitMsgHook.class);
    }

    /**
     * Asks Git rather than assuming {@code .git/hooks}: {@code core.hooksPath}, worktrees and submodules all
     * put the hooks elsewhere. Runs a Git process, so not for the event dispatch thread.
     *
     * @return {@code null} when Git does not tell
     */
    @Nullable
    public Path getHookFile(@NotNull GitRepository repository) {
        GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REV_PARSE);
        handler.addParameters("--git-path", "hooks");
        handler.setSilent(true);
        GitCommandResult result = Git.getInstance().runCommand(handler);
        if (!result.success() || result.getOutput().isEmpty()) {
            return null;
        }
        return resolveHookFile(Paths.get(repository.getRoot().getPath()), result.getOutput().get(0));
    }

    /**
     * {@code git rev-parse --git-path} answers relative to the directory it ran in, unless the path is absolute
     * (as {@code core.hooksPath} can be).
     */
    @VisibleForTesting
    static Path resolveHookFile(Path repositoryRoot, String hooksDirectory) {
        return repositoryRoot.resolve(hooksDirectory.trim()).resolve(HOOK_NAME);
    }

    /**
     * Existence is all it checks: a hook which is there may be the user's own, and is not ours to replace.
     */
    public boolean isInstalled(@NotNull Path hookFile) {
        return Files.exists(hookFile);
    }

    /**
     * Written aside and moved into place, so that a download which fails half way leaves no truncated file behind:
     * {@link #isInstalled} would take it for the hook and the warning would never come back. Never replaces a file
     * which appeared in the meantime.
     */
    public void install(@NotNull Path hookFile, @NotNull GerritAccount account) throws Exception {
        Path directory = hookFile.getParent();
        Files.createDirectories(directory);
        Path download = Files.createTempFile(directory, HOOK_NAME, ".download");
        try {
            try (InputStream hook = GerritApiProvider.getInstance().get(account).tools().getCommitMessageHook();
                 OutputStream target = Files.newOutputStream(download)) {
                hook.transferTo(target);
            }
            if (Files.size(download) == 0) {
                throw new IOException(GerritBundle.message("hook.error.empty"));
            }
            //noinspection ResultOfMethodCallIgnored
            download.toFile().setExecutable(true);
            Files.move(download, hookFile);
        } finally {
            Files.deleteIfExists(download);
        }
    }
}
