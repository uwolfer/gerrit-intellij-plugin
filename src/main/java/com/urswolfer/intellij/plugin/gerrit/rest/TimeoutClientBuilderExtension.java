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

import java.util.Arrays;

/**
 * Without a timeout, a request to a Gerrit which cannot be reached (a VPN which is down drops the packets instead of
 * refusing them) blocks its thread for minutes, among them the suggestions in a text field, which run on each
 * keystroke. Connecting gets the time the IDE gives its own requests.
 *
 * The REST client sets a config of its own on each API request, which replaces the default config of the HTTP
 * client, timeouts included. An interceptor sees the config the request ends up with, before it connects.
 *
 * Reading is left as the request has it: Gerrit can take long for a request such as a submit, and one reported as
 * failed while Gerrit completes it would be tried again. The suggestions of the text fields are the exception: they
 * are only read, on every keystroke, and nobody waits for an old one. Each that hangs - a Gerrit which accepts the
 * connection and never answers - would keep one of the few connections of the account, until every request of the
 * account waits for one, for good. They get the time the IDE gives its own reads, and so do the session check and
 * the login the REST client sends ahead of a request on the same thread: they change nothing, so ahead of a submit
 * too, one which fails is just tried again.
 */
public class TimeoutClientBuilderExtension extends HttpClientBuilderExtension {

    @Override
    public HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
        return httpClientBuilder.addInterceptorLast(
            new TimeoutInterceptor(HttpRequests.CONNECTION_TIMEOUT, HttpRequests.READ_TIMEOUT));
    }

    static class TimeoutInterceptor implements HttpRequestInterceptor {
        private final int connectTimeoutMs;
        private final int readTimeoutMs;

        TimeoutInterceptor(int connectTimeoutMs, int readTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
        }

        @Override
        public void process(HttpRequest request, HttpContext context) {
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            RequestConfig.Builder config =
                RequestConfig.copy(clientContext.getRequestConfig()).setConnectTimeout(connectTimeoutMs);
            if (isSuggestion(request) || isAuthentication(request)) {
                config.setSocketTimeout(readTimeoutMs);
            }
            clientContext.setRequestConfig(config.build());
        }
    }

    /** /changes/{id}/suggest_reviewers, and /accounts/?suggest */
    static boolean isSuggestion(HttpRequest request) {
        if (!"GET".equals(request.getRequestLine().getMethod())) {
            return false;
        }
        String path = path(request);
        String query = query(request);
        return path.endsWith("/suggest_reviewers")
            || path.endsWith("/accounts/")
                && Arrays.stream(query.split("&")).anyMatch(p -> p.equals("suggest") || p.startsWith("suggest="));
    }

    /** The session check and the login of the REST client: /accounts/self and /login/ */
    static boolean isAuthentication(HttpRequest request) {
        String path = path(request);
        return path.endsWith("/login/")
            || "GET".equals(request.getRequestLine().getMethod()) && path.endsWith("/accounts/self");
    }

    // not java.net.URI: with a host saved as "https://gerrit/", the request line is "//accounts/...", which it reads
    // as a host named "accounts"
    private static String path(HttpRequest request) {
        String uri = request.getRequestLine().getUri();
        int query = uri.indexOf('?');
        return query == -1 ? uri : uri.substring(0, query);
    }

    private static String query(HttpRequest request) {
        String uri = request.getRequestLine().getUri();
        int query = uri.indexOf('?');
        return query == -1 ? "" : uri.substring(query + 1);
    }
}
