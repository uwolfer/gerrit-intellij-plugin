package com.urswolfer.intellij.plugin.gerrit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: thomas
 * Date: 11/5/13
 * Time: 8:22 PM
 */
public interface GerritAuthData {
    @Nullable
    String getLogin();

    @NotNull
    String getPassword();

    @NotNull
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
