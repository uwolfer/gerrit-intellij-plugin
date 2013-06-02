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

import java.io.IOException;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URI;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.util.ThrowableConvertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.validator.ValidatorException;

/**
 * Provides various methods to work with SSL certificate protected HTTPS connections.
 *
 * Parts based on org.jetbrains.plugins.github.SslSupport
 *
 * @author Kirill Likhodedov
 * @author Urs Wolfer
 */
public class SslSupport {

    public static SslSupport getInstance() {
//        return ServiceManager.getService(SslSupport.class);
        return new SslSupport(); // TODO
    }

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
        }
        catch (IOException e) {
            HttpMethod m = handleCertificateExceptionAndRetry(e, method.getURI().getHost(), client, method.getURI(), methodCreator);
            if (m == null) {
                throw e;
            }
            return m;
        }
    }

    @Nullable
    private static HttpMethod handleCertificateExceptionAndRetry(@NotNull IOException e, @NotNull String host,
                                                                 @NotNull HttpClient client, @NotNull URI uri,
                                                                 @NotNull ThrowableConvertor<String, HttpMethod, IOException> methodCreator)
            throws IOException {
        if (!isCertificateException(e)) {
            throw e;
        }

        if (isTrusted(host)) {
            // creating a special configuration that allows connections to non-trusted HTTPS hosts
            // see the javadoc to EasySSLProtocolSocketFactory for details
            HostConfiguration hc = new HostConfiguration();
//            Protocol easyHttps = new Protocol("https", (ProtocolSocketFactory)new EasySSLProtocolSocketFactory(), 443);
//            hc.setHost(host, 443, easyHttps);
            String relativeUri = new URI(uri.getPathQuery(), false).getURI();
            // it is important to use relative URI here, otherwise our custom protocol won't work.
            // we have to recreate the method, because HttpMethod#setUri won't overwrite the host,
            // and changing host by hands (HttpMethodBase#setHostConfiguration) is deprecated.
            HttpMethod method = methodCreator.convert(relativeUri);
            client.executeMethod(hc, method);
            return method;
        }
        throw e;
    }

    public static boolean isCertificateException(IOException e) {
        return e.getCause() instanceof ValidatorException;
    }

    private static boolean isTrusted(@NotNull String host) {
//        return GerritSettings.getInstance().getTrustedHosts().contains(host);
        return false;
    }

    private static void saveToTrusted(@NotNull String host) {
//        GerritSettings.getInstance().addTrustedHost(host);
    }

    @CalledInAwt
    public boolean askIfShouldProceed(final String host) {
        final String BACK_TO_SAFETY = "No, I don't trust";
        final String TRUST = "Proceed anyway";
        int choice = Messages.showDialog("The security certificate of " + host + " is not trusted. Do you want to proceed anyway?",
                "Not Trusted Certificate", new String[] { BACK_TO_SAFETY, TRUST }, 0, Messages.getErrorIcon());
        boolean trust = (choice == 1);
        if (trust) {
            saveToTrusted(host);
        }
        return trust;
    }

}
