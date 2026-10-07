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

import org.apache.http.client.config.RequestConfig;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TimeoutClientBuilderExtensionTest {

    @Test
    public void testConnectTimeoutAddedToTheConfigOfTheRequest() {
        // the REST client sets this on each API request
        RequestConfig config = process(RequestConfig.custom().setNormalizeUri(false).build());

        Assert.assertEquals(config.getConnectTimeout(), 10000);
        Assert.assertEquals(config.getSocketTimeout(), -1);
        Assert.assertFalse(config.isNormalizeUri());
    }

    @Test
    public void testOtherTimeoutsKept() {
        RequestConfig config = process(RequestConfig.custom()
            .setConnectTimeout(300000).setSocketTimeout(300000).setConnectionRequestTimeout(300000).build());

        Assert.assertEquals(config.getConnectTimeout(), 10000);
        Assert.assertEquals(config.getSocketTimeout(), 300000);
        Assert.assertEquals(config.getConnectionRequestTimeout(), 300000);
    }

    @Test
    public void testSuggestionsGetAReadTimeout() {
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new HttpGet("http://gerrit/a/changes/7/suggest_reviewers?q=rita&n=20")).getSocketTimeout(), 5000);
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new HttpGet("http://gerrit/a/accounts/?suggest&q=rita&n=20")).getSocketTimeout(), 5000);
    }

    @Test
    public void testSuggestionsOfAHostWithTrailingSlashGetAReadTimeout() {
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new BasicHttpRequest("GET", "//accounts/?suggest&q=rita&n=20")).getSocketTimeout(), 5000);
    }

    @Test
    public void testAuthenticationGetsAReadTimeout() {
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new HttpGet("http://gerrit/accounts/self")).getSocketTimeout(), 5000);
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new HttpGet("http://gerrit/login/")).getSocketTimeout(), 5000);
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new HttpPost("http://gerrit/login/")).getSocketTimeout(), 5000);
    }

    @Test
    public void testOtherRequestsGetNoReadTimeout() {
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new HttpGet("http://gerrit/a/accounts/?q=suggestion")).getSocketTimeout(), -1);
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new HttpGet("http://gerrit/a/changes/?q=status:open")).getSocketTimeout(), -1);
        Assert.assertEquals(process(RequestConfig.DEFAULT,
            new HttpPost("http://gerrit/a/changes/7/suggest_reviewers")).getSocketTimeout(), -1);
    }

    private static RequestConfig process(RequestConfig requestConfig) {
        return process(requestConfig, new HttpGet("http://gerrit/"));
    }

    private static RequestConfig process(RequestConfig requestConfig, HttpRequest request) {
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(requestConfig);
        new TimeoutClientBuilderExtension.TimeoutInterceptor(10000, 5000).process(request, context);
        return context.getRequestConfig();
    }
}
