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

import com.google.gerrit.extensions.api.projects.ProjectState;
import com.google.gerrit.extensions.common.ProjectInfo;

/**
 * @author Thomas Forrer
 */
public final class ProjectInfoBuilder {
    private final ProjectInfo projectInfo = new ProjectInfo();

    public ProjectInfo get() {
        return projectInfo;
    }

    public ProjectInfoBuilder withId(String id) {
        projectInfo.id = id;
        return this;
    }

    public ProjectInfoBuilder withName(String name) {
        projectInfo.name = name;
        return this;
    }

    public ProjectInfoBuilder withParent(String parent) {
        projectInfo.parent = parent;
        return this;
    }

    public ProjectInfoBuilder withDescription(String description) {
        projectInfo.description = description;
        return this;
    }

    public ProjectInfoBuilder withState(ProjectState state) {
        projectInfo.state = state;
        return this;
    }
}
