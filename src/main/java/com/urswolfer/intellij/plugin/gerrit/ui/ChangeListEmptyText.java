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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Which text the change list shows when a load found nothing: a lookup which found no change, filters which
 * match nothing and a project without changes must not read alike.
 */
final class ChangeListEmptyText {
    /** Longer reasons are left to the notification, which has room for them. */
    private static final int MAX_REASON_LENGTH = 100;

    enum Kind {
        /** The load found nothing for the commits looked up. */
        NO_LOOKUP_RESULT,
        /** The load found nothing, and a filter is set to something else than at the start. */
        NO_MATCH,
        /** The load found nothing with the filters at their defaults. */
        NO_CHANGES
    }

    private ChangeListEmptyText() {
    }

    @VisibleForTesting
    static Kind of(boolean lookup, boolean filtersNarrowed) {
        if (lookup) {
            return Kind.NO_LOOKUP_RESULT;
        }
        return filtersNarrowed ? Kind.NO_MATCH : Kind.NO_CHANGES;
    }

    /**
     * @return the message of a failure if it fits a line of the empty text, otherwise null
     */
    @Nullable
    @VisibleForTesting
    static String reason(@Nullable String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_REASON_LENGTH || trimmed.indexOf('\n') >= 0) {
            return null;
        }
        return trimmed;
    }
}
