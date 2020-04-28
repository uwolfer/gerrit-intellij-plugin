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

package com.urswolfer.intellij.plugin.gerrit.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.ui.ShowProjectColumn;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

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
public class GerritSettings implements PersistentStateComponent<Element> {

    private static final String GERRIT_SETTINGS_TAG = "GerritSettings";
    private static final String AUTOMATIC_REFRESH = "AutomaticRefresh";
    private static final String LIST_ALL_CHANGES = "ListAllChanges";
    private static final String REFRESH_TIMEOUT = "RefreshTimeout";
    private static final String REVIEW_NOTIFICATIONS = "ReviewNotifications";
    private static final String PUSH_TO_GERRIT = "PushToGerrit";
    private static final String SHOW_CHANGE_NUMBER_COLUMN = "ShowChangeNumberColumn";
    private static final String SHOW_CHANGE_ID_COLUMN = "ShowChangeIdColumn";
    private static final String SHOW_TOPIC_COLUMN = "ShowTopicColumn";
    private static final String SHOW_PROJECT_COLUMN = "ShowProjectColumn";

    private Element element;

    private boolean listAllChanges = false;
    private boolean automaticRefresh = true;
    private int refreshTimeout = 15;
    private boolean refreshNotifications = true;
    private boolean pushToGerrit = false;
    private boolean showChangeNumberColumn = false;
    private boolean showChangeIdColumn = false;
    private boolean showTopicColumn = false;
    private ShowProjectColumn showProjectColumn = ShowProjectColumn.AUTO;

    private Logger log;

    public Element getState() {
        if (element == null){
            element = new Element(GERRIT_SETTINGS_TAG);
        }
        element.setAttribute(LIST_ALL_CHANGES, Boolean.toString(getListAllChanges()));
        element.setAttribute(AUTOMATIC_REFRESH, Boolean.toString(getAutomaticRefresh()));
        element.setAttribute(REFRESH_TIMEOUT, Integer.toString(getRefreshTimeout()));
        element.setAttribute(REVIEW_NOTIFICATIONS, Boolean.toString(getReviewNotifications()));
        element.setAttribute(PUSH_TO_GERRIT, Boolean.toString(getPushToGerrit()));
        element.setAttribute(SHOW_CHANGE_NUMBER_COLUMN, Boolean.toString(getShowChangeNumberColumn()));
        element.setAttribute(SHOW_CHANGE_ID_COLUMN, Boolean.toString(getShowChangeIdColumn()));
        element.setAttribute(SHOW_TOPIC_COLUMN, Boolean.toString(getShowTopicColumn()));
        element.setAttribute(SHOW_PROJECT_COLUMN, getShowProjectColumn().name());
        return element;
    }

    public void loadState(@NotNull final Element element) {
        // All the logic on retrieving password was moved to getPassword action to cleanup initialization process
        try {
            this.element = element;
            setListAllChanges(getBooleanValue(element, LIST_ALL_CHANGES));
            setAutomaticRefresh(getBooleanValue(element, AUTOMATIC_REFRESH));
            setRefreshTimeout(getIntegerValue(element, REFRESH_TIMEOUT));
            setReviewNotifications(getBooleanValue(element, REVIEW_NOTIFICATIONS));
            setPushToGerrit(getBooleanValue(element, PUSH_TO_GERRIT));
            setShowChangeNumberColumn(getBooleanValue(element, SHOW_CHANGE_NUMBER_COLUMN));
            setShowChangeIdColumn(getBooleanValue(element, SHOW_CHANGE_ID_COLUMN));
            setShowTopicColumn(getBooleanValue(element, SHOW_TOPIC_COLUMN));
            setShowProjectColumn(getShowProjectColumnValue(element, SHOW_PROJECT_COLUMN));
        } catch (Exception e) {
            log.error("Error happened while loading gerrit settings: " + e);
        }
    }

    public GerritProjectSettings forFocusedProject(){
        DataContext dataContext = DataManager.getInstance().getDataContextFromFocus().getResultSync();
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        return forProject(project);
    }

    public GerritProjectSettings forProject(Project project){
        GerritProjectSettings projectSettings = ServiceManager.getService(project, GerritProjectSettings.class);
        projectSettings.setLog(log);
        return projectSettings;
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

    public void setLog(Logger log) {
        this.log = log;
    }

    Element getElement(){
        return element;
    }
}
