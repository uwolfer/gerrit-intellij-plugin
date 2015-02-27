/*
 * Copyright 2013-2015 Urs Wolfer
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

import com.intellij.util.net.ssl.CertificateManager;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * @author Urs Wolfer
 */
public class CertificateManagerClientBuilderExtension extends HttpClientBuilderExtension {

    @Override
    public HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
        HttpClientBuilder builder = super.extend(httpClientBuilder, authData);
        builder.setSslcontext(CertificateManager.getInstance().getSslContext());
        return builder;
    }
}
