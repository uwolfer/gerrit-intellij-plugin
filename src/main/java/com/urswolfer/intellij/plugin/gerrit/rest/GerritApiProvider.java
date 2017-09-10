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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;

/**
 * @author Urs Wolfer
 */
public class GerritApiProvider implements Provider<GerritRestApi> {

    @Inject
    private GerritSettings gerritSettings;
    @Inject
    private CertificateManagerClientBuilderExtension certificateManagerClientBuilderExtension;
    @Inject
    private LoggerHttpClientBuilderExtension loggerHttpClientBuilderExtension;
    @Inject
    private ProxyHttpClientBuilderExtension proxyHttpClientBuilderExtension;
    @Inject
    private UserAgentClientBuilderExtension userAgentClientBuilderExtension;
    @Inject
    private GerritRestApiFactory gerritRestApiFactory;

    @Override
    public GerritRestApi get() {
        return gerritRestApiFactory.create(
            gerritSettings,
            certificateManagerClientBuilderExtension,
            loggerHttpClientBuilderExtension,
            proxyHttpClientBuilderExtension,
            userAgentClientBuilderExtension);
    }
}
