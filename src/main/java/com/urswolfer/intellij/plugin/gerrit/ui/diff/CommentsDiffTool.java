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

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.external.FrameDiffTool;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInfo;
import com.urswolfer.intellij.plugin.gerrit.util.GerritDataKeys;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Urs Wolfer
 */
public class CommentsDiffTool extends CustomizableFrameDiffTool {

    @Override
    public boolean canShow(DiffRequest request) {
        final boolean superCanShow = super.canShow(request);

        final AsyncResult<DataContext> dataContextFromFocus = DataManager.getInstance().getDataContextFromFocus();
        final DataContext context = dataContextFromFocus.getResult();
        if (context == null) return false;
        final ChangeInfo changeInfo = GerritDataKeys.CHANGE.getData(context);
        return superCanShow && changeInfo != null;
    }

    @Override
    protected DiffPanel createDiffPanel(DiffRequest data, Window window, @NotNull Disposable parentDisposable, FrameDiffTool tool) {
        DiffPanelImpl diffPanel = (DiffPanelImpl) super.createDiffPanel(data, window, parentDisposable, tool);
        handleComments(diffPanel);
        return diffPanel;
    }

    private void handleComments(DiffPanelImpl diffPanel) {
        final AsyncResult<DataContext> dataContextFromFocus = DataManager.getInstance().getDataContextFromFocus();
        final DataContext context = dataContextFromFocus.getResult();
        if (context == null) return;

        final Editor editor2 = diffPanel.getEditor2();

        ChangeInfo myChangeInfo = GerritDataKeys.CHANGE.getData(context);
        final GerritSettings settings = GerritSettings.getInstance();
        ChangeInfo changeDetails = GerritUtil.getChangeDetails(GerritApiUtil.getApiUrl(), settings.getLogin(), settings.getPassword(), myChangeInfo.getNumber());

        final VirtualFile vFile = PlatformDataKeys.VIRTUAL_FILE.getData(context);
        FilePath filePath = new FilePathImpl(new File(vFile.getCanonicalPath()), false);

        TreeMap<String,List<CommentInfo>> comments = GerritUtil.getComments(GerritApiUtil.getApiUrl(),
                settings.getLogin(), settings.getPassword(), changeDetails.getId(), changeDetails.getCurrentRevision());
        addCommentsGutter(editor2, filePath, comments);
    }

    private void addCommentsGutter(Editor editor2, FilePath filePath, TreeMap<String, List<CommentInfo>> comments) {
        List<CommentInfo> fileComments = Collections.emptyList();
        for (Map.Entry<String, List<CommentInfo>> entry : comments.entrySet()) {
            if (filePath.getName().endsWith(entry.getKey())) {
                fileComments = entry.getValue();
                break;
            }
        }

        final MarkupModel markup = editor2.getMarkupModel();
        for (CommentInfo fileComment : fileComments) {
            final RangeHighlighter highlighter = markup.addLineHighlighter(fileComment.getLine() - 1, HighlighterLayer.ERROR + 1, null);
            highlighter.setGutterIconRenderer(new CommentGutterIconRenderer(fileComment));
        }
    }
}
