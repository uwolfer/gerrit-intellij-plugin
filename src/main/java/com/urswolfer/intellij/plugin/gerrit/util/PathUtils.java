/*
 *
 *  * Copyright 2013-2014 Urs Wolfer
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.urswolfer.intellij.plugin.gerrit.util;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import git4idea.repo.GitRepository;

import java.io.File;

/**
 * @author Thomas Forrer
 */
public class PathUtils {
    @Inject
    private GerritGitUtil gerritGitUtil;

    public String getRelativePath(Project project, String absoluteFilePath, String gerritProjectName) {
        Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.getRepositoryForGerritProject(project, gerritProjectName);
        if (!gitRepositoryOptional.isPresent()) return null;
        GitRepository repository = gitRepositoryOptional.get();
        VirtualFile root = repository.getRoot();
        return FileUtil.getRelativePath(new File(root.getPath()), new File(absoluteFilePath));
    }

    /**
     * @return a relative path for all files under the project root, or the absolute path for other files
     */
    public String getRelativeOrAbsolutePath(Project project, String absoluteFilePath, String gerritProjectName) {
        String relativePath = getRelativePath(project, absoluteFilePath, gerritProjectName);
        if (relativePath == null || relativePath.contains(File.separator + "..")) {
            return absoluteFilePath;
        }
        return relativePath;
    }

    /**
     * Gerrit handles paths always with a forward slash (/). Windows uses backslash (\), so we need to convert them.
     */
    public static String ensureSlashSeparators(String path) {
        return path.replace('\\', '/');
    }
}
