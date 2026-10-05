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
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.NonStaticAuthenticator;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/**
 * @author Urs Wolfer
 */
public class ProxyHttpClientBuilderExtension extends HttpClientBuilderExtension {
    private static final String SOCKS_LOGIN_KEY = ProxyHttpClientBuilderExtension.class.getName() + ".socks";

    @Override
    public CredentialsProvider extendCredentialProvider(HttpClientBuilder httpClientBuilder,
                                                        CredentialsProvider credentialsProvider,
                                                        GerritAuthData authData) {
        HttpConfigurable proxySettings = HttpConfigurable.getInstance();
        IdeaWideProxySelector ideaWideProxySelector = new IdeaWideProxySelector(proxySettings);

        // only for the proxy the login is entered for, like the one of an HTTP proxy below
        if (proxySettings.USE_HTTP_PROXY && proxySettings.PROXY_TYPE_IS_SOCKS
                && proxySettings.PROXY_AUTHENTICATION && proxySettings.getProxyLogin() != null) {
            CommonProxy.getInstance().setCustomAuth(SOCKS_LOGIN_KEY, createSocksLogin(proxySettings.PROXY_HOST,
                proxySettings.PROXY_PORT, proxySettings.getProxyLogin(), proxySettings.getPlainProxyPassword()));
        } else {
            CommonProxy.getInstance().removeCustomAuth(SOCKS_LOGIN_KEY);
        }

        // This will always return at least one proxy, which can be the "NO_PROXY" instance.
        List<Proxy> proxies = ideaWideProxySelector.select(URI.create(authData.getHost()));

        // Find the first real proxy with an address type we support.
        for (Proxy proxy : proxies) {
            SocketAddress socketAddress = proxy.address();

            if (HttpConfigurable.isRealProxy(proxy) && socketAddress instanceof InetSocketAddress) {
                if (proxy.type() == Proxy.Type.SOCKS) {
                    // HttpClient only speaks HTTP to a proxy, so a SOCKS proxy has to go into the sockets below it
                    httpClientBuilder.setConnectionManager(
                        createSocksConnectionManager(proxy, CertificateManager.getInstance().getSslContext()));
                    // without, the builder asks the IDE's ProxySelector again, which skips the SOCKS proxy and
                    // takes an HTTP one listed after it (e.g. by a PAC script), to tunnel through both
                    httpClientBuilder.setRoutePlanner(new DefaultRoutePlanner(null));
                    break;
                }
                InetSocketAddress address = (InetSocketAddress) socketAddress;
                HttpHost proxyHttpHost = new HttpHost(address.getHostName(), address.getPort());
                httpClientBuilder.setProxy(proxyHttpHost);

                // Here we use the single username/password that we got from IDEA's settings. It feels kinda strange
                // to use these credential but it's probably what the user expects.
                if (proxySettings.PROXY_AUTHENTICATION && proxySettings.getProxyLogin() != null) {
                    AuthScope authScope = new AuthScope(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
                    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(proxySettings.getProxyLogin(), proxySettings.getPlainProxyPassword());
                    credentialsProvider.setCredentials(authScope, credentials);
                }
                break; // the first usable proxy is the one to use, the ones after it are the alternatives
            }
        }
        return credentialsProvider;
    }

    /**
     * Replaces the connection manager the builder would create, and with it what it sets up out of the system
     * properties and the SSL context set by {@link CertificateManagerClientBuilderExtension}, so both are
     * applied here again.
     *
     * A host name is left to the proxy to resolve: behind a SOCKS proxy (e.g. an SSH tunnel) Gerrit often has
     * a name only the proxy knows, or one which resolves to another address here.
     */
    static HttpClientConnectionManager createSocksConnectionManager(Proxy proxy, SSLContext sslContext) {
        Registry<ConnectionSocketFactory> socketFactories = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", new PlainConnectionSocketFactory() {
                @Override
                public Socket createSocket(HttpContext context) {
                    return new Socket(proxy);
                }

                @Override
                public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host,
                                            InetSocketAddress remoteAddress, InetSocketAddress localAddress,
                                            HttpContext context) throws IOException {
                    return super.connectSocket(connectTimeout, socket, host, target(host, remoteAddress),
                        localAddress, context);
                }
            })
            .register("https", new SSLConnectionSocketFactory(sslContext,
                split(System.getProperty("https.protocols")), split(System.getProperty("https.cipherSuites")),
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()) {
                @Override
                public Socket createSocket(HttpContext context) {
                    return new Socket(proxy);
                }

                @Override
                public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host,
                                            InetSocketAddress remoteAddress, InetSocketAddress localAddress,
                                            HttpContext context) throws IOException {
                    return super.connectSocket(connectTimeout, socket, host, target(host, remoteAddress),
                        localAddress, context);
                }
            })
            .build();
        // the address of a name is a placeholder, target() hands the name to the proxy instead
        DnsResolver proxyResolves = host -> isIpAddress(host)
            ? InetAddress.getAllByName(host)
            : new InetAddress[] {InetAddress.getByAddress(host, new byte[4])};
        return new PoolingHttpClientConnectionManager(socketFactories, proxyResolves);
    }

    /**
     * The JDK asks the default Authenticator for the login of a SOCKS proxy as for one of a server, which the
     * IDE's own one does not answer. The IDE's default Authenticator asks every one registered with it, so this
     * one answers that request only, and only for this proxy.
     */
    static NonStaticAuthenticator createSocksLogin(String proxyHost, int proxyPort, String login, String password) {
        // spelled the way the proxy selector's InetSocketAddress spells it, e.g. "::1" as "0:0:0:0:0:0:0:1"
        String requestingHost = isIpAddress(proxyHost)
            ? new InetSocketAddress(proxyHost, proxyPort).getHostString()
            : proxyHost;
        return new NonStaticAuthenticator() {
            @Override
            public PasswordAuthentication getPasswordAuthentication() {
                boolean socksLoginRequest = "SOCKS5".equals(getRequestingProtocol())
                    && requestingHost.equalsIgnoreCase(getRequestingHost())
                    && proxyPort == getRequestingPort();
                return socksLoginRequest
                    ? new PasswordAuthentication(login, password == null ? new char[0] : password.toCharArray())
                    : null;
            }
        };
    }

    private static InetSocketAddress target(HttpHost host, InetSocketAddress remoteAddress) {
        return isIpAddress(host.getHostName())
            ? remoteAddress
            : InetSocketAddress.createUnresolved(host.getHostName(), remoteAddress.getPort());
    }

    private static boolean isIpAddress(String host) {
        // an IPv6 address comes in brackets out of the url
        String address = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        return InetAddressUtils.isIPv4Address(address) || InetAddressUtils.isIPv6Address(address);
    }

    private static String[] split(String commaSeparated) {
        return commaSeparated == null || commaSeparated.isBlank() ? null : commaSeparated.split(" *, *");
    }
}
