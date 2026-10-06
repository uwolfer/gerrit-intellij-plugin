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
import org.apache.http.client.methods.HttpGet;
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

    private static RequestConfig process(RequestConfig requestConfig) {
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(requestConfig);
        new TimeoutClientBuilderExtension.TimeoutInterceptor(10000).process(new HttpGet("http://gerrit/"), context);
        return context.getRequestConfig();
    }
}
