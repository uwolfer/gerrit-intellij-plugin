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

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffExtension;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer;
import com.intellij.diff.tools.util.base.DiffViewerBase;
import com.intellij.diff.tools.util.base.DiffViewerListener;
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.ui.PopupHandler;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewedFilesService;
import com.urswolfer.intellij.plugin.gerrit.ui.action.ToggleReviewedAction;
import com.urswolfer.intellij.plugin.gerrit.util.GerritUserDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shows the comments of a Gerrit change in the diff viewers of its files, whichever of them the user picked.
 *
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class GerritCommentsDiffExtension extends DiffExtension {
    static final Key<AddCommentAction> ADD_COMMENT_ACTION = Key.create("gerrit.AddCommentAction");

    private static final String ADD_COMMENT_ACTION_ID = "Gerrit.AddComment";

    private static final Shortcut[] DEFAULT_ADD_COMMENT_SHORTCUTS = CustomShortcutSet.fromString("C").getShortcuts();

    /**
     * A bare "C" bound to the editor component competes with typing and gets swallowed by the diff
     * viewer in newer IDE versions (issue #410), so it only serves as the default. Once the user has
     * picked a shortcut for {@code Gerrit.AddComment}, the keymap dispatches it and a second binding
     * on a bare letter would just bring the conflict back. Resolved per keystroke, so that a keymap
     * change also reaches the diffs which are open already.
     */
    private static final ShortcutSet ADD_COMMENT_SHORTCUT_SET = () ->
        KeymapUtil.getActiveKeymapShortcuts(ADD_COMMENT_ACTION_ID).getShortcuts().length > 0
            ? Shortcut.EMPTY_ARRAY
            : DEFAULT_ADD_COMMENT_SHORTCUTS;

    // descending, as icons are added to the left of existing icons
    private static final Comparator<Comment> COMMENT_ORDERING =
        Comparator.comparingLong((Comment comment) -> comment.updated.getTime()).reversed();

    @Override
    public void onViewerCreated(@NotNull FrameDiffTool.DiffViewer viewer,
                                @NotNull DiffContext context,
                                @NotNull DiffRequest request) {
        Project project = context.getProject();
        ChangeInfo changeInfo = context.getUserData(GerritUserDataKeys.CHANGE);
        String selectedRevisionId = context.getUserData(GerritUserDataKeys.REVISION);
        Optional<Pair<String, RevisionInfo>> baseRevision = context.getUserData(GerritUserDataKeys.BASE_REVISION);
        Integer baseParent = context.getUserData(GerritUserDataKeys.BASE_PARENT);
        Change change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY);
        if (project == null || changeInfo == null || selectedRevisionId == null || baseRevision == null
            || change == null || !(viewer instanceof DiffViewerBase)) {
            return;
        }

        FilePath filePath = ChangesUtil.getFilePath(change);
        String relativeFilePath = getRelativeOrAbsolutePath(project, filePath.getPath(), changeInfo);
        // a file renamed since the base patch set has its old name there, and so have its comments; the parent side
        // of a patch set is not like that, Gerrit files its comments under the name in the patch set
        FilePath beforePath = ChangesUtil.getBeforePath(change);
        String baseFilePath = baseRevision.isPresent() && beforePath != null
            ? getRelativeOrAbsolutePath(project, beforePath.getPath(), changeInfo)
            : relativeFilePath;
        CommentSide revisionSide = CommentSide.onRevision(relativeFilePath, selectedRevisionId);
        CommentSide baseSide = baseRevision.isPresent()
            ? CommentSide.onRevision(baseFilePath, baseRevision.get().getFirst())
            : CommentSide.onParent(baseFilePath, selectedRevisionId, baseParent);

        DiffViewerBase viewerBase = (DiffViewerBase) viewer;
        DiffComments comments = new DiffComments(project, changeInfo, baseSide, revisionSide, viewerBase::isDisposed);
        TwosideTextDiffViewer twosideViewer = null;
        if (viewer instanceof TwosideTextDiffViewer) {
            twosideViewer = (TwosideTextDiffViewer) viewer;
            for (Side side : Side.values()) {
                EditorEx editor = twosideViewer.getEditor(side);
                if (editor != null) {
                    comments.addEditor(editor, new EditorLineMapping(side, editor.getDocument(), false));
                }
            }
        } else if (viewer instanceof OnesideTextDiffViewer) {
            // the one side of a deleted file is its old content, which comments on the base belong to
            OnesideTextDiffViewer onesideViewer = (OnesideTextDiffViewer) viewer;
            EditorEx editor = onesideViewer.getEditor();
            comments.addEditor(editor, new EditorLineMapping(onesideViewer.getSide(), editor.getDocument(), true));
        } else if (viewer instanceof UnifiedDiffViewer) {
            UnifiedDiffViewer unifiedViewer = (UnifiedDiffViewer) viewer;
            comments.addEditor(unifiedViewer.getEditor(), new UnifiedLineMapping(unifiedViewer));
        } else {
            return;
        }
        CommentNavigator navigator = new CommentNavigator(comments, twosideViewer);
        for (EditorEx editor : comments.getEditors()) {
            navigator.addEditor(editor, viewerBase);
            editor.putUserData(ToggleReviewedAction.TARGET,
                new ToggleReviewedAction.Target(changeInfo, selectedRevisionId, relativeFilePath));
            addActions(comments, editor);
        }

        viewerBase.addListener(new DiffViewerListener() {
            @Override
            protected void onAfterRediff() {
                comments.show();
            }
        });
        if (!(viewer instanceof UnifiedDiffViewer)) { // its editor is empty until the first rediff
            comments.show();
        }

        loadComments(comments, project, changeInfo, selectedRevisionId, baseRevision.isPresent());
        // as Gerrit's web UI does for a file opened; "Mark as Not Reviewed" takes it back until the diff is opened again
        ReviewedFilesService.getInstance(project).setReviewed(changeInfo, selectedRevisionId, List.of(relativeFilePath), true);
    }

    private void loadComments(DiffComments comments, Project project, ChangeInfo changeInfo,
                              String selectedRevisionId, boolean againstBaseRevision) {
        CommentSide revisionSide = comments.getSide(Side.RIGHT);
        CommentSide baseSide = comments.getSide(Side.LEFT);
        GerritUtil gerritUtil = GerritUtil.getInstance();
        gerritUtil.getComments(changeInfo._number, selectedRevisionId, project, true, true,
            (Map<String, List<CommentInfo>> fileComments) -> {
                List<CommentInfo> revisionComments = fileComments.get(revisionSide.filePath);
                if (revisionComments != null) {
                    comments.addAll(filter(revisionComments, revisionSide), revisionSide);
                    if (!againstBaseRevision) {
                        comments.addAll(filter(revisionComments, baseSide), baseSide);
                    }
                }
            });

        if (againstBaseRevision) {
            gerritUtil.getComments(changeInfo._number, baseSide.revisionId, project, true, true,
                (Map<String, List<CommentInfo>> fileComments) -> {
                    List<CommentInfo> baseComments = fileComments.get(baseSide.filePath);
                    if (baseComments != null) {
                        baseComments.sort(COMMENT_ORDERING);
                        comments.addAll(filter(baseComments, baseSide), baseSide);
                    }
                });
        }
    }

    private void addActions(DiffComments comments, EditorEx editor) {
        DefaultActionGroup group = new DefaultActionGroup();
        AddCommentAction addCommentAction = comments.getAddCommentActionBuilder()
                .create(comments, editor)
                .withText(GerritBundle.message("diff.addComment"))
                .withIcon(AllIcons.Toolwindows.ToolWindowMessages)
                .get();
        editor.putUserData(ADD_COMMENT_ACTION, addCommentAction);
        addCommentAction.registerCustomShortcutSet(ADD_COMMENT_SHORTCUT_SET, editor.getContentComponent());
        group.add(addCommentAction);

        // on the editor as well, so that they come before Previous and Next Occurrence, whose shortcuts they have by
        // default, and which step through the results of the last search from any editor; with the shortcut sets of
        // the registered actions, which follow the keymap, also once it changes
        AnAction previous = ActionManager.getInstance().getAction(GoToCommentAction.PREVIOUS_ID);
        AnAction next = ActionManager.getInstance().getAction(GoToCommentAction.NEXT_ID);
        new GoToCommentAction.Previous().registerCustomShortcutSet(previous.getShortcutSet(),
            editor.getContentComponent());
        new GoToCommentAction.Next().registerCustomShortcutSet(next.getShortcutSet(), editor.getContentComponent());
        group.add(previous);
        group.add(next);
        group.add(ActionManager.getInstance().getAction(ToggleReviewedAction.ID));
        PopupHandler.installPopupHandler(editor.getContentComponent(), group, "GerritCommentDiffPopup");
    }

    private static List<CommentInfo> filter(List<CommentInfo> comments, CommentSide side) {
        return comments.stream().filter(side::shows).collect(Collectors.toList());
    }

    private static String getRelativeOrAbsolutePath(Project project, String absoluteFilePath, ChangeInfo changeInfo) {
        return PathUtils.ensureSlashSeparators(PathUtils.getRelativeOrAbsolutePath(project, absoluteFilePath, changeInfo));
    }
}
