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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Urs Wolfer
 */
public class CommentGutterIconRenderer extends GutterIconRenderer {
    private final CommentInfo myFileComment;

    public CommentGutterIconRenderer(CommentInfo fileComment) {
        myFileComment = fileComment;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return AllIcons.Toolwindows.ToolWindowMessages;
    }

    @Override
    public boolean equals(Object obj) {
        return myFileComment.equals(obj);
    }

    @Override
    public int hashCode() {
        return myFileComment.hashCode();
    }

    @Nullable
    @Override
    public String getTooltipText() {
        return String.format("<strong>%s</strong><br/>%s", myFileComment.getAuthor().getName(), myFileComment.getMessage());
    }
}
