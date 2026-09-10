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
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;

/**
 * Creates {@link GerritRestApi} instances configured with all IDE specific client builder extensions.
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

    private volatile GerritRestApi gerritApi;

    public static GerritApiProvider getInstance() {
        return ApplicationManager.getApplication().getService(GerritApiProvider.class);
    }

    /**
     * @return the shared api for the configured Gerrit instance; it is backed by the live {@link GerritSettings}, so
     *         it keeps working after the user changed host or credentials.
     */
    public GerritRestApi get() {
        GerritRestApi api = gerritApi;
        if (api == null) {
            synchronized (this) {
                api = gerritApi;
                if (api == null) {
                    api = create(GerritSettings.getInstance());
                    gerritApi = api;
                }
            }
        }
        return api;
    }

    public GerritRestApi create(GerritAuthData gerritAuthData) {
        return gerritRestApiFactory.create(gerritAuthData, clientBuilderExtensions);
    }
}
