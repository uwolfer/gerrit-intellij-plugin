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

package com.urswolfer.intellij.plugin.gerrit.signin.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefCookieManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.RestClient;
import com.urswolfer.gerrit.client.rest.http.HttpStatusException;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiProvider;
import com.urswolfer.intellij.plugin.gerrit.signin.SignInCookies;
import com.urswolfer.intellij.plugin.gerrit.signin.SignInCookies.StoredCookie;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefCookie;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows the Gerrit in the embedded browser, where whatever stands in front of it asks the user to sign in, and closes
 * once a page loaded on Gerrit's host, by any scheme or port, as Gerrit may redirect from http to https, and its REST
 * API answers with the cookies the browser has for that page by then: those are the ones that let it through, and
 * are stored for the REST client. Where it answers without them, nothing is stored: there is nothing in front of it
 * which needs them, and a cookie of Gerrit's own, such as for a load balancer, would replace the one it sets.
 *
 * Gerrit's own session and XSRF cookies are left out. The REST client logs in to Gerrit itself, and would take a
 * session from the browser for one it had made, without the XSRF token which goes with it.
 *
 * @author Urs Wolfer
 */
final class BrowserSignInDialog extends DialogWrapper {
    private static final Logger LOG = Logger.getInstance(BrowserSignInDialog.class);
    private static final Set<String> GERRIT_COOKIES = new HashSet<>(Arrays.asList("GerritAccount", "XSRF_TOKEN"));

    private static final int RETRY_SECONDS = 5;

    private final String gerritUrl;
    @Nullable private final String gerritHost;
    private final JBCefBrowser browser;
    private final AtomicBoolean storing = new AtomicBoolean();
    private final AtomicBoolean retrying = new AtomicBoolean();
    private final AtomicBoolean warned = new AtomicBoolean();
    /** Of the last page on Gerrit's host. */
    private volatile String gerritPage;

    BrowserSignInDialog(@Nullable Project project, @Nullable Component parent, String gerritUrl) {
        super(project, parent, true, IdeModalityType.IDE);
        this.gerritUrl = gerritUrl;
        gerritHost = SignInCookies.hostName(gerritUrl);
        browser = new JBCefBrowser(gerritUrl);
        Disposer.register(getDisposable(), browser);
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                // a page of Gerrit, not of the sign-in, and not one of the redirects in between
                String url = cefBrowser.getURL();
                if (!frame.isMain()) {
                    return;
                }
                if (httpStatusCode >= 200 && httpStatusCode < 300 && gerritHost != null
                    && gerritHost.equalsIgnoreCase(SignInCookies.hostName(url))) {
                    gerritPage = url;
                    readCookies(url);
                } else { // such as on to the sign-in, which tries nothing while the user is there
                    gerritPage = null;
                }
            }
        }, browser.getCefBrowser());
        setTitle(GerritBundle.message("signIn.title", gerritUrl));
        init();
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getCancelAction()};
    }

    @Override
    protected JComponent createNorthPanel() {
        JBLabel hint = new JBLabel(GerritBundle.message("signIn.hint"));
        hint.setBorder(JBUI.Borders.emptyBottom(8));
        return hint;
    }

    @Override
    protected JComponent createCenterPanel() {
        JComponent component = browser.getComponent();
        component.setPreferredSize(JBUI.size(960, 720));
        return component;
    }

    /**
     * The cookie manager answers on a thread of the browser, one cookie per call, and not at all where there are no
     * cookies.
     *
     * TODO once the minimum IDE has JBCefCookieManager.getCookies(String, Boolean) returning a Future (2020.3 has not,
     *  2026.2 has): use it instead of the visitor and the timeout. The getCookies of 2020.3 with a time to wait is
     *  private by 2024.1.
     */
    private void readCookies(String page) {
        List<CefCookie> found = Collections.synchronizedList(new ArrayList<>());
        boolean https = gerritUrl.regionMatches(true, 0, "https:", 0, 6);
        CompletableFuture<Void> visited = new CompletableFuture<>();
        // those of the page, where a Secure one is there by https even if the url of the account has http
        boolean visiting = new JBCefCookieManager().getCefCookieManager().visitUrlCookies(page, true,
            (cookie, count, total, delete) -> {
                // one the browser keeps to https would go by plain http to a Gerrit whose url has that
                if (!GERRIT_COOKIES.contains(cookie.name) && !(cookie.secure && !https)) {
                    found.add(cookie);
                }
                if (count + 1 >= total) {
                    visited.complete(null);
                }
                return true;
            });
        if (!visiting) {
            if (warned.compareAndSet(false, true)) { // not on every retry
                LOG.warn("The browser's cookies cannot be read");
            }
            retryLater();
            return;
        }
        visited.completeOnTimeout(null, 2, TimeUnit.SECONDS).thenRun(() -> {
            List<StoredCookie> cookies;
            synchronized (found) {
                cookies = mostSpecific(found);
            }
            if (storing.compareAndSet(false, true)) {
                store(cookies);
            }
        });
    }

    /**
     * A page on Gerrit's host is not yet Gerrit: a proxy may show its sign-in page there, and the browser may still
     * have the cookies of a session the proxy has forgotten. Only a request for the REST API getting past the proxy
     * tells, one under {@code /a/}, which a proxy may guard where it lets the web UI through: any answer but its
     * sign-in page, Gerrit's refusal of a request without a login included. Blocks.
     *
     * @param cookies none for those stored before
     */
    private boolean reachGerrit(List<StoredCookie> cookies) {
        Map<String, String> values = new LinkedHashMap<>();
        for (StoredCookie cookie : cookies) {
            values.put(cookie.name, cookie.value);
        }
        GerritAuthData probe = new GerritAuthData.Basic(gerritUrl) {
            @Override
            public Map<String, String> getCookies() {
                return values;
            }
        };
        try {
            HttpResponse response = GerritApiProvider.getInstance().create(probe).restClient()
                .requestRest("/a/config/server/info", null, RestClient.HttpVerb.GET);
            EntityUtils.consumeQuietly(response.getEntity());
            return true;
        } catch (HttpStatusException e) {
            return true;
        } catch (IOException e) {
            LOG.debug("Gerrit not reached through the cookies of the sign-in yet", e);
            return false;
        }
    }

    /**
     * The REST client sends one cookie of a name, while the browser may have several, of the host and of a domain
     * above it, or of several paths: the one it sends first, of the longest path and then of the host itself, and the
     * newest of the rest.
     */
    private static List<StoredCookie> mostSpecific(List<CefCookie> cookies) {
        Map<String, CefCookie> byName = new LinkedHashMap<>();
        Comparator<CefCookie> specific = Comparator
            .<CefCookie>comparingInt(cookie -> cookie.path != null ? cookie.path.length() : 0)
            .thenComparingInt(cookie -> cookie.domain != null && !cookie.domain.startsWith(".") ? 1 : 0)
            .thenComparingInt(cookie -> cookie.domain != null ? cookie.domain.length() : 0)
            .thenComparing(cookie -> cookie.creation, Comparator.nullsFirst(Comparator.naturalOrder()));
        for (CefCookie cookie : cookies) {
            byName.merge(cookie.name, cookie, (one, other) -> specific.compare(one, other) >= 0 ? one : other);
        }
        List<StoredCookie> stored = new ArrayList<>();
        for (CefCookie cookie : byName.values()) {
            stored.add(stored(cookie));
        }
        return stored;
    }

    private static StoredCookie stored(CefCookie cookie) {
        long expires = cookie.hasExpires && cookie.expires != null ? cookie.expires.getTime() : 0;
        return new StoredCookie(cookie.name, cookie.value, expires);
    }

    /**
     * The credential store blocks, so not on the event dispatch thread; the window closes once they are stored, or
     * Gerrit answers with those stored before. Otherwise the cookies are tried again a while later, as a proxy may set
     * its cookie from the page a while after it loaded.
     */
    private void store(List<StoredCookie> cookies) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean stored = false;
            try {
                if (reachGerrit(Collections.emptyList())) {
                    SignInCookies.getInstance().reached(gerritUrl);
                    stored = true;
                } else if (!cookies.isEmpty() && reachGerrit(cookies) && !isDisposed()) { // cancelled: nothing to store
                    SignInCookies.getInstance().set(gerritUrl, cookies);
                    stored = true;
                }
            } catch (RuntimeException e) {
                LOG.warn("The cookies of the sign-in could not be stored", e);
            } finally {
                storing.set(stored); // unless stored, the next page of Gerrit tries again
            }
            if (stored) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisposed()) {
                        close(OK_EXIT_CODE);
                    }
                }, ModalityState.any());
            } else {
                retryLater();
            }
        });
    }

    /**
     * Gerrit's web UI loads no other page that would try again.
     */
    private void retryLater() {
        if (!retrying.compareAndSet(false, true)) { // one at a time, however many pages failed
            return;
        }
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            retrying.set(false);
            String page = gerritPage;
            if (!isDisposed() && page != null) {
                readCookies(page);
            }
        }, RETRY_SECONDS, TimeUnit.SECONDS);
    }

}
