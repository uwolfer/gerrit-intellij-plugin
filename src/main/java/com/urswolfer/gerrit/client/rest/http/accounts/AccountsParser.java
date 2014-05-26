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

package com.urswolfer.gerrit.client.rest.http.accounts;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * @author Thomas Forrer
 */
public class AccountsParser {
    private final Gson gson;

    public AccountsParser(Gson gson) {
        this.gson = gson;
    }

    public AccountInfo parseUserInfo(JsonElement result) throws RestApiException {
        if (result == null) {
            return null;
        }
        if (!result.isJsonObject()) {
            throw new RestApiException(String.format("Unexpected JSON result format: %s", result));
        }
        return gson.fromJson(result, AccountInfo.class);
    }
}
