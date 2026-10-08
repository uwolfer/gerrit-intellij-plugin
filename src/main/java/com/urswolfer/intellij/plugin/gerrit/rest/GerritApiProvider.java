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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccountAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.GerritAccountsListener;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates {@link GerritRestApi} instances set up with all IDE specific client builder extensions.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.APP)
public final class GerritApiProvider implements Disposable {

    private final GerritRestApiFactory gerritRestApiFactory = new GerritRestApiFactory();
    private final HttpClientBuilderExtension[] clientBuilderExtensions = {
        new CertificateManagerClientBuilderExtension(),
        new LoggerHttpClientBuilderExtension(),
        new ProxyHttpClientBuilderExtension(),
        new TimeoutClientBuilderExtension(),
        new UserAgentClientBuilderExtension()
    };

    /**
     * By account, host and login: a client keeps the session it logged in with, which must not outlive a change of
     * either, and has no use once its account is gone.
     */
    private final Map<String, GerritRestApi> apiByIdentity = new ConcurrentHashMap<>();

    public GerritApiProvider() {
        ApplicationManager.getApplication().getMessageBus().connect(this)
            .subscribe(GerritAccountsListener.TOPIC, this::dropStale);
    }

    @Override
    public void dispose() {
    }

    private void dropStale() {
        Set<String> current = new HashSet<>();
        for (GerritAccount account : GerritAccounts.getInstance().getAccounts()) {
            current.add(account.getIdentity());
        }
        apiByIdentity.keySet().removeIf(identity -> !current.contains(identity));
    }

    public static GerritApiProvider getInstance() {
        return ApplicationManager.getApplication().getService(GerritApiProvider.class);
    }

    /**
     * @return the api of this account; one is kept per account rather than per project, so the projects which share
     *         an account share its connections too
     */
    public GerritRestApi get(@Nullable GerritAccount account) {
        String id = account != null ? account.id : "";
        // the auth data reads the account per call, so the api stays right while the user edits its password
        String identity = account != null ? account.getIdentity() : "";
        return apiByIdentity.computeIfAbsent(identity, key -> create(new GerritAccountAuthData(id)));
    }

    public GerritRestApi create(GerritAuthData gerritAuthData) {
        return gerritRestApiFactory.create(gerritAuthData, clientBuilderExtensions);
    }
}
