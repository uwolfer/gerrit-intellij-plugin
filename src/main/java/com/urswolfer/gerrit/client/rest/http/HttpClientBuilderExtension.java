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

package com.urswolfer.gerrit.client.rest.http;

import com.urswolfer.gerrit.client.rest.GerritAuthData;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Allows modifying the http client builder. Returned object will be used for further flow.
 *
 * @author Urs Wolfer
 */
public abstract class HttpClientBuilderExtension {

    public HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
        return httpClientBuilder;
    }

    public CredentialsProvider extendCredentialProvider(HttpClientBuilder httpClientBuilder, CredentialsProvider credentialsProvider, GerritAuthData authData) {
        return credentialsProvider;
    }
}
