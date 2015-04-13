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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.google.inject.AbstractModule;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;

/**
 * @author Thomas Forrer
 */
public class GerritRestModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CertificateManagerClientBuilderExtension.class);
        bind(ProxyHttpClientBuilderExtension.class);
        bind(UserAgentClientBuilderExtension.class);
        bind(GerritRestApiFactory.class);
        bind(GerritRestApi.class).toProvider(new GerritApiProvider());
    }
}
