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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.intellij.util.messages.Topic;

/**
 * Published when a setting changed which decides what the list of changes shows, so that the tool windows of all
 * open projects reload it. The tool windows are created lazily by the platform and are not registered anywhere else.
 */
public interface GerritListSettingsListener {
    Topic<GerritListSettingsListener> TOPIC = Topic.create("Gerrit list settings", GerritListSettingsListener.class);

    void listSettingsChanged();
}
