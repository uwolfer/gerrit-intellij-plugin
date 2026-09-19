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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccountAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates {@link GerritRestApi} instances set up with all IDE specific client builder extensions.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.APP)
public final class GerritApiProvider {

    private final GerritRestApiFactory gerritRestApiFactory = new GerritRestApiFactory();
    private final HttpClientBuilderExtension[] clientBuilderExtensions = {
        new CertificateManagerClientBuilderExtension(),
        new LoggerHttpClientBuilderExtension(),
        new ProxyHttpClientBuilderExtension(),
        new UserAgentClientBuilderExtension()
    };

    private final Map<String, GerritRestApi> apiByAccountId = new ConcurrentHashMap<>();

    public static GerritApiProvider getInstance() {
        return ApplicationManager.getApplication().getService(GerritApiProvider.class);
    }

    /**
     * @return the api of the account which is used where nothing picks one
     */
    public GerritRestApi get() {
        GerritAccount account = GerritAccounts.getInstance().getDefaultAccount();
        return get(account != null ? account.id : "");
    }

    /**
     * @return the api of this account; one is kept per account rather than per project, so the projects which share
     *         an account share its connections too
     */
    public GerritRestApi get(@Nullable GerritAccount account) {
        return get(account != null ? account.id : "");
    }

    private GerritRestApi get(String accountId) {
        // the auth data reads the account per call, so the api stays right while the user edits it
        return apiByAccountId.computeIfAbsent(accountId, id -> create(new GerritAccountAuthData(id)));
    }

    public GerritRestApi create(GerritAuthData gerritAuthData) {
        return gerritRestApiFactory.create(gerritAuthData, clientBuilderExtensions);
    }
}
