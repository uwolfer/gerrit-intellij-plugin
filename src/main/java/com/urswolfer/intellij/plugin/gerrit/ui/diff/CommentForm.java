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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.spellchecker.ui.SpellCheckingEditorCustomization;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import com.urswolfer.intellij.plugin.gerrit.ReviewCommentSink;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.ChangeInfo;
import com.urswolfer.intellij.plugin.gerrit.rest.bean.CommentInput;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Set;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class CommentForm extends JPanel {
    private static final int ourBalloonWidth = 350;
    private static final int ourBalloonHeight = 200;

    private final EditorTextField reviewTextField;
    private JBPopup balloon;

    private Editor editor;
    @Nullable
    private FilePath filePath;
    private CommentInput commentInput;

    public CommentForm(@NotNull final Project project,
                       @Nullable FilePath filePath,
                       final ReviewCommentSink reviewCommentSink,
                       final ChangeInfo changeInfo,
                       final GerritGitUtil gerritGitUtil) {
        super(new BorderLayout());
        this.filePath = filePath;

        final EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
        final Set<EditorCustomization> editorFeatures = ContainerUtil.newHashSet();
        editorFeatures.add(SoftWrapsEditorCustomization.ENABLED);
        editorFeatures.add(SpellCheckingEditorCustomization.ENABLED);
        reviewTextField = service.getEditorField(PlainTextLanguage.INSTANCE, project, editorFeatures);

        final JScrollPane pane = ScrollPaneFactory.createScrollPane(reviewTextField);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(pane);

        reviewTextField.setPreferredSize(new Dimension(ourBalloonWidth, ourBalloonHeight));

        reviewTextField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).
                put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "postComment");
        reviewTextField.getActionMap().put("postComment", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CommentInput comment = new CommentInput();

                Optional<GitRepository> gitRepositoryOptional = gerritGitUtil.getRepositoryForGerritProject(project, changeInfo.getProject());
                if (!gitRepositoryOptional.isPresent()) return;
                GitRepository gitRepository = gitRepositoryOptional.get();
                VirtualFile root = gitRepository.getRoot();
                String path = CommentForm.this.filePath.getPath();
                String relativePath = FileUtil.getRelativePath(new File(root.getPath()), new File(path));

                comment.setPath(relativePath);
                comment.setLine(editor.getDocument().getLineNumber(editor.getCaretModel().getOffset()) + 1);
                comment.setMessage(getText());
                reviewCommentSink.addComment(changeInfo.getId(), comment);

                commentInput = comment;
                balloon.dispose();
            }
        });
    }

    public void requestFocus() {
        IdeFocusManager.findInstanceByComponent(reviewTextField).requestFocus(reviewTextField, true);
    }

    @NotNull
    public String getText() {
        return reviewTextField.getText();
    }

    public void setBalloon(@NotNull final JBPopup balloon) {
        this.balloon = balloon;
    }

    public void setEditor(@NotNull final Editor editor) {
        this.editor = editor;
    }

    public CommentInput getComment() {
        return commentInput;
    }
}
