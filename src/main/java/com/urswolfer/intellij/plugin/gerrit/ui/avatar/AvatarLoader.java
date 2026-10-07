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

package com.urswolfer.intellij.plugin.gerrit.ui.avatar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.ssl.CertificateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Downloads avatar images once per URL, the way the GitHub plugin's avatar loader does. Avatars are public, so the
 * request carries no Gerrit credentials, and goes through the IDE's proxy and certificate settings.
 */
@Service(Service.Level.APP)
public final class AvatarLoader {
    private static final Logger LOG = Logger.getInstance(AvatarLoader.class);
    private static final int MAX_CACHED = 200;
    private static final int STORED_IMAGE_SIZE = 128;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final long MAX_PIXELS = 1024L * 1024L;

    private final Executor executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Gerrit avatars", 2);

    // The last access decides what stays, as a new page of changes mostly shows the same owners again.
    private final Map<String, CompletableFuture<Image>> cache = new LinkedHashMap<String, CompletableFuture<Image>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<Image>> eldest) {
            return size() > MAX_CACHED;
        }
    };

    public static AvatarLoader getInstance() {
        return ApplicationManager.getApplication().getService(AvatarLoader.class);
    }

    /**
     * @return the image; {@code null} when there is none to be had at that URL, and completed exceptionally when
     *         it could not be loaded now. Whoever asks again after that, as after the IDE having started offline,
     *         gets another try, and decides how soon that is.
     */
    @NotNull
    public CompletableFuture<Image> request(@NotNull String url) {
        synchronized (cache) {
            CompletableFuture<Image> known = cache.get(url);
            if (known != null) {
                return known;
            }
            CompletableFuture<Image> loading = CompletableFuture.supplyAsync(() -> loadOnce(url), executor);
            cache.put(url, loading);
            loading.whenComplete((image, error) -> {
                // a failure which another try may change is not kept; one which it will not, as an image which is
                // too large or not one, is, so that it is not downloaded again every time
                if (error != null) {
                    synchronized (cache) {
                        cache.remove(url, loading);
                    }
                }
            });
            return loading;
        }
    }

    /**
     * One attempt: a failure which a later one may mend is tried again by the caller, a while later, rather than
     * here, where waiting would hold up the avatars queued behind it.
     */
    @Nullable
    private static Image loadOnce(String url) {
        try {
            return load(url);
        } catch (HttpRequests.HttpStatusException e) {
            int status = e.getStatusCode();
            // gone or not allowed, as it will stay; a timeout, a limit on requests or a proxy login is not
            if (status >= 400 && status < 500 && status != 407 && status != 408 && status != 429) {
                LOG.debug("Could not load avatar " + url, e);
                return null;
            }
            throw failure(url, e);
        } catch (RefusedException | MalformedURLException e) { // as a redirect to a relative URL in 2020.3
            LOG.debug("Could not load avatar " + url, e);
            return null;
        } catch (IOException e) { // an untrusted certificate too: the user may accept it in the settings meanwhile
            throw failure(url, e);
        }
    }

    private static CompletionException failure(String url, IOException e) {
        // debug only: an IDE without network fails this for every owner, again and again
        LOG.debug("Could not load avatar " + url, e);
        return new CompletionException(e);
    }

    private static Image load(String url) throws IOException {
        // the URL is the Gerrit server's to name, so what comes back is not trusted to be small
        byte[] bytes = HttpRequests.request(url).connectTimeout(10_000).readTimeout(15_000)
            .tuner(AvatarLoader::tune)
            .connect(request -> readLimited(request.getInputStream()));
        BufferedImage image = decode(bytes);
        if (image == null) { // not an image format Java reads, which another attempt does not change
            return null;
        }
        int largest = Math.max(image.getWidth(), image.getHeight());
        if (largest > STORED_IMAGE_SIZE) {
            double scale = (double) STORED_IMAGE_SIZE / largest;
            BufferedImage smaller = new BufferedImage(Math.max(1, (int) Math.round(image.getWidth() * scale)),
                Math.max(1, (int) Math.round(image.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = smaller.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(image, 0, 0, smaller.getWidth(), smaller.getHeight(), null);
            } finally {
                g.dispose();
            }
            return smaller;
        }
        return image;
    }

    /**
     * HttpRequests follows redirects itself, wherever they lead, and opens every connection with the IDE-wide
     * authenticator, which answers a host asking for a login with a password registered for it. Over HTTPS it asks
     * the user about a certificate the IDE does not trust yet, in a modal dialog. None of that is wanted for a host
     * the Gerrit server names: only the proxy gets a login, and a certificate is trusted or the avatar not loaded.
     */
    private static void tune(URLConnection connection) throws IOException {
        if (!(connection instanceof HttpURLConnection)) {
            throw new RefusedException("Not an HTTP avatar: " + connection.getURL());
        }
        ((HttpURLConnection) connection).setAuthenticator(PROXY_ONLY);
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(UnaskedTrust.SOCKET_FACTORY);
        }
    }

    /**
     * What the IDE trusts, the system's certificates and those the user accepted, without asking about any other.
     */
    private static final class UnaskedTrust implements X509TrustManager {
        private static final SSLSocketFactory SOCKET_FACTORY = createSocketFactory();

        private static SSLSocketFactory createSocketFactory() {
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                // the key managers of the IDE's own context, for a host which wants a client certificate
                context.init(CertificateManager.getDefaultKeyManagers(), new TrustManager[]{new UnaskedTrust()}, null);
                return context.getSocketFactory();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateManager.getInstance().getTrustManager().checkServerTrusted(chain, authType, false, false);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Not a server");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return CertificateManager.getInstance().getTrustManager().getAcceptedIssuers();
        }
    }

    @VisibleForTesting
    static final Authenticator PROXY_ONLY = new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (getRequestorType() != RequestorType.PROXY) {
                return null;
            }
            Authenticator ide = Authenticator.getDefault();
            return ide == null ? null : Authenticator.requestPasswordAuthentication(ide, getRequestingHost(),
                getRequestingSite(), getRequestingPort(), getRequestingProtocol(), getRequestingPrompt(),
                getRequestingScheme(), getRequestingURL(), getRequestorType());
        }
    };

    @VisibleForTesting
    static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_BYTES) {
                throw new RefusedException("Avatar is larger than " + MAX_BYTES + " bytes");
            }
        }
        return out.toByteArray();
    }

    /**
     * Reads the size before the pixels, as a small file can be an image of billions of them.
     */
    @VisibleForTesting
    @Nullable
    static BufferedImage decode(byte[] bytes) {
        // in memory: ImageIO.createImageInputStream would write the bytes it has to a temporary file
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) { // a broken image, which another download does not mend
            LOG.debug("Could not decode avatar", e);
            return null;
        }
    }

    @VisibleForTesting
    // not to be had, as another try will not change
    static final class RefusedException extends IOException {
        RefusedException(String message) {
            super(message);
        }
    }
}
