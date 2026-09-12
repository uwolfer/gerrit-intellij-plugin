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

package com.urswolfer.intellij.plugin.gerrit.errorreport;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.Consumer;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.Version;
import com.urswolfer.intellij.plugin.gerrit.rest.CertificateManagerClientBuilderExtension;
import com.urswolfer.intellij.plugin.gerrit.rest.ProxyHttpClientBuilderExtension;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;

/**
 * @author Urs Wolfer
 */
public class PluginErrorReportSubmitter extends ErrorReportSubmitter {

    private static final String ERROR_REPORT_URL = "https://urswolfer.com/gerrit-intellij-plugin/service/error-report/";

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int SOCKET_TIMEOUT_MS = 30000;

    @Override
    public String getReportActionText() {
        return "Report to Plugin Developer (Please include your email address)";
    }

    @Override
    public boolean submit(@NotNull IdeaLoggingEvent[] events, String additionalInfo, Component parentComponent, Consumer<? super SubmittedReportInfo> consumer) {
        if (Strings.isNullOrEmpty(additionalInfo) || !additionalInfo.contains("@")) {
            String emailAddress = Messages.showInputDialog(
                "It seems you have not included your email address.\n" +
                "If you enter it below, you will get most probably a message " +
                "with a solution for your issue or a question which " +
                "will help to solve it.", "Information Required", null);
            if (!Strings.isNullOrEmpty(emailAddress)) {
                additionalInfo = additionalInfo == null
                    ? emailAddress : additionalInfo + '\n' + emailAddress;
            }
        }
        ErrorBean errorBean = createErrorBean(events[0], additionalInfo);
        String json = new Gson().toJson(errorBean);
        postError(json);
        return true;
    }

    private ErrorBean createErrorBean(IdeaLoggingEvent loggingEvent, String additionalInfo) {
        ErrorBean errorBean = new ErrorBean();
        errorBean.setAdditionInfo(additionalInfo);
        errorBean.setPluginVersion(Version.get());
        ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
        String intellijVersion = String.format("%s %s.%s %s",
            appInfo.getVersionName(), appInfo.getMajorVersion(), appInfo.getMinorVersion(), appInfo.getApiVersion());
        errorBean.setIntellijVersion(intellijVersion);
        errorBean.setOs(String.format("%s %s", System.getProperty("os.name"), System.getProperty("os.version")));
        errorBean.setJava(String.format("%s %s", System.getProperty("java.vendor"), System.getProperty("java.version")));
        errorBean.setException(loggingEvent.getThrowableText());
        errorBean.setExceptionMessage(loggingEvent.getMessage());
        return errorBean;
    }

    private void postError(String json) {
        try {
            CloseableHttpClient httpClient = createHttpClient();
            try {
                HttpPost httpPost = new HttpPost(ERROR_REPORT_URL);
                httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
                CloseableHttpResponse response = httpClient.execute(httpPost);
                if (response.getStatusLine().getStatusCode() == 406) {
                    String reasonPhrase = response.getStatusLine().getReasonPhrase();
                    Messages.showErrorDialog(reasonPhrase, "Gerrit Plugin Message");
                }
            } finally {
                httpClient.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return a client set up like the ones talking to Gerrit: it goes through the proxy configured in the IDE
     *         (authenticating against it) and trusts what the IDE trusts, neither of which a default client does.
     *         Its requests time out, so that a service which never answers does not keep the report pending.
     */
    private CloseableHttpClient createHttpClient() {
        GerritAuthData authData = new GerritAuthData.Basic(ERROR_REPORT_URL);
        HttpClientBuilder httpClientBuilder = HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build());
        httpClientBuilder = new CertificateManagerClientBuilderExtension().extend(httpClientBuilder, authData);
        CredentialsProvider credentialsProvider = new ProxyHttpClientBuilderExtension()
            .extendCredentialProvider(httpClientBuilder, new BasicCredentialsProvider(), authData);
        return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider).build();
    }
}
