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

import com.google.common.base.Optional;
import com.intellij.ide.passwordSafe.MasterPasswordUnavailableException;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterKeyPasswordSafe;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
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
    private static final String AUTOMATIC_REFRESH = "AutomaticRefresh";
    private static final String REFRESH_TIMEOUT = "RefreshTimeout";
    private static final String REVIEW_NOTIFICATIONS = "ReviewNotifications";
    public static final String GERRIT_SETTINGS_PASSWORD_KEY = "GERRIT_SETTINGS_PASSWORD_KEY";
    private static final String TRUSTED_HOSTS = "GERRIT_TRUSTED_HOSTS";
    private static final String TRUSTED_HOST = "HOST";
    private static final String TRUSTED_URL = "URL";

    private String myLogin;
    private String myHost;
    private boolean myAutomaticRefresh;
    private int myRefreshTimeout;
    private boolean myRefreshNotifications;
    private Collection<String> myTrustedHosts = new ArrayList<String>();

    private Logger log;

    private boolean passwordChanged = false;

    // Once master password is refused, do not ask for it again
    private boolean masterPasswordRefused = false;

    private Optional<String> cachedPassword = Optional.absent();

    public Element getState() {
        log.assertTrue(!ProgressManager.getInstance().hasProgressIndicator(), "Password should not be accessed under modal progress");

        try {
            if (passwordChanged && !masterPasswordRefused) {
                PasswordSafe.getInstance().storePassword(null, GerritSettings.class, GERRIT_SETTINGS_PASSWORD_KEY, getPassword());
            }
        } catch (MasterPasswordUnavailableException e) {
            log.info("Couldn't store password for key [" + GERRIT_SETTINGS_PASSWORD_KEY + "]", e);
            masterPasswordRefused = true;
        } catch (Exception e) {
            Messages.showErrorDialog("Error happened while storing password for gerrit", "Error");
            log.info("Couldn't get password for key [" + GERRIT_SETTINGS_PASSWORD_KEY + "]", e);
        }
        passwordChanged = false;
        final Element element = new Element(GERRIT_SETTINGS_TAG);
        element.setAttribute(LOGIN, getLogin());
        element.setAttribute(HOST, getHost());
        element.setAttribute(AUTOMATIC_REFRESH, "" + getAutomaticRefresh());
        element.setAttribute(REFRESH_TIMEOUT, "" + getRefreshTimeout());
        element.setAttribute(REVIEW_NOTIFICATIONS, "" + getReviewNotifications());
        Element trustedHosts = new Element(TRUSTED_HOSTS);
        for (String host : myTrustedHosts) {
            Element hostEl = new Element(TRUSTED_HOST);
            hostEl.setAttribute(TRUSTED_URL, host);
            trustedHosts.addContent(hostEl);
        }
        element.addContent(trustedHosts);
        return element;
    }

    public void loadState(@NotNull final Element element) {
        // All the logic on retrieving password was moved to getPassword action to cleanup initialization process
        try {
            setLogin(element.getAttributeValue(LOGIN));
            setHost(element.getAttributeValue(HOST));

            String automaticRefreshValue = element.getAttributeValue(AUTOMATIC_REFRESH);
            if (automaticRefreshValue != null) {
                setAutomaticRefresh(Boolean.valueOf(automaticRefreshValue));
            }

            String refreshTimeoutValue = element.getAttributeValue(REFRESH_TIMEOUT);
            if (refreshTimeoutValue != null) {
                setRefreshTimeout(Integer.valueOf(refreshTimeoutValue));
            }

            String reviewNotificationsValue = element.getAttributeValue(REVIEW_NOTIFICATIONS);
            if (reviewNotificationsValue != null) {
                setReviewNotifications(Boolean.valueOf(reviewNotificationsValue));
            }

            for (Object trustedHostsObj : element.getChildren(TRUSTED_HOSTS)) {
                Element trustedHosts = (Element) trustedHostsObj;
                for (Object trustedHostObj : trustedHosts.getChildren()) {
                    Element trustedHost = (Element) trustedHostObj;
                    addTrustedHost(trustedHost.getAttributeValue(TRUSTED_URL));
                }
            }
        } catch (Exception e) {
            log.error("Error happened while loading gerrit settings: " + e);
        }
    }

    @Override
    @Nullable
    public String getLogin() {
        return myLogin;
    }

    public void preloadPassword() {
        cachedPassword = Optional.of(getPassword());
    }

    @Override
    @NotNull
    public String getPassword() {
        boolean hasProgressIndicator = ProgressManager.getInstance().hasProgressIndicator();
        if (hasProgressIndicator) {
            log.assertTrue(cachedPassword.isPresent(), "Password must be preloaded when accessed under modal progress");
            return cachedPassword.get();
        }
        String password;
        final Project project = ProjectManager.getInstance().getDefaultProject();
        final PasswordSafeImpl passwordSafe = (PasswordSafeImpl) PasswordSafe.getInstance();
        try {
            password = passwordSafe.getMemoryProvider().getPassword(project, GerritSettings.class, GERRIT_SETTINGS_PASSWORD_KEY);
            if (password != null) {
                return password;
            }
            final MasterKeyPasswordSafe masterKeyProvider = passwordSafe.getMasterKeyProvider();
            if (!masterKeyProvider.isEmpty()) {
                // workaround for: don't ask for master password, if the requested password is not there.
                // this should be fixed in PasswordSafe: don't ask master password to look for keys
                // until then we assume that is PasswordSafe was used (there is anything there), then it makes sense to look there.
                password = masterKeyProvider.getPassword(project, GerritSettings.class, GERRIT_SETTINGS_PASSWORD_KEY);
            }
        } catch (PasswordSafeException e) {
            log.info("Couldn't get password for key [" + GERRIT_SETTINGS_PASSWORD_KEY + "]", e);
            masterPasswordRefused = true;
            password = "";
        }

        passwordChanged = false;
        return password != null ? password : "";
    }

    @Override
    public String getHost() {
        return myHost;
    }

    public boolean getAutomaticRefresh() {
        return myAutomaticRefresh;
    }

    public int getRefreshTimeout() {
        return myRefreshTimeout;
    }

    public boolean getReviewNotifications() {
        return myRefreshNotifications;
    }

    public void setLogin(final String login) {
        myLogin = login != null ? login : "";
    }

    public void setPassword(final String password) {
        passwordChanged = !getPassword().equals(password);
        try {
            PasswordSafe.getInstance().storePassword(null, GerritSettings.class, GERRIT_SETTINGS_PASSWORD_KEY, password != null ? password : "");
        } catch (PasswordSafeException e) {
            log.info("Couldn't get password for key [" + GERRIT_SETTINGS_PASSWORD_KEY + "]", e);
        }
    }

    public void setHost(final String host) {
        myHost = host;
    }

    public void setAutomaticRefresh(final boolean automaticRefresh) {
        myAutomaticRefresh = automaticRefresh;
    }

    public void setRefreshTimeout(final int refreshTimeout) {
        myRefreshTimeout = refreshTimeout;
    }

    public void setReviewNotifications(final boolean reviewNotifications) {
        myRefreshNotifications = reviewNotifications;
    }

    @NotNull
    public Collection<String> getTrustedHosts() {
        return myTrustedHosts;
    }

    public void addTrustedHost(String host) {
        if (!myTrustedHosts.contains(host)) {
            myTrustedHosts.add(host);
        }
    }

    public void setLog(Logger log) {
        this.log = log;
    }
}
