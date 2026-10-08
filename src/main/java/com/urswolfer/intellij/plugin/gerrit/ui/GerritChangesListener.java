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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.util.Consumer;
import com.intellij.util.messages.Topic;

/**
 * Published on the message bus of the project once Gerrit has applied a modification of changes, so that the list
 * shows it wherever the modification was started from. Published on the event dispatch thread.
 */
public interface GerritChangesListener {
    @Topic.ProjectLevel
    Topic<GerritChangesListener> TOPIC = new Topic<>(GerritChangesListener.class, Topic.BroadcastDirection.NONE);

    /**
     * The listed changes are no longer what Gerrit has, e.g. an abandoned change is still listed as open.
     */
    void changesModified();

    /**
     * For a modification which does not justify reloading the list.
     *
     * @param update applies the modification to the listed change
     */
    void changeModified(String changeId, Consumer<ChangeInfo> update);
}
