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

package com.urswolfer.intellij.plugin.gerrit.push;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.BiFunction;

/**
 * The texts of the push dialog panels. The panels run in the class loader of the Git plugin, which cannot load
 * {@code GerritBundle} or find its resource bundle, so the texts are handed over as a {@link BiFunction}: a type
 * which both class loaders share. See {@link GerritPushExtension}.
 */
public final class PushMessages {
    private static volatile BiFunction<String, Object[], String> provider = PushMessages::fromBundle;

    private PushMessages() {}

    @SuppressWarnings("unused") // called by reflection, on the copy in the Git plugin class loader
    public static void setProvider(BiFunction<String, Object[], String> provider) {
        PushMessages.provider = provider != null ? provider : PushMessages::fromBundle;
    }

    public static String message(String key, Object... params) {
        return provider.apply(key, params);
    }

    /**
     * What is used until the texts are handed over, and what the unit tests see. The copy in the Git plugin class
     * loader does not find the bundle: it shows the key then, which a panel can live with better than an error.
     */
    private static String fromBundle(String key, Object[] params) {
        try {
            String pattern = ResourceBundle.getBundle("messages.GerritBundle", Locale.ROOT,
                PushMessages.class.getClassLoader()).getString(key);
            return params.length > 0 ? MessageFormat.format(pattern, params) : pattern;
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
