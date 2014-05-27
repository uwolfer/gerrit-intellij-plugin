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

package com.urswolfer.gerrit.client.rest.http.common;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.LabelInfo;

/**
 * @author Thomas Forrer
 */
public final class LabelInfoBuilder {
    private final LabelInfo labelInfo = new LabelInfo();

    public LabelInfo get() {
        return labelInfo;
    }

    public LabelInfoBuilder withApproved(AccountInfo accountInfo) {
        labelInfo.approved = accountInfo;
        return this;
    }
}
