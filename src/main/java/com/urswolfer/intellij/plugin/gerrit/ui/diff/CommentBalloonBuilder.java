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

import com.google.inject.Inject;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class CommentBalloonBuilder {
    private static final String POPUP_TEXT =
        String.format("Hit %s to create a comment. It will be published once you post your review.",
            KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.getShortcuts()));

    @Inject
    private JBPopupFactory jbPopupFactory;

    public JBPopup getNewCommentBalloon(final CommentForm balloonContent, @NotNull final String title) {
        final ComponentPopupBuilder builder = jbPopupFactory.
                createComponentPopupBuilder(balloonContent, balloonContent);
        builder.setAdText(POPUP_TEXT);
        builder.setTitle(title);
        builder.setResizable(true);
        builder.setMovable(true);
        builder.setRequestFocus(true);
        builder.setCancelOnClickOutside(false);
        builder.setCancelOnWindowDeactivation(false);
        return builder.createPopup();
    }
}
