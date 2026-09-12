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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.diagnostic.SubmittedReportInfo.SubmissionStatus;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.Version;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;

/**
 * @author Urs Wolfer
 */
public class PluginErrorReportSubmitter extends ErrorReportSubmitter {

    private static final Logger LOG = Logger.getInstance(PluginErrorReportSubmitter.class);

    private static final String ERROR_REPORT_URL = "https://urswolfer.com/gerrit-intellij-plugin/service/error-report/";

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
        final String json = new Gson().toJson(createErrorBean(events[0], additionalInfo));
        // the report is sent in background: this runs on the event dispatch thread, and the IDE waits for the
        // result to be handed to the consumer before it shows the report as submitted (or as failed)
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                consumer.consume(postError(json));
            }
        });
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

    private SubmittedReportInfo postError(String json) {
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            try {
                HttpPost httpPost = new HttpPost(ERROR_REPORT_URL);
                httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
                CloseableHttpResponse response = httpClient.execute(httpPost);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 406) {
                    showMessage(response.getStatusLine().getReasonPhrase());
                    return new SubmittedReportInfo(SubmissionStatus.FAILED);
                }
                if (statusCode < 200 || statusCode >= 300) {
                    LOG.warn("Error report was not accepted: " + response.getStatusLine());
                    return new SubmittedReportInfo(SubmissionStatus.FAILED);
                }
                return new SubmittedReportInfo(SubmissionStatus.NEW_ISSUE);
            } finally {
                httpClient.close();
            }
        } catch (IOException e) {
            // the IDE tells the user that the report could not be sent, so this must not be thrown any further
            LOG.warn("Failed to send error report.", e);
            return new SubmittedReportInfo(SubmissionStatus.FAILED);
        }
    }

    private void showMessage(final String message) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                Messages.showErrorDialog(message, "Gerrit Plugin Message");
            }
        });
    }
}
