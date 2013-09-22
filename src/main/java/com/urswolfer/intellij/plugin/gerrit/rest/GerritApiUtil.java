/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.google.common.base.Throwables;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.net.HttpConfigurable;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Parts based on org.jetbrains.plugins.github.GithubApiUtil
 *
 * @author Urs Wolfer
 */
public class GerritApiUtil {

    private static final int CONNECTION_TIMEOUT = 5000;
    private static final Logger LOG = Logger.getInstance(GerritApiUtil.class);

    @Nullable
    public static JsonElement getRequest(@NotNull String host, @NotNull String login, @NotNull String password,
                                                         @NotNull String path) {
        return request(host, login, password, path, null, false);
    }

    @Nullable
    public static JsonElement postRequest(@NotNull String host, @Nullable String login, @Nullable String password,
                                                          @NotNull String path, @Nullable String requestBody) {
        return request(host, login, password, path, requestBody, true);
    }

    @Nullable
    private static JsonElement request(@NotNull String host, @Nullable String login, @Nullable String password,
                                                       @NotNull String path, @Nullable String requestBody, boolean post) {
        HttpMethod method = null;
        try {
            method = doREST(host, login, password, path, requestBody, post);
            String resp = method.getResponseBodyAsString();
            if (method.getStatusCode() != 200) {
                String message = String.format("Request not successful. Status-Code: %s, Message: %s.", method.getStatusCode(), method.getStatusText());
                LOG.warn(message);
                throw new RuntimeException(message);
            }
            if (resp == null) {
                String message = String.format("Unexpectedly empty response: %s.", resp);
                LOG.warn(message);
                throw new RuntimeException(message);
            }
            return parseResponse(resp);
        } catch (IOException e) {
            LOG.warn(String.format("Request failed: %s", e.getMessage()), e);
            throw Throwables.propagate(e);
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }

    @NotNull
    private static HttpMethod doREST(@NotNull String host, @Nullable String login, @Nullable String password, @NotNull String path,
                                     @Nullable final String requestBody, final boolean post) throws IOException {
        HttpClient client = getHttpClient(login, password);
        String uri = host + path;
        return SslSupport.getInstance().executeSelfSignedCertificateAwareRequest(client, uri,
                new ThrowableConvertor<String, HttpMethod, IOException>() {
                    @Override
                    public HttpMethod convert(String uri) throws IOException {
                        if (post) {
                            PostMethod method = new PostMethod(uri);
                            if (requestBody != null) {
                                method.setRequestEntity(new StringRequestEntity(requestBody, "application/json", "UTF-8"));
                            }
                            return method;
                        }
                        return new GetMethod(uri);
                    }
                });
    }

    public static String getApiUrl() {
        return GerritSettings.getInstance().getHost();
    }

    @NotNull
    private static HttpClient getHttpClient(@Nullable final String login, @Nullable final String password) {
        final HttpClient client = new HttpClient();
        HttpConnectionManagerParams params = client.getHttpConnectionManager().getParams();
        params.setConnectionTimeout(CONNECTION_TIMEOUT); //set connection timeout (how long it takes to connect to remote host)
        params.setSoTimeout(CONNECTION_TIMEOUT); //set socket timeout (how long it takes to retrieve data from remote host)

        client.getParams().setContentCharset("UTF-8");
        // Configure proxySettings if it is required
        final HttpConfigurable proxySettings = HttpConfigurable.getInstance();
        if (proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST)) {
            client.getHostConfiguration().setProxy(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
            if (proxySettings.PROXY_AUTHENTICATION) {
                client.getState().setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN,
                        proxySettings.getPlainProxyPassword()));
            }
        }
        if (login != null && password != null) {
            client.getParams().setCredentialCharset("UTF-8");
            client.getParams().setAuthenticationPreemptive(true);
            client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(login, password));
        }
        return client;
    }


    @NotNull
    private static JsonElement parseResponse(@NotNull String response) {
        try {
            return new JsonParser().parse(response);
        } catch (JsonSyntaxException jse) {
            throw new RuntimeException(String.format("Couldn't parse response: %n%s", response), jse);
        }
    }

}
