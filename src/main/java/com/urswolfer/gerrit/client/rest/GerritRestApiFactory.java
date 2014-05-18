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

package com.urswolfer.gerrit.client.rest;

import com.google.gerrit.extensions.api.GerritApi;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import com.urswolfer.gerrit.client.rest.http.HttpRequestExecutor;

/**
 * @author Urs Wolfer
 */
public class GerritRestApiFactory {

    public GerritApi create(GerritAuthData authData,
                            HttpClientBuilderExtension... httpClientBuilderExtensions) {
        return create(authData, new HttpRequestExecutor(), httpClientBuilderExtensions);
    }

    public GerritApi create(GerritAuthData authData,
                            HttpRequestExecutor httpRequestExecutor,
                            HttpClientBuilderExtension... httpClientBuilderExtensions) {
        return new GerritApiImpl(authData, httpRequestExecutor, httpClientBuilderExtensions);
    }
}
