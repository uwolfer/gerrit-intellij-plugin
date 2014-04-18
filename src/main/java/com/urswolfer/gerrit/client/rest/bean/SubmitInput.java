/*
 * Copyright 2013 Urs Wolfer
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

package com.urswolfer.gerrit.client.rest.bean;

import com.google.gson.annotations.SerializedName;

/**
 * @author Urs Wolfer
 */
public class SubmitInput {
    @SerializedName("wait_for_merge")
    private boolean waitForMerge = true;

    public boolean isWaitForMerge() {
        return waitForMerge;
    }

    public void setWaitForMerge(boolean waitForMerge) {
        this.waitForMerge = waitForMerge;
    }
}
