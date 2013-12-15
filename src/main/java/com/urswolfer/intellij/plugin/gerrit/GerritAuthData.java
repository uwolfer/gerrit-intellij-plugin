/*
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

package com.urswolfer.intellij.plugin.gerrit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Thomas Forrer
 */
public interface GerritAuthData {
    String getLogin();

    String getPassword();

    String getHost();

    final class TempGerritAuthData implements GerritAuthData {
        @NotNull
        private final String host;
        @NotNull
        private final String login;
        @NotNull
        private final String password;

        public TempGerritAuthData(@NotNull String host, @NotNull String login, @NotNull String password) {
            this.host = host;
            this.login = login;
            this.password = password;
        }

        @Nullable
        @Override
        public String getLogin() {
            return login;
        }

        @NotNull
        @Override
        public String getPassword() {
            return password;
        }

        @NotNull
        @Override
        public String getHost() {
            return host;
        }
    }
}
