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

package com.urswolfer.intellij.plugin.gerrit;

import com.intellij.util.messages.Topic;

/**
 * Published on the application bus when the accounts or their passwords changed, which reaches every project, and on
 * a project's bus when it was bound to another account. Listeners can be called on any thread.
 */
public interface GerritAccountsListener {
    Topic<GerritAccountsListener> TOPIC = Topic.create("Gerrit accounts", GerritAccountsListener.class);

    void accountsChanged();
}
