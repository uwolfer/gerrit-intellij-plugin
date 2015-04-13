/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.urswolfer.intellij.plugin.gerrit;

import com.google.common.base.Strings;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Parts based on org.jetbrains.plugins.github.GithubSettings
 *
 * @author oleg
 * @author Urs Wolfer
 */
@State(
        name = "GerritSettings",
        storages = {
                @Storage(
                        file = StoragePathMacros.APP_CONFIG + "/gerrit_settings.xml"
                )}
)
public class GerritSettings implements PersistentStateComponent<Element>, GerritAuthData {

    private static final String GERRIT_SETTINGS_TAG = "GerritSettings";
    private static final String LOGIN = "Login";
    private static final String HOST = "Host";
    private static final String CLONE_URL = "CloneUrl";
    private static final String AUTOMATIC_REFRESH = "AutomaticRefresh";
    private static final String LIST_ALL_CHANGES = "ListAllChanges";
    private static final String REFRESH_TIMEOUT = "RefreshTimeout";
    private static final String REVIEW_NOTIFICATIONS = "ReviewNotifications";
    private static final String PUSH_TO_GERRIT = "PushToGerrit";
    private static final String SHOW_CHANGE_NUMBER_COLUMN = "ShowChangeNumberColumn";
    private static final String SHOW_CHANGE_ID_COLUMN = "ShowChangeIdColumn";
    private static final String GERRIT_SETTINGS_PASSWORD_KEY = "GERRIT_SETTINGS_PASSWORD_KEY";

    private String login;
    private String host;
    private String cloneUrl;
    private boolean listAllChanges;
    private boolean automaticRefresh;
    private int refreshTimeout;
    private boolean refreshNotifications;
    private boolean pushToGerrit;
    private boolean showChangeNumberColumn;
    private boolean showChangeIdColumn;

    private Logger log;

    public Element getState() {
        final Element element = new Element(GERRIT_SETTINGS_TAG);
        element.setAttribute(LOGIN, (getLogin() != null ? getLogin() : ""));
        element.setAttribute(HOST, (getHost() != null ? getHost() : ""));
        element.setAttribute(CLONE_URL, (getCloneUrl() != null ? getCloneUrl() : ""));
        element.setAttribute(LIST_ALL_CHANGES, "" + getListAllChanges());
        element.setAttribute(AUTOMATIC_REFRESH, "" + getAutomaticRefresh());
        element.setAttribute(REFRESH_TIMEOUT, "" + getRefreshTimeout());
        element.setAttribute(REVIEW_NOTIFICATIONS, "" + getReviewNotifications());
        element.setAttribute(PUSH_TO_GERRIT, "" + getPushToGerrit());
        element.setAttribute(SHOW_CHANGE_NUMBER_COLUMN, "" + getShowChangeNumberColumn());
        element.setAttribute(SHOW_CHANGE_ID_COLUMN, "" + getShowChangeIdColumn());
        return element;
    }

    public void loadState(@NotNull final Element element) {
        // All the logic on retrieving password was moved to getPassword action to cleanup initialization process
        try {
            setLogin(element.getAttributeValue(LOGIN));
            setHost(element.getAttributeValue(HOST));
            setCloneUrl(element.getAttributeValue(CLONE_URL));
            setListAllChanges(getBooleanValue(element, LIST_ALL_CHANGES));
            setAutomaticRefresh(getBooleanValue(element, AUTOMATIC_REFRESH));
            setRefreshTimeout(getIntegerValue(element, REFRESH_TIMEOUT));
            setReviewNotifications(getBooleanValue(element, REVIEW_NOTIFICATIONS));
            setPushToGerrit(getBooleanValue(element, PUSH_TO_GERRIT));
            setShowChangeNumberColumn(getBooleanValue(element, SHOW_CHANGE_NUMBER_COLUMN));
            setShowChangeIdColumn(getBooleanValue(element, SHOW_CHANGE_ID_COLUMN));
        } catch (Exception e) {
            log.error("Error happened while loading gerrit settings: " + e);
        }
    }

    private boolean getBooleanValue(Element element, String attributeName) {
        String attributeValue = element.getAttributeValue(attributeName);
        if (attributeValue != null) {
            return Boolean.valueOf(attributeValue);
        } else {
            return false;
        }
    }

    private int getIntegerValue(Element element, String attributeName) {
        String attributeValue = element.getAttributeValue(attributeName);
        if (attributeValue != null) {
            return Integer.valueOf(attributeValue);
        } else {
            return 0;
        }
    }

    @Override
    @Nullable
    public String getLogin() {
        return login;
    }

    @Override
    @NotNull
    public String getPassword() {
        String password;
        try {
            password = PasswordSafe.getInstance().getPassword(null, GerritSettings.class, GERRIT_SETTINGS_PASSWORD_KEY);
        } catch (PasswordSafeException e) {
            log.info("Couldn't get password for key [" + GERRIT_SETTINGS_PASSWORD_KEY + "]", e);
            password = "";
        }
        return StringUtil.notNullize(password);
    }

    @Override
    public String getHost() {
        return host;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    @Override
    public boolean isLoginAndPasswordAvailable() {
        return !Strings.isNullOrEmpty(getLogin()) && !Strings.isNullOrEmpty(getPassword());
    }

    public boolean getListAllChanges() {
        return listAllChanges;
    }

    public void setListAllChanges(boolean listAllChanges) {
        this.listAllChanges = listAllChanges;
    }

    public boolean getAutomaticRefresh() {
        return automaticRefresh;
    }

    public int getRefreshTimeout() {
        return refreshTimeout;
    }

    public boolean getReviewNotifications() {
        return refreshNotifications;
    }

    public void setLogin(final String login) {
        this.login = login != null ? login : "";
    }

    public void setPassword(final String password) {
        try {
            PasswordSafe.getInstance().storePassword(null, GerritSettings.class, GERRIT_SETTINGS_PASSWORD_KEY, password != null ? password : "");
        } catch (PasswordSafeException e) {
            log.info("Couldn't set password for key [" + GERRIT_SETTINGS_PASSWORD_KEY + "]", e);
        }
    }

    public void forgetPassword() {
        try {
            PasswordSafe.getInstance().removePassword(null, GerritSettings.class, GERRIT_SETTINGS_PASSWORD_KEY);
        } catch (PasswordSafeException e) {
            log.info("Couldn't forget password for key [" + GERRIT_SETTINGS_PASSWORD_KEY + "]", e);
        }
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public void setAutomaticRefresh(final boolean automaticRefresh) {
        this.automaticRefresh = automaticRefresh;
    }

    public void setRefreshTimeout(final int refreshTimeout) {
        this.refreshTimeout = refreshTimeout;
    }

    public void setReviewNotifications(final boolean reviewNotifications) {
        refreshNotifications = reviewNotifications;
    }

    public void setPushToGerrit(boolean pushToGerrit) {
        this.pushToGerrit = pushToGerrit;
    }

    public boolean getPushToGerrit() {
        return pushToGerrit;
    }

    public boolean getShowChangeNumberColumn() {
        return showChangeNumberColumn;
    }

    public void setShowChangeNumberColumn(boolean showChangeNumberColumn) {
        this.showChangeNumberColumn = showChangeNumberColumn;
    }

    public boolean getShowChangeIdColumn() {
        return showChangeIdColumn;
    }

    public void setShowChangeIdColumn(boolean showChangeIdColumn) {
        this.showChangeIdColumn = showChangeIdColumn;
    }

    public void setLog(Logger log) {
        this.log = log;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }
}
