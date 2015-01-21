/*
 * Copyright 2013-2014 Urs Wolfer
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

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Resources;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Urs Wolfer
 */
public class Version {

    private static final String PLUGIN_VERSION;

    static {
        try {
            URL url = Version.class.getClassLoader().getResource("META-INF/plugin.xml");
            PLUGIN_VERSION = parseVersionFromFile(url);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static String parseVersionFromFile(URL url) throws IOException {
        String text = Resources.toString(url, Charsets.UTF_8);

        Pattern versionTagPattern = Pattern.compile(".*?<version>(.+?)</version>");
        Matcher matcher = versionTagPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "<unknown>";
        }
    }

    public static String get() {
        return PLUGIN_VERSION;
    }

}
