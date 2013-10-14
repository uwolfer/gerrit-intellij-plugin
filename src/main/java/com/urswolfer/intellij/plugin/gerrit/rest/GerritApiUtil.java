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
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
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
                String message = String.format("Request not successful. Message: %s. Status-Code: %s.", method.getStatusText(), method.getStatusCode());
                LOG.warn(message);
                throw new HttpStatusException(method.getStatusCode(), method.getStatusText(), message);
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
    public static HttpMethod doREST(@NotNull String host, @Nullable String login, @Nullable String password, @NotNull String path,
                                     @Nullable final String requestBody, final boolean post) throws IOException {
        HttpClient client = getHttpClient(login, password);
        tryGerritHttpAuth(host, client);
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

    /*
     * Try to authenticate against Gerrit instances with HTTP auth (not OAuth or something like that).
     * In case of success we get a GerritAccount cookie. In that case no more login credentials need to be sent as
     * long as we use the *same* HTTP client. Even requests against authenticated rest api (/a) will be processed
     * with the GerritAccount cookie.
     *
     * This is a workaround for "double" HTTP authentication (i.e. reverse proxy *and* Gerrit do HTTP authentication
     * for rest api (/a)).
     *
     * Following old notes from README about the issue:
     * If you have correctly set up a HTTP Password in Gerrit, but still have authentication issues, your Gerrit instance
     * might be behind a HTTP Reverse Proxy (like Nginx or Apache) with enabled HTTP Authentication. You can identify that if
     * you have to enter an username and password (browser password request) for opening the Gerrit web interface. Since this
     * plugin uses Gerrit REST API (with authentication enabled), you need to tell your system administrator that he should
     * disable HTTP Authentication for any request to <code>/a</code> path (e.g. https://git.example.com/a). For these requests
     * HTTP Authentication is done by Gerrit (double HTTP Authentication will not work). For more information see
     * [Gerrit documentation].
     * [Gerrit documentation]: https://gerrit-review.googlesource.com/Documentation/rest-api.html#authentication
     */
    private static void tryGerritHttpAuth(String host, HttpClient client) throws IOException {
        String loginUrl = host + "/login/";
        HttpMethod loginRequest = SslSupport.getInstance().executeSelfSignedCertificateAwareRequest(client, loginUrl,
                new ThrowableConvertor<String, HttpMethod, IOException>() {
                    @Override
                    public HttpMethod convert(String loginUrl) throws IOException {
                        GetMethod method = new GetMethod(loginUrl);
                        method.setFollowRedirects(false); // we do not need any further information; status code and GerritAccount cookie is enough
                        return method;
                    }
                });
        if (loginRequest.getStatusCode() != 401) {
            Cookie[] cookies = client.getState().getCookies();
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("GerritAccount")) {
                    LOG.info("Successfully logged in with /login/ request.");
                    break;
                }
            }
        }
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
        addUserAgent(client);
        return client;
    }

    private static void addUserAgent(HttpClient client) {
        HttpClientParams httpClientParams = client.getParams();
        Object existingUserAgent = httpClientParams.getParameter(HttpMethodParams.USER_AGENT);
        String userAgent = "gerrit-intellij-plugin";
        if (existingUserAgent != null) {
            userAgent += " using " + existingUserAgent;
        }
        httpClientParams.setParameter(HttpMethodParams.USER_AGENT, userAgent);
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
