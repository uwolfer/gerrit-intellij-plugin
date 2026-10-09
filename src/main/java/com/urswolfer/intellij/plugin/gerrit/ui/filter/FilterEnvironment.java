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

package com.urswolfer.intellij.plugin.gerrit.ui.filter;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * What a saved filter value is checked against when it is restored: the value may name something which is gone by
 * now, or need a login which is not there any more.
 */
public interface FilterEnvironment {
    /**
     * Nothing known: every value which depends on it falls back to its default.
     */
    FilterEnvironment UNKNOWN = new FilterEnvironment() {
        @Override
        public boolean isLoggedIn() {
            return false;
        }

        @NotNull
        @Override
        public Map<String, Set<String>> getRemoteBranches() {
            return Map.of();
        }
    };

    /**
     * Whether the project's account has a login, which "self" in a query needs.
     */
    boolean isLoggedIn();

    /**
     * The names of the remote branches of the Gerrit projects of the repositories, by project name.
     */
    @NotNull
    Map<String, Set<String>> getRemoteBranches();
}
