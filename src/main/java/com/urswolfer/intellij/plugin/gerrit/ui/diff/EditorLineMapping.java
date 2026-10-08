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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Document;

/**
 * An editor which shows the text of one side as it is.
 */
final class EditorLineMapping implements LineMapping {
    private final Side side;
    private final Document document;
    private final boolean showsOtherSide;

    /**
     * @param showsOtherSide the comments of the other side show here as well, as the diff of an added or a deleted
     *                       file has one side only: without that, they could neither be seen nor answered, such as a
     *                       draft an older version has saved on that side
     */
    EditorLineMapping(Side side, Document document, boolean showsOtherSide) {
        this.side = side;
        this.document = document;
        this.showsOtherSide = showsOtherSide;
    }

    @Override
    public int toEditorLine(Side side, int line) {
        return toEditorLineStrict(side, line);
    }

    @Override
    public int toEditorLineStrict(Side side, int line) {
        return side == this.side || showsOtherSide ? line : -1;
    }

    @Override
    public int toSideLine(Side side, int editorLine) {
        return side == this.side ? editorLine : -1;
    }

    @Override
    public Side preferredSide() {
        return side;
    }

    @Override
    public int lineCount(Side side) {
        return document.getLineCount();
    }
}
