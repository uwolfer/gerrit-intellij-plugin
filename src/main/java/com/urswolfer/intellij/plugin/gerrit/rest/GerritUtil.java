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

package com.urswolfer.intellij.plugin.gerrit.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Parts based on org.jetbrains.plugins.github.GithubUtil
 *
 * @author Urs Wolfer
 */
public class GerritUtil {

    public static final Logger LOG = Logger.getInstance("gerrit");

    @NotNull
    public static List<ChangeInfo> getChanges(@NotNull String url, @NotNull String login, @NotNull String password) {
        final String request = "/changes/";
        try {
            JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
            if (result == null) {
                return Collections.emptyList();
            }
            return parseChangeInfos(result);
        } catch (IOException e) {
            LOG.error(e);
            return Collections.emptyList();
        }
    }

    @NotNull
    public static ChangeInfo getChangeDetails(@NotNull String url, @NotNull String login, @NotNull String password, @NotNull String changeNr) {
        final String request = "/changes/?q=" + changeNr + "&o=CURRENT_REVISION";
        try {
            JsonElement result = GerritApiUtil.getRequest(url, login, password, request);
            if (result == null) {
                throw new RuntimeException("No valid result available.");
            }
            return parseSingleChangeInfos(result.getAsJsonArray().get(0).getAsJsonObject());
        } catch (IOException e) {
            LOG.error(e);
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static List<ChangeInfo> parseChangeInfos(@NotNull JsonElement result) {
        if (!result.isJsonArray()) {
            LOG.assertTrue(result.isJsonObject(), String.format("Unexpected JSON result format: %s", result));
            return Collections.singletonList(parseSingleChangeInfos(result.getAsJsonObject()));
        }

        List<ChangeInfo> changeInfoList = new ArrayList<ChangeInfo>();
        for (JsonElement element : result.getAsJsonArray()) {
            LOG.assertTrue(element.isJsonObject(),
                    String.format("This element should be a JsonObject: %s%nTotal JSON response: %n%s", element, result));
            changeInfoList.add(parseSingleChangeInfos(element.getAsJsonObject()));
        }
        return changeInfoList;
    }

    @NotNull
    private static ChangeInfo parseSingleChangeInfos(@NotNull JsonObject result) {
        final Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd hh:mm:ss")
                .create();
        return gson.fromJson(result, ChangeInfo.class);
    }
}
