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

package com.urswolfer.gerrit.client.rest;

import com.google.common.base.Strings;

/**
 * @author Thomas Forrer
 */
public interface GerritAuthData {
    String getLogin();

    String getPassword();

    String getHost();

    boolean isLoginAndPasswordAvailable();

    public final class Basic implements GerritAuthData {
        private final String host;
        private final String login;
        private final String password;

        public Basic(String host) {
            this.host = host;
            this.login = "";
            this.password = "";
        }

        public Basic(String host, String login, String password) {
            this.host = host;
            this.login = login;
            this.password = password;
        }

        @Override
        public String getLogin() {
            return login;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getHost() {
            return host;
        }

        @Override
        public boolean isLoginAndPasswordAvailable() {
            return !Strings.isNullOrEmpty(getLogin()) && !Strings.isNullOrEmpty(getPassword());
        }
    }
}
