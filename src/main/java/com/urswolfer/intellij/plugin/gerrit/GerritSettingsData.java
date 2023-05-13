package com.urswolfer.intellij.plugin.gerrit;

import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.urswolfer.intellij.plugin.gerrit.ui.ShowProjectColumn;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GerritSettingsData {
    public static final String NAME = "name";

    private static final String LOGIN = "Login";
    private static final String HOST = "Host";
    private static final String AUTOMATIC_REFRESH = "AutomaticRefresh";
    private static final String LIST_ALL_CHANGES = "ListAllChanges";
    private static final String REFRESH_TIMEOUT = "RefreshTimeout";
    private static final String REVIEW_NOTIFICATIONS = "ReviewNotifications";
    private static final String PUSH_TO_GERRIT = "PushToGerrit";
    private static final String SHOW_CHANGE_NUMBER_COLUMN = "ShowChangeNumberColumn";
    private static final String SHOW_CHANGE_ID_COLUMN = "ShowChangeIdColumn";
    private static final String SHOW_TOPIC_COLUMN = "ShowTopicColumn";
    private static final String SHOW_PROJECT_COLUMN = "ShowProjectColumn";
    private static final String CLONE_BASE_URL = "CloneBaseUrl";

    private String login = "";
    private String host = "";
    private boolean listAllChanges = false;
    private boolean automaticRefresh = true;
    private int refreshTimeout = 15;
    private boolean refreshNotifications = true;
    private boolean pushToGerrit = false;
    private boolean showChangeNumberColumn = false;
    private boolean showChangeIdColumn = false;
    private boolean showTopicColumn = false;
    private ShowProjectColumn showProjectColumn = ShowProjectColumn.AUTO;
    private String cloneBaseUrl = "";

    private Logger logger;

    public GerritSettingsData(@NotNull final Element element, Logger log) {
        this.logger = log;

        try {
            setLogin(element.getAttributeValue(LOGIN));
            setHost(element.getAttributeValue(HOST));

            setListAllChanges(getBooleanValue(element, LIST_ALL_CHANGES));
            setAutomaticRefresh(getBooleanValue(element, AUTOMATIC_REFRESH));
            setRefreshTimeout(getIntegerValue(element, REFRESH_TIMEOUT));
            setReviewNotifications(getBooleanValue(element, REVIEW_NOTIFICATIONS));
            setPushToGerrit(getBooleanValue(element, PUSH_TO_GERRIT));
            setShowChangeNumberColumn(getBooleanValue(element, SHOW_CHANGE_NUMBER_COLUMN));
            setShowChangeIdColumn(getBooleanValue(element, SHOW_CHANGE_ID_COLUMN));
            setShowTopicColumn(getBooleanValue(element, SHOW_TOPIC_COLUMN));
            setShowProjectColumn(getShowProjectColumnValue(element, SHOW_PROJECT_COLUMN));
            setCloneBaseUrl(element.getAttributeValue(CLONE_BASE_URL));
        } catch (Exception e) {
            logger.error("Error happened while loading gerrit settings: " + e);
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

    private ShowProjectColumn getShowProjectColumnValue(Element element, String attributeName) {
        String attributeValue = element.getAttributeValue(attributeName);
        if (attributeValue != null) {
            return ShowProjectColumn.valueOf(attributeValue);
        } else {
            return ShowProjectColumn.AUTO;
        }
    }

    @Nullable
    public String getLogin() {
        return login;
    }

    public String getHost() {
        return host;
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

    public boolean getShowTopicColumn() {
        return showTopicColumn;
    }

    public ShowProjectColumn getShowProjectColumn() {
        return showProjectColumn;
    }

    public void setShowProjectColumn(ShowProjectColumn showProjectColumn) {
        this.showProjectColumn = showProjectColumn;
    }

    public void setShowTopicColumn(boolean showTopicColumn) {
        this.showTopicColumn = showTopicColumn;
    }

    public void setCloneBaseUrl(String cloneBaseUrl) {
        this.cloneBaseUrl = cloneBaseUrl;
    }

    public String getCloneBaseUrl() {
        return cloneBaseUrl;
    }

    public String getCloneBaseUrlOrHost() {
        return Strings.isNullOrEmpty(cloneBaseUrl) ? host : cloneBaseUrl;
    }

    public void setLog(Logger log) {
        this.logger = log;
    }

    public Element fillElement(Element element, String elementName){
        element.setAttribute(NAME, elementName);
        element.setAttribute(LOGIN, (getLogin() != null ? getLogin() : ""));
        element.setAttribute(HOST, (getHost() != null ? getHost() : ""));
        element.setAttribute(LIST_ALL_CHANGES, Boolean.toString(getListAllChanges()));
        element.setAttribute(AUTOMATIC_REFRESH, Boolean.toString(getAutomaticRefresh()));
        element.setAttribute(REFRESH_TIMEOUT, Integer.toString(getRefreshTimeout()));
        element.setAttribute(REVIEW_NOTIFICATIONS, Boolean.toString(getReviewNotifications()));
        element.setAttribute(PUSH_TO_GERRIT, Boolean.toString(getPushToGerrit()));
        element.setAttribute(SHOW_CHANGE_NUMBER_COLUMN, Boolean.toString(getShowChangeNumberColumn()));
        element.setAttribute(SHOW_CHANGE_ID_COLUMN, Boolean.toString(getShowChangeIdColumn()));
        element.setAttribute(SHOW_TOPIC_COLUMN, Boolean.toString(getShowTopicColumn()));
        element.setAttribute(SHOW_PROJECT_COLUMN, getShowProjectColumn().name());
        element.setAttribute(CLONE_BASE_URL, (getCloneBaseUrl() != null ? getCloneBaseUrl() : ""));
        return element;
    }
}
