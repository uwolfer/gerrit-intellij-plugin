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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AvatarInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The avatar icons of one component, which is repainted when an avatar arrives. Like the GitHub plugin's icons
 * provider, it hands out an icon at once, a placeholder until the image is there.
 *
 * TODO once the minimum IDE has com.intellij.ui.AsyncImageIcon and com.intellij.collaboration.ui.icon's
 * AsyncImageIconsProvider and CachingIconsProvider (2020.3 has not, 2026.2 has): build the icons with them, as the
 * GitHub plugin does, and drop AvatarIcon, its retry timing and the icon map. AsyncImageIcon also renders again when
 * the screen's scale changes, which this one does not. Their loader is a Kotlin suspend function, so it wants a
 * Kotlin class or a coroutine bridge, and plugin.xml may have to declare the collaboration tools module.
 */
public final class AvatarIcons {
    private static final Logger LOG = Logger.getInstance(AvatarIcons.class);
    private static final int MAX_ICONS = 500;
    // the first retry soon, for a failure of the moment, later ones seldom, for a host which is not to be reached
    private static final long FIRST_RETRY_AFTER_MILLIS = 30 * 1000L;
    private static final long RETRY_AFTER_MILLIS = 5 * 60 * 1000L;

    private final JComponent component;
    private final int size;
    // Bounded, as an icon keeps its image for as long as the panel lives; one dropped here is made again at its next
    // paint, from the loader's image while the loader still has it.
    private final Map<String, AvatarIcon> icons = Collections.synchronizedMap(
        new LinkedHashMap<String, AvatarIcon>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AvatarIcon> eldest) {
                return size() > MAX_ICONS;
            }
        });

    /**
     * @param size the edge of the icon in unscaled pixels
     */
    public AvatarIcons(@NotNull JComponent component, int size) {
        this.component = component;
        this.size = size;
    }

    public static boolean hasAvatar(@Nullable AccountInfo account) {
        return account != null && account.avatars != null && account.avatars.stream().anyMatch(AvatarIcons::isFetchable);
    }

    /**
     * @return {@code null} for an account without an avatar, as on a Gerrit without an avatar plugin, where nothing
     *         should take room in the list
     */
    @Nullable
    public Icon getIcon(@Nullable AccountInfo account) {
        int pixels = pixels();
        AvatarInfo avatar = account != null ? pick(account.avatars, pixels) : null;
        if (avatar == null) {
            return null;
        }
        AvatarIcon icon = icons.computeIfAbsent(avatar.url, url -> new AvatarIcon(size));
        if (icon.needsRequest()) {
            request(avatar.url, icon, pixels);
        }
        return icon;
    }

    /**
     * What the icon takes of the screen, which is more than its size where the UI is scaled or the screen has a
     * higher resolution, and what an avatar has to be of to stay sharp.
     */
    private int pixels() {
        return Math.round(JBUI.scale(size) * JBUIScale.sysScale(component));
    }

    private void request(String url, AvatarIcon icon, int pixels) {
        AvatarLoader.getInstance().request(url).thenAcceptAsync(image -> {
            if (image == null) { // none there, as it will stay
                markGone(icon);
                return;
            }
            // drawn at the screen's resolution here, off the EDT, rather than on each repaint
            BufferedImage round;
            try {
                round = roundImage(image, pixels);
            } catch (RuntimeException e) { // the same image again would fail the same way
                LOG.debug("Could not draw avatar " + url, e);
                markGone(icon);
                return;
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                icon.image = round;
                component.repaint();
            }, ModalityState.any());
        }, AppExecutorUtil.getAppExecutorService()).exceptionally(error -> {
            // the paint at the time of the retry asks again; on a list nobody touches, none would come of itself
            long retryAfter = icon.failed();
            AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> ApplicationManager.getApplication()
                .invokeLater(component::repaint, ModalityState.any()), retryAfter, TimeUnit.MILLISECONDS);
            return null;
        });
    }

    // nothing is painted, and the room stays as it is
    private void markGone(AvatarIcon icon) {
        ApplicationManager.getApplication().invokeLater(() -> {
            icon.gone = true;
            component.repaint();
        }, ModalityState.any());
    }

    /**
     * The middle square of the image as a circle. The edge is drawn here, with its own anti-aliasing: a clip set
     * while painting would be a jagged one.
     */
    @VisibleForTesting
    static BufferedImage roundImage(Image source, int pixels) {
        int width = source.getWidth(null);
        int height = source.getHeight(null);
        int side = Math.min(width, height);
        BufferedImage result = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setComposite(AlphaComposite.Src);
            g.fill(new Ellipse2D.Float(0, 0, pixels, pixels));
            g.setComposite(AlphaComposite.SrcIn);
            int left = (width - side) / 2;
            int top = (height - side) / 2;
            g.drawImage(source, 0, 0, pixels, pixels, left, top, left + side, top + side, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    /**
     * The smallest avatar of at least the size wanted, so that none is scaled up while a larger one is there, else
     * the largest. One which Gerrit sends without a size counts as its default size.
     */
    @VisibleForTesting
    @Nullable
    static AvatarInfo pick(@Nullable List<AvatarInfo> avatars, int wanted) {
        if (avatars == null) {
            return null;
        }
        AvatarInfo smallestLargeEnough = null;
        AvatarInfo largest = null;
        for (AvatarInfo avatar : avatars) {
            if (!isFetchable(avatar)) {
                continue;
            }
            int height = height(avatar);
            if (height >= wanted && (smallestLargeEnough == null || height < height(smallestLargeEnough))) {
                smallestLargeEnough = avatar;
            }
            if (largest == null || height > height(largest)) {
                largest = avatar;
            }
        }
        return smallestLargeEnough != null ? smallestLargeEnough : largest;
    }

    private static int height(AvatarInfo avatar) {
        return avatar.height != null ? avatar.height : AvatarInfo.DEFAULT_SIZE;
    }

    // a relative URL is the avatar plugin of the Gerrit, which is not reached without its host
    private static boolean isFetchable(@Nullable AvatarInfo avatar) {
        return avatar != null && avatar.url != null
            && (avatar.url.startsWith("https://") || avatar.url.startsWith("http://"));
    }

    private static final class AvatarIcon implements Icon {
        private final int size;
        private volatile BufferedImage image;
        private volatile boolean gone;
        private long requestedAt;
        private boolean failed;
        private int failures;

        private AvatarIcon(int size) {
            this.size = size;
        }

        /**
         * Paints ask for the icon over and over, so a failed request is not repeated at once: the avatar host may
         * only be unreachable for now, as when the IDE was started offline.
         */
        private synchronized boolean needsRequest() {
            long now = System.currentTimeMillis();
            if (image != null || requestedAt != 0 && (!failed || now - requestedAt < retryAfter())) {
                return false;
            }
            requestedAt = now;
            failed = false;
            return true;
        }

        /**
         * @return how long until the next request may be made
         */
        private synchronized long failed() {
            // from now rather than from the request, which may have waited long in the loader's queue
            requestedAt = System.currentTimeMillis();
            failed = true;
            failures++;
            return retryAfter();
        }

        private long retryAfter() {
            return failures <= 1 ? FIRST_RETRY_AFTER_MILLIS : RETRY_AFTER_MILLIS;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (gone) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int edge = getIconWidth();
                Image current = image;
                if (current != null) {
                    g2.drawImage(current, x, y, edge, edge, null);
                } else {
                    g2.setColor(JBColor.border());
                    g2.fill(new Ellipse2D.Float(x, y, edge, edge));
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return JBUI.scale(size);
        }

        @Override
        public int getIconHeight() {
            return JBUI.scale(size);
        }
    }
}
