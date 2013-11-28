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
import com.intellij.util.ThrowableConvertor;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.validator.ValidatorException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Provides various methods to work with SSL certificate protected HTTPS connections.
 * Parts based on org.jetbrains.plugins.github.GithubSslSupport
 *
 * @author Kirill Likhodedov
 * @author Urs Wolfer
 */
public class SslSupport {

    @Inject
    private GerritSettings gerritSettings;

    /**
     * Tries to execute the {@link HttpMethod} and captures the {@link ValidatorException exception} which is thrown if user connects
     * to an HTTPS server with a non-trusted (probably, self-signed) SSL certificate. In which case proposes to cancel the connection
     * or to proceed without certificate check.
     *
     * @param methodCreator a function to create the HttpMethod. This is required instead of just {@link HttpMethod} instance, because the
     *                      implementation requires the HttpMethod to be recreated in certain circumstances.
     * @return the HttpMethod instance which was actually executed
     *         and which can be {@link HttpMethod#getResponseBodyAsString() asked for the response}.
     * @throws IOException in case of other errors or if user declines the proposal of non-trusted connection.
     */
    @NotNull
    public HttpMethod executeSelfSignedCertificateAwareRequest(@NotNull HttpClient client, @NotNull String uri,
                                                               @NotNull ThrowableConvertor<String, HttpMethod, IOException> methodCreator)
            throws IOException {
        HttpMethod method = methodCreator.convert(uri);
        try {
            client.executeMethod(method);
            return method;
        } catch (IOException e) {
            HttpMethod m = handleCertificateExceptionAndRetry(e, method.getURI().getHost(), client, method.getURI(), methodCreator);
            if (m == null) {
                throw e;
            }
            return m;
        }
    }

    @Nullable
    private HttpMethod handleCertificateExceptionAndRetry(@NotNull IOException e, @NotNull String host,
                                                                 @NotNull HttpClient client, @NotNull URI uri,
                                                                 @NotNull ThrowableConvertor<String, HttpMethod, IOException> methodCreator)
            throws IOException {
        if (!isCertificateException(e)) {
            throw e;
        }

        if (isTrusted(uri.getAuthority())) {
            int port = uri.getPort();
            if (port <= 0) {
                port = 443;
            }
            // creating a special configuration that allows connections to non-trusted HTTPS hosts
            // see the javadoc to EasySSLProtocolSocketFactory for details
            Protocol easyHttps = new Protocol("https", (ProtocolSocketFactory) new EasySSLProtocolSocketFactory(), port);
            HostConfiguration hc = new HostConfiguration();
            hc.setHost(host, port, easyHttps);
            String relativeUri = uri.getEscapedPathQuery();
            // it is important to use relative URI here, otherwise our custom protocol won't work.
            // we have to recreate the method, because HttpMethod#setUri won't overwrite the host,
            // and changing host by hands (HttpMethodBase#setHostConfiguration) is deprecated.
            HttpMethod method = methodCreator.convert(relativeUri);
            client.executeMethod(hc, method);
            return method;
        }
        throw e;
    }

    public boolean isCertificateException(Exception e) {
        List<Throwable> causalChain = Throwables.getCausalChain(e);
        for (Throwable throwable : causalChain) {
            if (throwable instanceof ValidatorException) {
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
