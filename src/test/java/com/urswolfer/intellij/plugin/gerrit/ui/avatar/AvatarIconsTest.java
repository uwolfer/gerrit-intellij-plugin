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
import org.testng.Assert;
import org.testng.annotations.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;

public class AvatarIconsTest {

    @Test
    public void noAvatarsPickNothing() {
        Assert.assertNull(AvatarIcons.pick(null, 16));
        Assert.assertNull(AvatarIcons.pick(Collections.emptyList(), 16));
    }

    @Test
    public void picksTheSmallestOfAtLeastTheSizeWanted() {
        AvatarInfo small = avatar("https://example.com/s", 26);
        AvatarInfo medium = avatar("https://example.com/m", 40);
        AvatarInfo large = avatar("https://example.com/l", 100);
        Assert.assertSame(AvatarIcons.pick(Arrays.asList(large, small, medium), 16), small);
        // closer to 32 is the 26 one, which would be scaled up on a screen of twice the resolution
        Assert.assertSame(AvatarIcons.pick(Arrays.asList(large, small, medium), 32), medium);
    }

    @Test
    public void picksTheLargestWhenNoneIsLargeEnough() {
        AvatarInfo small = avatar("https://example.com/s", 26);
        AvatarInfo medium = avatar("https://example.com/m", 40);
        Assert.assertSame(AvatarIcons.pick(Arrays.asList(medium, small), 64), medium);
    }

    @Test
    public void anAvatarWithoutHeightIsTheDefaultSize() {
        AvatarInfo unsized = avatar("https://example.com/u", null);
        AvatarInfo large = avatar("https://example.com/l", 100);
        Assert.assertSame(AvatarIcons.pick(Arrays.asList(large, unsized), AvatarInfo.DEFAULT_SIZE), unsized);
        Assert.assertSame(AvatarIcons.pick(Arrays.asList(large, unsized), AvatarInfo.DEFAULT_SIZE + 1), large);
    }

    @Test
    public void skipsUrlsWhichCannotBeFetched() {
        AvatarInfo relative = avatar("/plugins/avatars/a.png", 16);
        AvatarInfo none = avatar(null, 16);
        AvatarInfo http = avatar("http://example.com/h", 100);
        Assert.assertSame(AvatarIcons.pick(Arrays.asList(relative, none, http), 16), http);
        Assert.assertNull(AvatarIcons.pick(Arrays.asList(relative, none), 16));
    }

    @Test
    public void anAccountHasAnAvatarOnlyWithAFetchableUrl() {
        AccountInfo none = new AccountInfo(1);
        Assert.assertFalse(AvatarIcons.hasAvatar(null));
        Assert.assertFalse(AvatarIcons.hasAvatar(none));
        none.avatars = Collections.singletonList(avatar("/relative.png", 16));
        Assert.assertFalse(AvatarIcons.hasAvatar(none));
        none.avatars = Collections.singletonList(avatar("https://example.com/a.png", 16));
        Assert.assertTrue(AvatarIcons.hasAvatar(none));
    }

    @Test
    public void roundsTheMiddleSquareWithASoftEdge() {
        BufferedImage wide = new BufferedImage(60, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = wide.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 60, 40);
        g.dispose();

        BufferedImage round = AvatarIcons.roundImage(wide, 32);

        Assert.assertEquals(round.getWidth(), 32);
        Assert.assertEquals(round.getRGB(16, 16), Color.RED.getRGB());
        Assert.assertEquals(round.getRGB(0, 0) >>> 24, 0, "the corner is outside the circle");
        int edge = round.getRGB(2, 7) >>> 24; // on the rim, where it is neither in nor out
        Assert.assertTrue(edge > 0 && edge < 255, "the edge is anti-aliased: " + edge);
    }

    private static AvatarInfo avatar(String url, Integer height) {
        AvatarInfo avatar = new AvatarInfo();
        avatar.url = url;
        avatar.height = height;
        return avatar;
    }
}
