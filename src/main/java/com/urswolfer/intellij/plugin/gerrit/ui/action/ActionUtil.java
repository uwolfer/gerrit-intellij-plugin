/*
 * Copyright 2013-2016 Urs Wolfer
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

package com.urswolfer.intellij.plugin.gerrit.ui.action;

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.ui.table.TableView;

import java.awt.*;

/**
 * @author Urs Wolfer
 */
public class ActionUtil {

    private ActionUtil() {}

    public static Optional<ChangeInfo> getSelectedChange(AnActionEvent anActionEvent) {
        Component component = anActionEvent.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        if (!(component instanceof TableView)) {
            return Optional.absent();
        }
        final TableView table = (TableView) component;
        Object selectedObject = table.getSelectedObject();
        if (!(selectedObject instanceof ChangeInfo)) {
            return Optional.absent();
        }
        final ChangeInfo selectedChange = (ChangeInfo) selectedObject;
        return Optional.fromNullable(selectedChange);
    }
}
