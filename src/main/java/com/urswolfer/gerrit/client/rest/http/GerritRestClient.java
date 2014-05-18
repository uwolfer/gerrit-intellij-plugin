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

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.*;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.Version;
import com.urswolfer.gerrit.client.rest.gson.DateDeserializer;
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
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This class provides basic http access to the rest interface of a gerrit instance.
 *
 * @author Urs Wolfer
 */
public class GerritRestClient {

    private static final String UTF_8 = "UTF-8";
    private static final Pattern GERRIT_AUTH_PATTERN = Pattern.compile(".*?xGerritAuth=\"(.+?)\"");
    private static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final String PREEMPTIVE_AUTH = "preemptive-auth";
    private static final Gson GSON = initGson();

    private final GerritAuthData authData;
    private final HttpRequestExecutor httpRequestExecutor;
    private final List<HttpClientBuilderExtension> httpClientBuilderExtensions;

    public GerritRestClient(GerritAuthData authData,
                            HttpRequestExecutor httpRequestExecutor,
                            HttpClientBuilderExtension... httpClientBuilderExtensions) {
        this.authData = authData;
        this.httpRequestExecutor = httpRequestExecutor;
        this.httpClientBuilderExtensions = Arrays.asList(httpClientBuilderExtensions);
    }

    public enum HttpVerb {
        GET, POST, DELETE, HEAD, PUT
    }

    public Gson getGson() {
        return GSON;
    }

    public JsonElement getRequest(String path, Header... headers) throws RestApiException {
        return request(path, null, Arrays.asList(headers), HttpVerb.GET);
    }

    public JsonElement postRequest(String path, String requestBody, Header... headers) throws RestApiException {
        return request(path, requestBody, Arrays.asList(headers), HttpVerb.POST);
    }

    public JsonElement putRequest(String path, Header... headers) throws RestApiException {
        return request(path, null, Arrays.asList(headers), HttpVerb.PUT);
    }

    public JsonElement deleteRequest(String path, Header... headers) throws RestApiException {
        return request(path, null, Arrays.asList(headers), HttpVerb.DELETE);
    }

    public JsonElement request(String path, String requestBody, Collection<Header> headers, HttpVerb verb) throws RestApiException {
        try {
            HttpResponse response = doRest(path, requestBody, headers, verb);

            checkStatusCode(response);

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return null;
            }
            InputStream resp = entity.getContent();
            JsonElement ret = parseResponse(resp);
            if (ret.isJsonNull()) {
                String message = String.format("Unexpectedly empty response: %s.", CharStreams.toString(new InputStreamReader(resp)));
                throw new RestApiException(message);
            }
            return ret;
        } catch (IOException e) {
            throw new RestApiException(e);
        }
    }

    public HttpResponse doRest(String path, String requestBody, Collection<Header> headers, HttpVerb verb) throws IOException, RestApiException {
        HttpContext httpContext = new BasicHttpContext();
        HttpClientBuilder client = getHttpClient(httpContext);

        BasicCookieStore cookieStore = new BasicCookieStore();
        httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

        final Optional<String> gerritAuthOptional = tryGerritHttpAuth(client, httpContext);
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
            case PUT:
                method = new HttpPut(uri);
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

        return httpRequestExecutor.execute(client, method, httpContext);
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
    private Optional<String> tryGerritHttpAuth(HttpClientBuilder client, HttpContext httpContext) throws IOException {
        String loginUrl = authData.getHost() + "/login/";
        HttpResponse loginRequest = httpRequestExecutor.execute(client, new HttpGet(loginUrl), httpContext);
        if (loginRequest.getStatusLine().getStatusCode() != HttpStatus.SC_UNAUTHORIZED) {
            List<Cookie> cookies = ((BasicCookieStore) httpContext.getAttribute(HttpClientContext.COOKIE_STORE)).getCookies();
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("GerritAccount")) {
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

    private HttpClientBuilder getHttpClient(HttpContext httpContext) {
        HttpClientBuilder client = HttpClients.custom();

        client.useSystemProperties(); // see also: com.intellij.util.net.ssl.CertificateManager

        RequestConfig.Builder requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT_MS) // how long it takes to connect to remote host
                .setSocketTimeout(CONNECTION_TIMEOUT_MS) // how long it takes to retrieve data from remote host
                .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS);
        client.setDefaultRequestConfig(requestConfig.build());

        CredentialsProvider credentialsProvider = getCredentialsProvider();
        client.setDefaultCredentialsProvider(credentialsProvider);

        if (authData.isLoginAndPasswordAvailable()) {
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(authData.getLogin(), authData.getPassword()));

            BasicScheme basicAuth = new BasicScheme();
            httpContext.setAttribute(PREEMPTIVE_AUTH, basicAuth);
            client.addInterceptorFirst(new PreemptiveAuthHttpRequestInterceptor(authData));
        }

        client.addInterceptorLast(new UserAgentHttpRequestInterceptor());

        for (HttpClientBuilderExtension httpClientBuilderExtension : httpClientBuilderExtensions) {
            client = httpClientBuilderExtension.extend(client, authData);
            credentialsProvider = httpClientBuilderExtension.extendCredentialProvider(client, credentialsProvider, authData);
        }

        return client;
    }

    /**
     * With this impl, it only returns the same credentials once. Otherwise it's possible that a loop will occur.
     * When server returns status code 401, the HTTP client provides the same credentials forever.
     * Since we create a new HTTP client for every request, we can handle it this way.
     */
    private BasicCredentialsProvider getCredentialsProvider() {
        return new BasicCredentialsProvider() {
            private Set<AuthScope> authAlreadyTried = Sets.newHashSet();

            @Override
            public Credentials getCredentials(AuthScope authscope) {
                if (authAlreadyTried.contains(authscope)) {
                    return null;
                }
                authAlreadyTried.add(authscope);
                return super.getCredentials(authscope);
            }
        };
    }

    private JsonElement parseResponse(InputStream response) throws IOException {
        Reader reader = new InputStreamReader(response, UTF_8);
        try {
            return new JsonParser().parse(reader);
        } catch (JsonSyntaxException jse) {
            throw new IOException(String.format("Couldn't parse response: %n%s", CharStreams.toString(new InputStreamReader(response))), jse);
        } finally {
            reader.close();
        }
    }

    private void checkStatusCode(HttpResponse response) throws HttpStatusException {
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
        private GerritAuthData authData;

        public PreemptiveAuthHttpRequestInterceptor(GerritAuthData authData) {
            this.authData = authData;
        }

        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);

            // if no auth scheme available yet, try to initialize it preemptively
            if (authState.getAuthScheme() == null) {
                AuthScheme authScheme = (AuthScheme) context.getAttribute(PREEMPTIVE_AUTH);
                if (authScheme != null) {
                    UsernamePasswordCredentials creds = new UsernamePasswordCredentials(authData.getLogin(), authData.getPassword());
                    authState.update(authScheme, creds);
                }
            }
        }
    }

    private static class UserAgentHttpRequestInterceptor implements HttpRequestInterceptor {

        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            Header existingUserAgent = request.getFirstHeader(HttpHeaders.USER_AGENT);
            String userAgent = String.format("gerrit-intellij-plugin/%s", Version.get());
            if (existingUserAgent != null) {
                userAgent += " using " + existingUserAgent.getValue();
            }
            request.setHeader(HttpHeaders.USER_AGENT, userAgent);
        }
    }

    private static Gson initGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new DateDeserializer());
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        return builder.create();
    }
}
