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

package com.urswolfer.intellij.plugin.gerrit.signin;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.annotations.XCollection;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritAccounts;
import com.urswolfer.intellij.plugin.gerrit.GerritAccountsListener;
import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cookies a sign-in through the browser got from what stands in front of a Gerrit, such as a single sign-on
 * proxy, which the REST client then sends along, see {@link #withCookies}. Once the proxy no longer takes them, the
 * user signs in again.
 *
 * They are kept by host rather than by account, the way a browser keeps them: they belong to the person who signed
 * in, not to the Gerrit login, and the settings page checks credentials which belong to no account yet.
 *
 * The cookies go to the credential store. Which hosts have any is kept apart, in a file which is not synced, so that
 * a host without them never costs a read of the credential store: that is every host until someone signs in.
 *
 * @author Urs Wolfer
 */
@Service(Service.Level.APP)
@State(name = "GerritSignInCookies", storages = @Storage(value = "gerrit_sign_in.xml", roamingType = RoamingType.DISABLED))
public final class SignInCookies implements PersistentStateComponent<SignInCookies.HostsState> {

    public static final class HostsState {
        @XCollection(propertyElementName = "hosts", elementName = "host", valueAttributeName = "url")
        public List<String> hosts = new ArrayList<>();
    }

    /**
     * One cookie, without its domain and path: the REST client sends it to the host it was stored for, and nowhere
     * else.
     */
    public static final class StoredCookie {
        public final String name;
        public final String value;
        /** Milliseconds since the epoch, 0 for a cookie which lasts as long as the session. */
        public final long expires;

        public StoredCookie(String name, String value, long expires) {
            this.name = name;
            this.value = value;
            this.expires = expires;
        }
    }

    /**
     * Where the cookies are kept, behind an interface so that the rest can be tested without a credential store.
     */
    interface CredentialStore {
        @Nullable
        String read(String origin);

        void write(String origin, @Nullable String cookies);
    }

    private static final Logger LOG = Logger.getInstance(SignInCookies.class);

    /**
     * Taken by what writes the credential store, which can block for long, such as on a prompt to unlock it; what
     * reads for a request does without it.
     */
    private final Object writeLock = new Object();
    private final Set<String> hosts = ConcurrentHashMap.newKeySet();
    private final Map<String, List<StoredCookie>> cache = new ConcurrentHashMap<>();
    private final Map<String, Integer> versions = new ConcurrentHashMap<>();
    /** When reading found nothing, by host; asked for several times per request, so not again right away. */
    private final Map<String, Long> emptyReads = new ConcurrentHashMap<>();
    private volatile CredentialStore credentialStore = new CredentialStore() {
        @Override
        public String read(String origin) {
            Credentials credentials = PasswordSafe.getInstance().get(attributes(origin));
            return credentials != null ? credentials.getPasswordAsString() : null;
        }

        @Override
        public void write(String origin, @Nullable String cookies) {
            PasswordSafe.getInstance().set(attributes(origin), cookies != null ? new Credentials(null, cookies) : null);
        }
    };

    public static SignInCookies getInstance() {
        return ApplicationManager.getApplication().getService(SignInCookies.class);
    }

    void setCredentialStore(CredentialStore credentialStore) { // for tests
        this.credentialStore = credentialStore;
    }

    @Override
    public HostsState getState() {
        HostsState state = new HostsState();
        state.hosts = new ArrayList<>(new TreeSet<>(hosts));
        return state;
    }

    @Override
    public void loadState(@NotNull HostsState state) {
        hosts.clear();
        hosts.addAll(state.hosts);
    }

    /**
     * Reads the credential store the first time a host which has cookies is asked for, which blocks.
     *
     * @return the cookies to send to the Gerrit at this url, none where nobody signed in to it through the browser
     */
    public List<StoredCookie> get(String gerritUrl) {
        String origin = origin(gerritUrl);
        if (origin == null) {
            return Collections.emptyList();
        }
        List<StoredCookie> cookies = cache.get(origin);
        if (cookies != null) {
            return cookies;
        }
        if (!hosts.contains(origin)) {
            return Collections.emptyList();
        }
        // nothing read, such as from a credential store which is not unlocked or does not save, is asked for again,
        // but only a while later
        long now = System.currentTimeMillis();
        Long emptyRead = emptyReads.get(origin);
        if (emptyRead != null && now - emptyRead < 60_000) {
            return Collections.emptyList();
        }
        try {
            cookies = parse(credentialStore.read(origin));
        } catch (RuntimeException e) { // the requests go without, rather than fail
            LOG.warn("The cookies of the sign-in to " + origin + " could not be read", e);
            cookies = Collections.emptyList();
        }
        if (cookies.isEmpty()) {
            emptyReads.put(origin, now);
            return cookies;
        }
        // a sign-in while this was reading has the newer cookies
        List<StoredCookie> meanwhile = cache.putIfAbsent(origin, cookies);
        if (meanwhile != null) {
            return meanwhile;
        }
        if (!hosts.contains(origin)) { // forgotten while this was reading
            cache.remove(origin, cookies);
            return Collections.emptyList();
        }
        return cookies;
    }

    /**
     * @return the cookies of the host which have not expired, as the value of a Cookie header; empty for none
     */
    public String cookieHeader(String url) {
        StringBuilder header = new StringBuilder();
        valid(url).forEach((name, value) -> header.append(header.length() > 0 ? "; " : "").append(name).append('=').append(value));
        return header.toString();
    }

    private Map<String, String> valid(String url) {
        Map<String, String> cookies = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (StoredCookie cookie : get(url)) {
            if (cookie.expires == 0 || cookie.expires > now) {
                cookies.put(cookie.name, cookie.value);
            }
        }
        return cookies;
    }

    /**
     * Replaces the cookies of the host. Blocks on the credential store.
     */
    public void set(String gerritUrl, List<StoredCookie> cookies) {
        String origin = origin(gerritUrl);
        if (origin == null) {
            return;
        }
        synchronized (writeLock) {
            boolean written = false;
            try {
                credentialStore.write(origin, format(cookies));
                written = true;
            } catch (RuntimeException e) { // the sign-in still holds until the IDE closes
                LOG.warn("The cookies of the sign-in to " + origin + " could not be stored", e);
            }
            cache.put(origin, Collections.unmodifiableList(new ArrayList<>(cookies)));
            emptyReads.remove(origin);
            if (written) { // or else a restart would find the host without its cookies
                hosts.add(origin);
            }
            versions.merge(origin, 1, Integer::sum); // before the lock goes to a Cleanup, which keeps it by this
        }
        accountsChanged();
    }

    /**
     * For a sign-in which needed no new cookies, as Gerrit answered with those stored before, or none.
     */
    public void reached(String gerritUrl) {
        String origin = origin(gerritUrl);
        if (origin != null) {
            versions.merge(origin, 1, Integer::sum);
            accountsChanged();
        }
    }

    private static void accountsChanged() {
        // what was loaded before, such as the change list, loads again, as after a login; Cleanup keeps the cookies of
        // a host signed in to since the start, whose account may not be saved yet
        Application application = ApplicationManager.getApplication();
        if (application != null) { // none in the unit tests
            application.getMessageBus().syncPublisher(GerritAccountsListener.TOPIC).accountsChanged();
        }
    }

    /**
     * @return whether someone signed in to the host through the browser, whose cookies may not be readable right now
     */
    public boolean isSignedIn(String url) {
        String origin = origin(url);
        return origin != null && (hosts.contains(origin) || cache.containsKey(origin));
    }

    /**
     * @return the auth data, with the cookies of its host which have not expired, unless it has cookies itself, such
     *         as those the sign-in tries before storing them; read per call, as the REST client does with what the
     *         auth data says, which starts a new login when they change
     */
    public GerritAuthData withCookies(GerritAuthData authData) {
        return new GerritAuthData() {
            @Override
            public String getLogin() {
                return authData.getLogin();
            }

            @Override
            public String getPassword() {
                return authData.getPassword();
            }

            @Override
            public boolean isHttpPassword() {
                return authData.isHttpPassword();
            }

            @Override
            public String getHost() {
                return authData.getHost();
            }

            @Override
            public boolean isLoginAndPasswordAvailable() {
                return authData.isLoginAndPasswordAvailable();
            }

            @Override
            public Map<String, String> getCookies() {
                Map<String, String> own = authData.getCookies();
                return own.isEmpty() ? valid(getHost()) : own;
            }
        };
    }

    /**
     * @return how often someone signed in to the host since the IDE started, which tells what was loaded before a
     *         sign-in apart from what is loaded after it, as a password version does
     */
    public int getVersion(String gerritUrl) {
        String origin = origin(gerritUrl);
        return origin != null ? versions.getOrDefault(origin, 0) : 0;
    }

    /**
     * Forgets the cookies of hosts which no account is on any more, but not of one signed in to since the IDE started:
     * the account for it may still be waiting on the settings page to be applied. Blocks on the credential store.
     */
    void forgetUnused(List<GerritAccount> accounts) {
        Set<String> used = new HashSet<>();
        for (GerritAccount account : accounts) {
            used.add(origin(account.host));
        }
        synchronized (writeLock) {
            for (String origin : new ArrayList<>(hosts)) {
                if (!used.contains(origin) && !versions.containsKey(origin)) {
                    try {
                        credentialStore.write(origin, null);
                    } catch (RuntimeException e) { // kept, to be tried again with the next change
                        LOG.warn("The cookies of the sign-in to " + origin + " could not be removed", e);
                        continue;
                    }
                    hosts.remove(origin);
                    cache.remove(origin);
                }
            }
        }
    }

    /**
     * Cookies go by scheme, host and port, not by the path Gerrit is served under.
     *
     * @return the url as {@code scheme://host[:port]}, lower case and without the default port; {@code null} for
     *         one which names no host
     */
    @Nullable
    public static String origin(@Nullable String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            HttpHost host = URIUtils.extractHost(uri);
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            int port = host.getPort();
            boolean defaultPort = port == -1 || "https".equals(scheme) && port == 443 || "http".equals(scheme) && port == 80;
            return scheme + "://" + host.getHostName().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * As the REST client reads it, which gets along with a name {@link URI#getHost()} does not, such as one with an
     * underscore.
     *
     * @return {@code null} for a url which names no host
     */
    @Nullable
    public static String hostName(@Nullable String url) {
        try {
            HttpHost host = url != null ? URIUtils.extractHost(new URI(url.trim())) : null;
            return host != null ? host.getHostName() : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    // a cookie's name and value hold neither tabs nor line breaks, so these separate them
    static String format(List<StoredCookie> cookies) {
        StringBuilder text = new StringBuilder();
        for (StoredCookie cookie : cookies) {
            text.append(cookie.name).append('\t').append(cookie.value).append('\t').append(cookie.expires).append('\n');
        }
        return text.toString();
    }

    static List<StoredCookie> parse(@Nullable String text) {
        List<StoredCookie> cookies = new ArrayList<>();
        if (text == null) {
            return cookies;
        }
        for (String line : text.split("\n")) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 3 || fields[0].isEmpty()) {
                continue;
            }
            long expires;
            try {
                expires = Long.parseLong(fields[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            cookies.add(new StoredCookie(fields[0], fields[1], expires));
        }
        return Collections.unmodifiableList(cookies);
    }

    private static CredentialAttributes attributes(String origin) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("Gerrit sign-in", origin), origin);
    }

    /**
     * Drops the cookies of a host along with its last account. On a thread of its own: the credential store blocks,
     * and the accounts can change on the event dispatch thread.
     */
    public static final class Cleanup implements GerritAccountsListener {
        @Override
        public void accountsChanged() {
            ApplicationManager.getApplication().executeOnPooledThread(
                () -> getInstance().forgetUnused(GerritAccounts.getInstance().getAccounts()));
        }
    }
}
