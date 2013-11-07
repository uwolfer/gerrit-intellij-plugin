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
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.net.HttpConfigurable;
import com.urswolfer.intellij.plugin.gerrit.GerritAuthData;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parts based on org.jetbrains.plugins.github.GithubApiUtil
 *
 * @author Urs Wolfer
 * @author Kirill Likhodedov
 */
public class GerritApiUtil {

    private static final String APPLICATION_JSON = "application/json";
    private static final String UTF_8 = "UTF-8";
    private static final Pattern GERRIT_AUTH_PATTERN = Pattern.compile(".*?xGerritAuth=\"(.+?)\"");
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final Logger LOG = GerritUtil.LOG;

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
        HttpMethod method = null;
        try {
            method = doREST(authData, path, requestBody, headers, verb);

            checkStatusCode(method);

            InputStream resp = method.getResponseBodyAsStream();
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
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }

    @NotNull
    public HttpMethod doREST(@NotNull String path,
                                    @Nullable final String requestBody,
                                    @NotNull final Collection<Header> headers,
                                    @NotNull final HttpVerb verb) throws IOException {
        return doREST(authData, path, requestBody, headers, verb);
    }


    @NotNull
    public HttpMethod doREST(@NotNull GerritAuthData authData,
                                    @NotNull String path,
                                    @Nullable final String requestBody,
                                    @NotNull final Collection<Header> headers,
                                    @NotNull final HttpVerb verb) throws IOException {
        HttpClient client = getHttpClient(authData);
        final Optional<String> gerritAuthOptional = tryGerritHttpAuth(authData, client);
        String uri = authData.getHost() + path;
        return sslSupport.executeSelfSignedCertificateAwareRequest(client, uri,
                new ThrowableConvertor<String, HttpMethod, IOException>() {
                    @Override
                    public HttpMethod convert(String uri) throws IOException {
                        HttpMethod method;
                        switch (verb) {
                            case POST:
                                method = new PostMethod(uri);
                                if (requestBody != null) {
                                    ((PostMethod) method).setRequestEntity(new StringRequestEntity(requestBody, APPLICATION_JSON, UTF_8));
                                }
                                break;
                            case GET:
                                method = new GetMethod(uri);
                                break;
                            case DELETE:
                                method = new DeleteMethod(uri);
                                break;
                            case HEAD:
                                method = new HeadMethod(uri);
                                break;
                            default:
                                throw new IllegalStateException("Wrong HttpVerb: unknown method: " + verb.toString());
                        }
                        for (Header header : headers) {
                            method.addRequestHeader(header);
                        }
                        if (gerritAuthOptional.isPresent()) {
                            method.setRequestHeader("X-Gerrit-Auth", gerritAuthOptional.get());
                        }
                        method.setRequestHeader("Accept", APPLICATION_JSON);
                        return method;
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
    private Optional<String> tryGerritHttpAuth(@NotNull GerritAuthData authData,
                                                      @NotNull HttpClient client) throws IOException {
        String loginUrl = authData.getHost() + "/login/";
        HttpMethod loginRequest = sslSupport.executeSelfSignedCertificateAwareRequest(client, loginUrl,
                new ThrowableConvertor<String, HttpMethod, IOException>() {
                    @Override
                    public HttpMethod convert(String loginUrl) throws IOException {
                        return new GetMethod(loginUrl);
                    }
                });
        if (loginRequest.getStatusCode() != HttpStatus.SC_UNAUTHORIZED) {
            Cookie[] cookies = client.getState().getCookies();
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("GerritAccount")) {
                    LOG.info("Successfully logged in with /login/ request.");
                    Matcher matcher = GERRIT_AUTH_PATTERN.matcher(loginRequest.getResponseBodyAsString());
                    if (matcher.find()) {
                        return  Optional.of(matcher.group(1));
                    }
                    break;
                }
            }
        }
        return Optional.absent();
    }

    @NotNull
    private HttpClient getHttpClient(@NotNull GerritAuthData authData) {
        final HttpClient client = new HttpClient();
        HttpConnectionManagerParams params = client.getHttpConnectionManager().getParams();
        params.setConnectionTimeout(CONNECTION_TIMEOUT); //set connection timeout (how long it takes to connect to remote host)
        params.setSoTimeout(CONNECTION_TIMEOUT); //set socket timeout (how long it takes to retrieve data from remote host)

        client.getParams().setContentCharset(UTF_8);
        // Configure proxySettings if it is required
        final HttpConfigurable proxySettings = HttpConfigurable.getInstance();
        if (proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST)) {
            client.getHostConfiguration().setProxy(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
            if (proxySettings.PROXY_AUTHENTICATION) {
                client.getState().setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN,
                        proxySettings.getPlainProxyPassword()));
            }
        }
        if (authData.getLogin() != null && authData.getPassword() != null) {
            client.getParams().setCredentialCharset(UTF_8);
            client.getParams().setAuthenticationPreemptive(true);
            client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(authData.getLogin(), authData.getPassword()));
        }
        addUserAgent(client);
        return client;
    }

    private void addUserAgent(HttpClient client) {
        HttpClientParams httpClientParams = client.getParams();
        Object existingUserAgent = httpClientParams.getParameter(HttpMethodParams.USER_AGENT);
        String userAgent = "gerrit-intellij-plugin";
        if (existingUserAgent != null) {
            userAgent += " using " + existingUserAgent;
        }
        httpClientParams.setParameter(HttpMethodParams.USER_AGENT, userAgent);
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

    private void checkStatusCode(@NotNull HttpMethod method) throws HttpStatusException {
        int code = method.getStatusCode();
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
                String message = String.format("Request not successful. Message: %s. Status-Code: %s.", method.getStatusText(), method.getStatusCode());
                LOG.warn(message);
                throw new HttpStatusException(method.getStatusCode(), method.getStatusText(), message);
        }
    }

}
