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
 * Reading is bounded too. A Gerrit which accepts the connection and never answers would otherwise keep the request
 * for good - its thread, and whatever waits for it: a background task which never ends, the IDE behind a modal
 * progress. A GET changes nothing, so one which fails can safely be sent again: it gets the time the IDE gives its
 * own reads, also in place of the five minutes of the REST client's default config, which the requests it sends ahead
 * of an API request come with. So does the login it sends ahead, a POST which changes nothing either. Anything else,
 * such as a submit, can take Gerrit long, and one reported as failed while Gerrit completes it would be sent again:
 * it gets five minutes.
 */
public class TimeoutClientBuilderExtension extends HttpClientBuilderExtension {

    @Override
    public HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
        return httpClientBuilder.addInterceptorLast(new TimeoutInterceptor(
            HttpRequests.CONNECTION_TIMEOUT, HttpRequests.READ_TIMEOUT, CHANGE_READ_TIMEOUT_MS));
    }

    static final int CHANGE_READ_TIMEOUT_MS = 300000;

    static class TimeoutInterceptor implements HttpRequestInterceptor {
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final int changeReadTimeoutMs;

        TimeoutInterceptor(int connectTimeoutMs, int readTimeoutMs, int changeReadTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.changeReadTimeoutMs = changeReadTimeoutMs;
        }

        @Override
        public void process(HttpRequest request, HttpContext context) {
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            RequestConfig requestConfig = clientContext.getRequestConfig();
            RequestConfig.Builder config = RequestConfig.copy(requestConfig).setConnectTimeout(connectTimeoutMs);
            if (changesNothing(request)) {
                config.setSocketTimeout(readTimeoutMs);
            } else if (requestConfig.getSocketTimeout() < 0) {
                // not left unset: on HTTPS, the socket factory bounds the handshake by making the connect timeout
                // the read timeout of the socket, and the connection only replaces it with a socket timeout which is
                // set - one left unset would bound every read by the connect timeout
                config.setSocketTimeout(changeReadTimeoutMs);
            }
            clientContext.setRequestConfig(config.build());
        }
    }

    static boolean changesNothing(HttpRequest request) {
        String method = request.getRequestLine().getMethod();
        return "GET".equals(method) || "HEAD".equals(method)
            || "POST".equals(method) && path(request).endsWith("/login/");
    }

    // not java.net.URI: with a host saved as "https://gerrit/", the request line is "//login/", which it reads as a
    // host named "login"
    private static String path(HttpRequest request) {
        String uri = request.getRequestLine().getUri();
        int query = uri.indexOf('?');
        return query == -1 ? uri : uri.substring(0, query);
    }
}
