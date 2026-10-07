/*
 * Copyright 2026 Urs Wolfer
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

import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BasicHttpRequest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TimeoutClientBuilderExtensionTest {

    /** What GerritHttpClientFactory sets as the default config of the HTTP client. */
    private static final RequestConfig CLIENT_DEFAULT = RequestConfig.custom()
        .setConnectTimeout(300000).setSocketTimeout(300000).setConnectionRequestTimeout(300000).build();

    @Test
    public void testConnectTimeoutAddedToTheConfigOfTheRequest() {
        // the REST client sets this on each API request
        RequestConfig config = process(RequestConfig.custom().setNormalizeUri(false).build(),
            new HttpPost("http://gerrit/a/changes/7/submit"));

        Assert.assertEquals(config.getConnectTimeout(), 10000);
        Assert.assertFalse(config.isNormalizeUri());
    }

    @Test
    public void testOtherTimeoutsOfAChangeKept() {
        RequestConfig config = process(CLIENT_DEFAULT, new HttpPost("http://gerrit/a/changes/7/submit"));

        Assert.assertEquals(config.getConnectTimeout(), 10000);
        Assert.assertEquals(config.getSocketTimeout(), 300000);
        Assert.assertEquals(config.getConnectionRequestTimeout(), 300000);
    }

    @Test
    public void testNoReadTimeoutOfAChangeKept() {
        Assert.assertEquals(process(RequestConfig.custom().setSocketTimeout(0).build(),
            new HttpPost("http://gerrit/a/changes/7/submit")).getSocketTimeout(), 0);
    }

    @Test
    public void testReadsGetAReadTimeout() {
        Assert.assertEquals(socketTimeout(new HttpGet("http://gerrit/a/changes/?q=status:open")), 5000);
        Assert.assertEquals(socketTimeout(new HttpGet("http://gerrit/a/accounts/?suggest&q=rita&n=20")), 5000);
    }

    @Test
    public void testRequestsAheadOfAnApiRequestGetAReadTimeout() {
        // they come with the default config of the REST client, not with the one of an API request
        Assert.assertEquals(process(CLIENT_DEFAULT, new HttpGet("http://gerrit/accounts/self")).getSocketTimeout(),
            5000);
        Assert.assertEquals(process(CLIENT_DEFAULT, new HttpGet("http://gerrit/login/")).getSocketTimeout(), 5000);
        Assert.assertEquals(process(CLIENT_DEFAULT, new HttpPost("http://gerrit/login/")).getSocketTimeout(), 5000);
        // a host saved with a trailing slash
        Assert.assertEquals(process(CLIENT_DEFAULT, new BasicHttpRequest("POST", "//login/")).getSocketTimeout(),
            5000);
    }

    @Test
    public void testChangesGetALongReadTimeoutSetExplicitly() {
        // set rather than left unset, so that a TLS connection does not keep the connect timeout for reading
        Assert.assertEquals(socketTimeout(new HttpPost("http://gerrit/a/changes/7/submit")), 7000);
        Assert.assertEquals(socketTimeout(new HttpPut("http://gerrit/a/changes/7/topic")), 7000);
        Assert.assertEquals(socketTimeout(new HttpDelete("http://gerrit/a/changes/7/topic")), 7000);
    }

    private static int socketTimeout(HttpRequest request) {
        return process(RequestConfig.custom().setNormalizeUri(false).build(), request).getSocketTimeout();
    }

    private static RequestConfig process(RequestConfig requestConfig, HttpRequest request) {
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(requestConfig);
        new TimeoutClientBuilderExtension.TimeoutInterceptor(10000, 5000, 7000).process(request, context);
        return context.getRequestConfig();
    }
}
