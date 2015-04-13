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

import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.Version;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.awt.*;
import java.io.IOException;

/**
 * @author Urs Wolfer
 */
public class PluginErrorReportSubmitter extends ErrorReportSubmitter {

    private static final String ERROR_REPORT_URL = "http://urswolfer.com/gerrit-intellij-plugin/service/error-report/";

    @Override
    public String getReportActionText() {
        return "Report to Gerrit Plugin Author (Thanks for your Help to improve the Plugin!)";
    }

    @Override
    public boolean submit(IdeaLoggingEvent[] events, String additionalInfo, Component parentComponent, Consumer<SubmittedReportInfo> consumer) {
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
        errorBean.setException(loggingEvent.getThrowableText());
        errorBean.setExceptionMessage(loggingEvent.getMessage());
        return errorBean;
    }

    private void postError(String json) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(ERROR_REPORT_URL);
        httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        try {
            httpClient.execute(httpPost);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
