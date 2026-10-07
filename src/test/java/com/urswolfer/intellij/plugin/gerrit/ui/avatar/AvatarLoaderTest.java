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

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AvatarLoaderTest {

    @Test
    public void decodesAnImage() throws IOException {
        BufferedImage image = AvatarLoader.decode(png(8, 8));
        Assert.assertNotNull(image);
        Assert.assertEquals(image.getWidth(), 8);
    }

    @Test
    public void bytesWhichAreNoImageAreNoAvatar() throws IOException {
        Assert.assertNull(AvatarLoader.decode("<html>not an image</html>".getBytes()));
        Assert.assertNull(AvatarLoader.decode(new byte[0]));
    }

    /**
     * A few kilobytes of white are a picture of 5000 x 5000 pixels, which would take 100 MB to decode.
     */
    @Test
    public void anImageOfTooManyPixelsIsNotDecoded() throws IOException {
        Assert.assertNull(AvatarLoader.decode(png(5000, 5000)));
    }

    @Test
    public void readsUpToTheLimitOnly() throws IOException {
        Assert.assertEquals(AvatarLoader.readLimited(new ByteArrayInputStream(new byte[1000])).length, 1000);
        try {
            AvatarLoader.readLimited(new ByteArrayInputStream(new byte[3 * 1024 * 1024]));
            Assert.fail("should not read more than the limit");
        } catch (IOException expected) {
            // the response is dropped
        }
    }

    /**
     * The IDE-wide authenticator would answer a host asking for a login with a remembered password or a dialog.
     */
    @Test
    public void onlyTheProxyIsAnsweredALogin() throws Exception {
        PasswordAuthentication proxyLogin = new PasswordAuthentication("proxy", "secret".toCharArray());
        List<Authenticator.RequestorType> asked = new ArrayList<>();
        Authenticator ide = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                asked.add(getRequestorType());
                return proxyLogin;
            }
        };
        Authenticator previous = Authenticator.getDefault();
        Authenticator.setDefault(ide);
        try {
            URL avatar = new URL("https://avatars.example.com/a.png");
            Assert.assertNull(Authenticator.requestPasswordAuthentication(AvatarLoader.PROXY_ONLY,
                "avatars.example.com", null, 443, "https", "realm", "basic", avatar, Authenticator.RequestorType.SERVER));
            Assert.assertTrue(asked.isEmpty(), "the IDE was asked for a login to the avatar host");

            Assert.assertSame(Authenticator.requestPasswordAuthentication(AvatarLoader.PROXY_ONLY,
                "proxy.example.com", null, 3128, "http", "realm", "basic", avatar, Authenticator.RequestorType.PROXY),
                proxyLogin);
        } finally {
            Authenticator.setDefault(previous);
        }
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png", out);
        return out.toByteArray();
    }
}
