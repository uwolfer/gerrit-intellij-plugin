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

import com.google.common.base.Optional;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.external.FrameDiffTool;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentable;
import com.intellij.ui.PopupHandler;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritApiUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.util.GerritDataKeys;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class CommentsDiffTool extends FrameDiffTool {

    @Override
    public boolean canShow(DiffRequest request) {
        final boolean superCanShow = super.canShow(request);

        final AsyncResult<DataContext> dataContextFromFocus = DataManager.getInstance().getDataContextFromFocus();
        final DataContext context = dataContextFromFocus.getResult();
        if (context == null) return false;
        final ChangeInfo changeInfo = GerritDataKeys.CHANGE.getData(context);
        return superCanShow && changeInfo != null;
    }

    @Nullable
    @Override
    protected DiffPanelImpl createDiffPanelImpl(@NotNull DiffRequest request, @Nullable Window window, @NotNull Disposable parentDisposable) {
        DiffPanelImpl diffPanel = new CommentableDiffPanel(window, request);
        diffPanel.setDiffRequest(request);
        Disposer.register(parentDisposable, diffPanel);
        return diffPanel;
    }

    private void handleComments(DiffPanelImpl diffPanel, String filePathString) {
        final AsyncResult<DataContext> dataContextFromFocus = DataManager.getInstance().getDataContextFromFocus();
        final DataContext context = dataContextFromFocus.getResult();
        if (context == null) return;

        final Editor editor2 = diffPanel.getEditor2();

        ChangeInfo myChangeInfo = GerritDataKeys.CHANGE.getData(context);
        Project project = PlatformDataKeys.PROJECT.getData(context);
        final GerritSettings settings = GerritSettings.getInstance();
        Optional<ChangeInfo> changeDetailsOptional = GerritUtil.getChangeDetails(GerritApiUtil.getApiUrl(),
                settings.getLogin(), settings.getPassword(), myChangeInfo.getNumber(), project);
        if (!changeDetailsOptional.isPresent()) return;
        ChangeInfo changeDetails = changeDetailsOptional.get();

        FilePath filePath = new FilePathImpl(new File(filePathString), false); // PlatformDataKeys.VIRTUAL_FILE.getData(context) returns null im some cases

        ReviewCommentSink reviewCommentSink = GerritDataKeys.REVIEW_COMMENT_SINK.getData(context);
        addCommentAction(editor2, filePath, reviewCommentSink, myChangeInfo);

        TreeMap<String,List<CommentInfo>> comments = GerritUtil.getComments(GerritApiUtil.getApiUrl(),
                settings.getLogin(), settings.getPassword(), changeDetails.getId(), changeDetails.getCurrentRevision(), project);
        addCommentsGutter(editor2, filePath, comments, reviewCommentSink, myChangeInfo, project);
    }

    private void addCommentAction(@Nullable final Editor editor2,
                                  @Nullable final FilePath filePath,
                                  ReviewCommentSink reviewCommentSink,
                                  ChangeInfo changeInfo) {
        if (editor2 != null) {
            DefaultActionGroup group = new DefaultActionGroup();
            final AddCommentAction addCommentAction = new AddCommentAction(reviewCommentSink, changeInfo, editor2, filePath);
            addCommentAction.setContextComponent(editor2.getComponent());
            group.add(addCommentAction);
            PopupHandler.installUnknownPopupHandler(editor2.getContentComponent(), group, ActionManager.getInstance());
        }
    }

    private void addCommentsGutter(Editor editor2, FilePath filePath, TreeMap<String, List<CommentInfo>> comments, ReviewCommentSink reviewCommentSink, ChangeInfo changeInfo, Project project) {
        List<CommentInfo> fileComments = Collections.emptyList();
        Optional<GitRepository> gitRepositoryOptional = GerritGitUtil.getRepositoryForGerritProject(project, changeInfo.getProject());
        if (!gitRepositoryOptional.isPresent()) return;
        GitRepository repository = gitRepositoryOptional.get();
        for (Map.Entry<String, List<CommentInfo>> entry : comments.entrySet()) {
            String path = repository.getRoot().getPath();
            if (filePath.getPath().equals(path + File.separator + entry.getKey())) {
                fileComments = entry.getValue();
                break;
            }
        }

        List< CommentInput > commentInputsFromSink = reviewCommentSink.getCommentsForChange(changeInfo.getId());
        for (CommentInput commentInput : commentInputsFromSink) {
            fileComments.add(commentInput.toCommentInfo());
        }

        final MarkupModel markup = editor2.getMarkupModel();
        for (CommentInfo fileComment : fileComments) {
            int line = fileComment.getLine() - 1;
            if (line < 0) {
                line = 0;
            }
            final RangeHighlighter highlighter = markup.addLineHighlighter(line, HighlighterLayer.ERROR + 1, null);
            highlighter.setGutterIconRenderer(new CommentGutterIconRenderer(fileComment, reviewCommentSink, changeInfo, highlighter, markup));
        }
    }

    private class CommentableDiffPanel extends DiffPanelImpl {
        public CommentableDiffPanel(Window window, DiffRequest request) {
            super(window, request.getProject(), true, true, DiffManagerImpl.FULL_DIFF_DIVIDER_POLYGONS_OFFSET, CommentsDiffTool.this);
        }

        @Override
        public void setDiffRequest(DiffRequest request) {
            super.setDiffRequest(request);

            Object chain = request.getGenericData().get(VcsDataKeys.DIFF_REQUEST_CHAIN.getName());
            if (chain instanceof ChangeRequestChain) {
                DiffRequestPresentable currentRequest = ((ChangeRequestChain) chain).getCurrentRequest();
                if (currentRequest != null) {
                    String path = currentRequest.getPathPresentation();
                    handleComments(this, path);
                }
            }
        }
    }
}
