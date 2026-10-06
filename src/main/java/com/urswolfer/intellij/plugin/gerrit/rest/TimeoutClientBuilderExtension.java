/*
 * Copyright 2026 Urs Wolfer
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

import com.intellij.util.io.HttpRequests;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

/**
 * Without a timeout, a request to a Gerrit which cannot be reached (a VPN which is down drops the packets instead of
 * refusing them) blocks its thread for minutes, among them the suggestions in a text field, which run on each
 * keystroke. Connecting gets the time the IDE gives its own requests.
 *
 * The REST client sets a config of its own on each API request, which replaces the default config of the HTTP
 * client, timeouts included. An interceptor sees the config the request ends up with, before it connects.
 *
 * Reading is left as the request has it: Gerrit can take long for a request such as a submit, and one reported as
 * failed while Gerrit completes it would be tried again.
 */
public class TimeoutClientBuilderExtension extends HttpClientBuilderExtension {

    @Override
    public HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
        return httpClientBuilder.addInterceptorLast(new TimeoutInterceptor(HttpRequests.CONNECTION_TIMEOUT));
    }

    static class TimeoutInterceptor implements HttpRequestInterceptor {
        private final int connectTimeoutMs;

        TimeoutInterceptor(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        @Override
        public void process(HttpRequest request, HttpContext context) {
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            clientContext.setRequestConfig(
                RequestConfig.copy(clientContext.getRequestConfig()).setConnectTimeout(connectTimeoutMs).build());
        }
    }
}
