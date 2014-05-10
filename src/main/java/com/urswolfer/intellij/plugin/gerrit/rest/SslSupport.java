/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.google.inject.Inject;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CalledInAwt;
import com.urswolfer.gerrit.client.rest.http.HttpRequestExecutor;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.validator.ValidatorException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Provides various methods to work with SSL certificate protected HTTPS connections.
 * Parts based on org.jetbrains.plugins.github.GithubSslSupport
 *
 * @author Kirill Likhodedov
 * @author Urs Wolfer
 */
public class SslSupport extends HttpRequestExecutor {

    @Inject
    private GerritSettings gerritSettings;

    /**
     * Tries to execute the {@link HttpRequestBase} and captures the {@link ValidatorException exception} which is thrown if user connects
     * to an HTTPS server with a non-trusted (probably, self-signed) SSL certificate. In which case proposes to cancel the connection
     * or to proceed without certificate check.
     *
     * @return the HttpMethod instance which was actually executed
     *         and which can be {@link org.apache.http.HttpResponse#getEntity()} asked for the response.
     * @throws IOException in case of other errors or if user declines the proposal of non-trusted connection.
     */
    @Override
    public HttpResponse execute(HttpClientBuilder client,
                                HttpRequestBase method,
                                @Nullable HttpContext context)
            throws IOException {
        try {
            return client.build().execute(method, context);
        } catch (IOException e) {
            method.reset();
            HttpResponse m = handleCertificateExceptionAndRetry(e, method, client, context);
            if (m == null) {
                throw e;
            }
            return m;
        }
    }

    @Nullable
    private HttpResponse handleCertificateExceptionAndRetry(IOException e,
                                                            HttpRequestBase method,
                                                            HttpClientBuilder client,
                                                            @Nullable HttpContext context)
            throws IOException {
        if (!isCertificateException(e)) {
            throw e;
        }

        if (isTrusted(method.getURI().getAuthority())) {
            // creating a special configuration that allows connections to non-trusted HTTPS hosts
            try {
                SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
                sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        return true;
                    }
                });
                SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                        sslContextBuilder.build(), SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

                Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("https", sslConnectionSocketFactory)
                        .build();

                PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                client.setConnectionManager(connectionManager);
            } catch (Exception sslException) {
                throw Throwables.propagate(sslException);
            }
            return client.build().execute(method, context);
        }
        throw e;
    }

    public boolean isCertificateException(Exception e) {
        List<Throwable> causalChain = Throwables.getCausalChain(e);
        for (Throwable throwable : causalChain) {
            if (throwable instanceof ValidatorException) {
                return true;
            }
            if (throwable instanceof SSLException) { // e.g. "SSLException: hostname in certificate didn't match: <localhost> != <unknown>"
                return true;
            }
        }
        return false;
    }

    private boolean isTrusted(@NotNull String host) {
        return gerritSettings.getTrustedHosts().contains(host);
    }

    private void saveToTrusted(@NotNull String host) {
        try {
            gerritSettings.addTrustedHost(new java.net.URI(host).getAuthority());
        } catch (URISyntaxException e) {
            throw Throwables.propagate(e);
        }
    }

    @CalledInAwt
    public boolean askIfShouldProceed(final String host) {
        int choice = Messages.showYesNoDialog("The security certificate of " + host + " is not trusted. Do you want to proceed anyway?",
                "Not Trusted Certificate", "Proceed anyway", "No, I don't trust", Messages.getErrorIcon());
        boolean trust = (choice == Messages.YES);
        if (trust) {
            saveToTrusted(host);
        }
        return trust;
    }
}
