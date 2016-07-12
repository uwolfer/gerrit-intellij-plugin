/*
 * Copyright 2013-2015 Urs Wolfer
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

package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * This "copy" is required because Git4ideaIcons.CherryPick got moved to DvcsImplIcons and we don't want to create
 * just another plugin build for these newer versions. Can be removed once we depend on a version which
 * delivers DvcsImplIcons.
 *
 * IntelliJ commit: d13a4725f16636aed289cd084b9bfff92e9237b8
 *
 * @author Urs Wolfer
 */
public final class GerritIcons {

    private GerritIcons() {}

    private static Icon load(String path) {
        return IconLoader.getIcon(path, GerritIcons.class);
    }

    public static final Icon CherryPick = load("/icons/cherryPick.png"); // 16x16
}
