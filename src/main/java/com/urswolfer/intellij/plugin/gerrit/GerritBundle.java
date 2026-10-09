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

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Settled, a review should not raise these again:
 *
 * <ul>
 *   <li>dialog mnemonics stay bound to the English letters until a translation exists to choose from;</li>
 *   <li>keys with the same English text are shared where the meaning is the same, and separate where the role
 *   differs;</li>
 *   <li>brand and product names ("Gerrit", "Gitiles") stay in code and {@code plugin.xml}; so does content sent to
 *   Gerrit, such as the git-style labels of {@code CommitMessageFormatter}, the revert message and the merge
 *   subject.</li>
 * </ul>
 */
public final class GerritBundle extends DynamicBundle {
    private static final String BUNDLE = "messages.GerritBundle";
    private static final GerritBundle INSTANCE = new GerritBundle();

    private GerritBundle() {
        super(BUNDLE);
    }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }
}
