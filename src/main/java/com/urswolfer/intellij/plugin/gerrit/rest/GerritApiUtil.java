/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.google.common.base.Optional;
import com.google.common.io.CharStreams;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import com.urswolfer.intellij.plugin.gerrit.GerritAuthData;
import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parts based on org.jetbrains.plugins.github.GithubApiUtil
 *
 * @author Urs Wolfer
 * @author Kirill Likhodedov
 */
public class GerritApiUtil {

    private static final String UTF_8 = "UTF-8";
    private static final Pattern GERRIT_AUTH_PATTERN = Pattern.compile(".*?xGerritAuth=\"(.+?)\"");
    private static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final String PREEMPTIVE_AUTH = "preemptive-auth";

    @Inject
    private Logger LOG;
    @Inject
    private GerritAuthData authData;
    @Inject
    private SslSupport sslSupport;

    public enum HttpVerb {
        GET, POST, DELETE, HEAD
    }

    public JsonElement getRequest(@NotNull GerritAuthData gerritAuthData, @NotNull String path, @NotNull Header... headers) throws RestApiException {
        return request(gerritAuthData, path, null, Arrays.asList(headers), HttpVerb.GET);
    }

    @Nullable
    public JsonElement getRequest(@NotNull String path,
                                         @NotNull Header... headers) throws RestApiException {
        return request(authData, path, null, Arrays.asList(headers), HttpVerb.GET);
    }

    @Nullable
    public JsonElement postRequest(@NotNull String path,
                                          @Nullable String requestBody,
                                          @NotNull Header... headers) throws RestApiException {
        return request(authData, path, requestBody, Arrays.asList(headers), HttpVerb.POST);
    }

    @Nullable
    public JsonElement deleteRequest(@NotNull String path,
                                            @NotNull Header... headers) throws RestApiException {
        return request(authData, path, null, Arrays.asList(headers), HttpVerb.DELETE);
    }

    @Nullable
    private JsonElement request(@NotNull GerritAuthData authData,
                                       @NotNull String path,
                                       @Nullable String requestBody,
                                       @NotNull Collection<Header> headers,
                                       @NotNull HttpVerb verb) throws RestApiException {
        try {
            HttpResponse response = doREST(authData, path, requestBody, headers, verb);

            checkStatusCode(response);

            InputStream resp = response.getEntity().getContent();
            if (resp == null) {
                String message = String.format("Unexpectedly empty response.");
                LOG.warn(message);
                throw new RestApiException(message);
            }
            JsonElement ret = parseResponse(resp);
            if (ret.isJsonNull()) {
                String message = String.format("Unexpectedly empty response: %s.", CharStreams.toString(new InputStreamReader(resp)));
                LOG.warn(message);
                throw new RestApiException(message);
            }
            return ret;
        } catch (IOException e) {
            LOG.warn(String.format("Request failed: %s", e.getMessage()), e);
            throw new RestApiException(e);
        }
    }

    @NotNull
    public HttpResponse doREST(@NotNull String path,
                                    @Nullable final String requestBody,
                                    @NotNull final Collection<Header> headers,
                                    @NotNull final HttpVerb verb) throws IOException {
        return doREST(authData, path, requestBody, headers, verb);
    }


    @NotNull
    public HttpResponse doREST(@NotNull GerritAuthData authData,
                                    @NotNull String path,
                                    @Nullable final String requestBody,
                                    @NotNull final Collection<Header> headers,
                                    @NotNull final HttpVerb verb) throws IOException {
        HttpContext httpContext = new BasicHttpContext();
        HttpClientBuilder client = getHttpClient(authData, httpContext);

        BasicCookieStore cookieStore = new BasicCookieStore();
        httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

        final Optional<String> gerritAuthOptional = tryGerritHttpAuth(authData, client, httpContext);
        String uri = authData.getHost();
        if (authData.isLoginAndPasswordAvailable()) {
            uri += "/a";
        }
        uri += path;

        HttpRequestBase method;
        switch (verb) {
            case POST:
                method = new HttpPost(uri);
                if (requestBody != null) {
                    ((HttpPost) method).setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
                }
                break;
            case GET:
                method = new HttpGet(uri);
                break;
            case DELETE:
                method = new HttpDelete(uri);
                break;
            case HEAD:
                method = new HttpHead(uri);
                break;
            default:
                throw new IllegalStateException("Wrong HttpVerb: unknown method: " + verb.toString());
        }
        for (Header header : headers) {
            method.addHeader(header);
        }
        if (gerritAuthOptional.isPresent()) {
            method.addHeader("X-Gerrit-Auth", gerritAuthOptional.get());
        }
        method.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());

        return sslSupport.executeSelfSignedCertificateAwareRequest(client, method, httpContext);
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
    private Optional<String> tryGerritHttpAuth(GerritAuthData authData,
                                               HttpClientBuilder client,
                                               HttpContext httpContext) throws IOException {
        String loginUrl = authData.getHost() + "/login/";
        HttpResponse loginRequest = sslSupport.executeSelfSignedCertificateAwareRequest(client, new HttpGet(loginUrl), httpContext);
        if (loginRequest.getStatusLine().getStatusCode() != HttpStatus.SC_UNAUTHORIZED) {
            List<Cookie> cookies = ((BasicCookieStore) httpContext.getAttribute(HttpClientContext.COOKIE_STORE)).getCookies();
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("GerritAccount")) {
                    LOG.info("Successfully logged in with /login/ request.");
                    Matcher matcher = GERRIT_AUTH_PATTERN.matcher(EntityUtils.toString(loginRequest.getEntity(), Consts.UTF_8));
                    if (matcher.find()) {
                        return Optional.of(matcher.group(1));
                    }
                    break;
                }
            }
        }
        return Optional.absent();
    }

    @NotNull
    private HttpClientBuilder getHttpClient(GerritAuthData authData,
                                            HttpContext httpContext) {
        HttpClientBuilder client = HttpClients.custom();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        client.setConnectionManager(connectionManager);

        RequestConfig.Builder requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECTION_TIMEOUT_MS) // how long it takes to connect to remote host
            .setSocketTimeout(CONNECTION_TIMEOUT_MS) // (how long it takes to retrieve data from remote host
            .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
            ;
        client.setDefaultRequestConfig(requestConfig.build());

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        client.setDefaultCredentialsProvider(credentialsProvider);

        // Configure proxySettings if it is required
        final HttpConfigurable proxySettings = HttpConfigurable.getInstance();
        if (proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST)) {
            HttpHost proxy = new HttpHost(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
            client.setProxy(proxy);

            if (proxySettings.PROXY_AUTHENTICATION) {
                credentialsProvider.setCredentials(new AuthScope(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT),
                        new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN, proxySettings.getPlainProxyPassword()));
            }

        }

        if (authData.isLoginAndPasswordAvailable()) {
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(authData.getLogin(), authData.getPassword()));

            BasicScheme basicAuth = new BasicScheme();
            httpContext.setAttribute(PREEMPTIVE_AUTH, basicAuth);
            client.addInterceptorFirst(new PreemptiveAuthHttpRequestInterceptor());
        }

        client.addInterceptorLast(new UserAgentHttpRequestInterceptor());

        return client;
    }

    @NotNull
    private JsonElement parseResponse(@NotNull InputStream response) throws IOException {
        Reader reader = new InputStreamReader(response, UTF_8);
        try {
            return new JsonParser().parse(reader);
        } catch (JsonSyntaxException jse) {
            throw new IOException(String.format("Couldn't parse response: %n%s", CharStreams.toString(new InputStreamReader(response))), jse);
        } finally {
            reader.close();
        }
    }

    private void checkStatusCode(@NotNull HttpResponse response) throws HttpStatusException {
        StatusLine statusLine = response.getStatusLine();
        int code = statusLine.getStatusCode();
        switch (code) {
            case HttpStatus.SC_OK:
            case HttpStatus.SC_CREATED:
            case HttpStatus.SC_ACCEPTED:
            case HttpStatus.SC_NO_CONTENT:
                return;
            case HttpStatus.SC_BAD_REQUEST:
            case HttpStatus.SC_UNAUTHORIZED:
            case HttpStatus.SC_PAYMENT_REQUIRED:
            case HttpStatus.SC_FORBIDDEN:
            default:
                String message = String.format("Request not successful. Message: %s. Status-Code: %s.", statusLine.getReasonPhrase(), statusLine.getStatusCode());
                LOG.warn(message);
                throw new HttpStatusException(statusLine.getStatusCode(), statusLine.getReasonPhrase(), message);
        }
    }

    /**
     * With preemptive auth, it will send the basic authentication response even before the server gives an unauthorized
     * response in certain situations, thus reducing the overhead of making the connection again.
     *
     * Based on:
     * https://subversion.jfrog.org/jfrog/build-info/trunk/build-info-client/src/main/java/org/jfrog/build/client/PreemptiveHttpClient.java
     */
    private static class PreemptiveAuthHttpRequestInterceptor implements HttpRequestInterceptor {
        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);

            // if no auth scheme available yet, try to initialize it preemptively
            if (authState.getAuthScheme() == null) {
                AuthScheme authScheme = (AuthScheme) context.getAttribute("preemptive-auth");
                CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
                if (authScheme != null) {
                    Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                    if (creds == null) {
                        throw new HttpException("No credentials for preemptive authentication found");
                    }
                    authState.update(authScheme, creds);
                }
            }
        }
    }

    private static class UserAgentHttpRequestInterceptor implements HttpRequestInterceptor {
        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            Header existingUserAgent = request.getFirstHeader(HttpHeaders.USER_AGENT);
            String userAgent = "gerrit-intellij-plugin";
            if (existingUserAgent != null) {
                userAgent += " using " + existingUserAgent.getValue();
            }
            request.setHeader(HttpHeaders.USER_AGENT, userAgent);
        }
    }

}
