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

package com.urswolfer.intellij.plugin.gerrit.rest;

import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IdeaWideProxySelector;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/**
 * @author Urs Wolfer
 */
public class ProxyHttpClientBuilderExtension extends HttpClientBuilderExtension {

    @Override
    public CredentialsProvider extendCredentialProvider(HttpClientBuilder httpClientBuilder,
                                                        CredentialsProvider credentialsProvider,
                                                        GerritAuthData authData) {
        HttpConfigurable proxySettings = HttpConfigurable.getInstance();
        IdeaWideProxySelector ideaWideProxySelector = new IdeaWideProxySelector(proxySettings);

        // This will always return at least one proxy, which can be the "NO_PROXY" instance.
        List<Proxy> proxies = ideaWideProxySelector.select(URI.create(authData.getHost()));

        // Find the first real proxy with an address type we support.
        for (Proxy proxy : proxies) {
            SocketAddress socketAddress = proxy.address();

            if (HttpConfigurable.isRealProxy(proxy) && socketAddress instanceof InetSocketAddress) {
                InetSocketAddress address = (InetSocketAddress) socketAddress;
                HttpHost proxyHttpHost = new HttpHost(address.getHostName(), address.getPort());
                httpClientBuilder.setProxy(proxyHttpHost);

                // Here we use the single username/password that we got from IDEA's settings. It feels kinda strange
                // to use these credential but it's probably what the user expects.
                if (proxySettings.PROXY_AUTHENTICATION && proxySettings.getProxyLogin() != null) {
                    AuthScope authScope = new AuthScope(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
                    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(proxySettings.getProxyLogin(), proxySettings.getPlainProxyPassword());
                    credentialsProvider.setCredentials(authScope, credentials);
                    break;
                }
            }
        }
        return credentialsProvider;
    }
}
