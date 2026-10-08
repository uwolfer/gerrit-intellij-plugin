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

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer;
import com.intellij.diff.util.Side;

/**
 * The editor of a unified diff, which shows the lines of both sides one after the other: a removed line is one of the
 * left side, an added one of the right, and an unchanged one of both. The editor gets its text with the first rediff,
 * and gets it anew with each one.
 */
final class UnifiedLineMapping implements LineMapping {
    private final UnifiedDiffViewer viewer;

    UnifiedLineMapping(UnifiedDiffViewer viewer) {
        this.viewer = viewer;
    }

    @Override
    public int toEditorLine(Side side, int line) {
        return viewer.transferLineToOneside(side, line);
    }

    @Override
    public int toEditorLineStrict(Side side, int line) {
        return viewer.transferLineToOnesideStrict(side, line);
    }

    @Override
    public int toSideLine(Side side, int editorLine) {
        return viewer.transferLineFromOnesideStrict(side, editorLine);
    }

    /**
     * The side the viewer edits, the right one unless only the left one can be; the platform counts an unchanged line
     * to it as well, as the GitHub and GitLab plugins do with their comments.
     */
    @Override
    public Side preferredSide() {
        return viewer.getMasterSide();
    }

    @Override
    public int lineCount(Side side) {
        return viewer.getDocument(side).getLineCount();
    }
}
