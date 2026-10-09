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

import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.util.Range;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.urswolfer.intellij.plugin.gerrit.GerritAccountsListener;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Shows the comments of the changes on HEAD in the editors of the files they change, on the lines the patch set had
 * as the user edits them. The text is compared with the patch set in the background, a while after the last edit, and
 * a comparison which an edit overtakes is dropped.
 *
 * <p>
 * Settled, a review should not raise these again:
 *
 * <ul>
 *   <li>after pushing an amended commit as a new patch set the editor stays on the previous one until the next
 *   refresh: its comments are the ones being addressed;</li>
 *   <li>after HEAD moves, the earlier result shows until the new one is in, so that a commit does not make the
 *   comments blink; replies and edits go to the patch set of their comment, only a new comment waits;</li>
 *   <li>a failed lookup retries with a backoff, through the listeners and so only for files still open; an {@code
 *   Error} is not retried.</li>
 * </ul>
 */
@Service(Service.Level.PROJECT)
public final class EditorComments implements Disposable {
    private static final Logger LOG = Logger.getInstance(EditorComments.class);

    private static final int DIFF_DELAY_MS = 300;
    // the platform gives up on large diffs as well, this spares loading and comparing them in the first place
    private static final int MAX_LENGTH = 5_000_000;
    private static final long RETRY_LOAD_AFTER_FAILURE = TimeUnit.MINUTES.toMillis(1);

    private final Project project;
    private final Map<Editor, Installed> installed = new HashMap<>();

    public EditorComments(Project project) {
        this.project = project;
        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(HeadChanges.Listener.TOPIC, this::update);
        // with other credentials, or another server, there are other comments, or some at all
        connection.subscribe(GerritAccountsListener.TOPIC, () -> ApplicationManager.getApplication()
            .invokeLater(() -> settingChanged(project), project.getDisposed()));
        // the repositories are not known yet while the editors of the last session open
        connection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, (VcsRepositoryMappingListener)
            () -> ApplicationManager.getApplication().invokeLater(this::update, project.getDisposed()));
    }

    public static EditorComments getInstance(Project project) {
        return project.getService(EditorComments.class);
    }

    /** After the settings changed, which are application wide: every open project has to pick them up. */
    public static void settingChanged() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            settingChanged(project);
        }
    }

    /**
     * After the settings or the account of a project changed. The comments are asked for again: with other
     * credentials there may be others, or some at all.
     */
    public static void settingChanged(Project project) {
        HeadChanges.refreshIfCreated(project);
        // created only once wanted, so it may not be there yet
        EditorComments comments = HeadChanges.isWanted(project)
            ? getInstance(project) : project.getServiceIfCreated(EditorComments.class);
        if (comments != null) {
            comments.update();
        }
    }

    static boolean isFileEditor(Editor editor) {
        return editor.getEditorKind() == EditorKind.MAIN_EDITOR && editor instanceof EditorEx;
    }

    void editorCreated(Editor editor) {
        if (HeadChanges.isWanted(project)) {
            update(editor);
        }
    }

    void editorReleased(Editor editor) {
        Installed comments = installed.remove(editor);
        if (comments != null) {
            Disposer.dispose(comments);
        }
    }

    /** Brings every editor of the project in line with the changes on HEAD and the setting. */
    void update() {
        HeadChanges headChanges = project.getServiceIfCreated(HeadChanges.class);
        if (headChanges != null && !HeadChanges.isWanted(project)) {
            headChanges.forget();
        }
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            if (editor.getProject() == project && isFileEditor(editor)) {
                update(editor);
            }
        }
    }

    private void update(Editor editor) {
        HeadChanges.FileOnHead file = null;
        if (HeadChanges.isWanted(project)) {
            VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (virtualFile != null) {
                file = HeadChanges.getInstance(project).find(virtualFile);
            }
        }
        Installed current = installed.get(editor);
        if (current != null && file != null && current.shows(file)) {
            current.setChange(file.change, file.current);
            return;
        }
        if (current != null) {
            installed.remove(editor);
            Disposer.dispose(current);
        }
        // what was found before HEAD moved stays where it is shown, but is not shown anew
        if (file != null && file.current && !editor.isDisposed()) {
            Installed comments = new Installed((EditorEx) editor, file);
            installed.put(editor, comments);
            Disposer.register(this, comments);
            comments.start();
        }
    }

    @Override
    public void dispose() {
        installed.clear(); // disposed as children
    }

    /** The comments of one patch set in one editor. Lives on the event dispatch thread but for the comparison. */
    private final class Installed implements Disposable {
        private final EditorEx editor;
        private final String path;
        private final String revisionId;
        private final LocalLineMapping mapping = new LocalLineMapping();
        private final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        private volatile HeadChanges.HeadChange change;
        private DiffComments comments;
        private volatile boolean disposed;
        private volatile String patchSet;
        // not asked again on every edit, only after a while, or once the changes on HEAD are
        private volatile long loadFailedAt;
        // a patch set this large is not compared at all
        private volatile boolean tooLarge;
        // whether the text could be compared last time, and so a comment can go on it
        private volatile boolean available = true;
        // the change is known to be on HEAD as it is now, see FileOnHead.current
        private boolean current = true;
        private volatile ProgressIndicator comparison;

        Installed(EditorEx editor, HeadChanges.FileOnHead file) {
            this.editor = editor;
            this.path = file.path;
            this.revisionId = file.change.revisionId;
            this.change = file.change;
        }

        boolean shows(HeadChanges.FileOnHead file) {
            return path.equals(file.path) && revisionId.equals(file.change.revisionId)
                && change.change._number == file.change.change._number;
        }

        void start() {
            comments = createComments();
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(@NotNull DocumentEvent event) {
                    mapping.markStale();
                    ProgressIndicator running = comparison;
                    if (running != null) {
                        running.cancel();
                    }
                    compareLater(DIFF_DELAY_MS);
                }
            }, this);
            compareLater(0);
        }

        /**
         * The same patch set as loaded again, with the comments as they are now, which replace those shown: the text
         * compares the same, unless the patch set could not be loaded before.
         */
        void setChange(HeadChanges.HeadChange change, boolean current) {
            if (this.current != current) {
                this.current = current;
                updateAddCommentAction(comments);
            }
            if (this.change == change) return;
            this.change = change;
            if (patchSet == null) {
                loadFailedAt = 0;
                compareLater(0);
            }
            comments.replaceAll(commentsOf(change), revision());
        }

        private CommentSide revision() {
            return CommentSide.onRevision(path, revisionId);
        }

        private List<CommentInfo> commentsOf(HeadChanges.HeadChange change) {
            List<CommentInfo> fileComments = change.comments.getOrDefault(path, Collections.emptyList());
            synchronized (fileComments) {
                return fileComments.stream().filter(revision()::shows).collect(Collectors.toList());
            }
        }

        private DiffComments createComments() {
            CommentSide revision = revision();
            // the editor shows the patch set only, this side never has comments
            CommentSide parent = CommentSide.onParent(path, revisionId, null);
            DiffComments diffComments = new DiffComments(project, change.change, parent, revision,
                () -> disposed || editor.isDisposed());
            diffComments.addEditor(editor, mapping);
            updateAddCommentAction(diffComments);
            diffComments.addAll(commentsOf(change), revision);
            return diffComments;
        }

        /** Only where a comment can go: the text compares, and the change is known to be on HEAD now. */
        private void updateAddCommentAction(DiffComments diffComments) {
            // no shortcut of its own, as in a diff: a bare letter in the editor of the file is typing
            editor.putUserData(GerritCommentsDiffExtension.ADD_COMMENT_ACTION, !available || !current ? null
                : diffComments.getAddCommentActionBuilder()
                    .create(diffComments, editor)
                    .withText(GerritBundle.message("diff.addComment"))
                    .withIcon(AllIcons.Toolwindows.ToolWindowMessages)
                    .get());
        }

        private void compareLater(int delay) {
            if (disposed) return;
            alarm.cancelAllRequests();
            alarm.addRequest(this::compare, delay);
        }

        /** On the pooled thread of the alarm, which runs one at a time. */
        private void compare() {
            if (disposed || tooLarge || project.isDisposed()) return;
            Document document = editor.getDocument();
            String base = patchSet;
            if (base == null) {
                if (ApplicationManager.getApplication().runReadAction((Computable<Integer>) document::getTextLength)
                    > MAX_LENGTH) {
                    unavailable("the file is too large to compare"); // not worth loading the patch set for
                    return;
                }
                if (System.currentTimeMillis() - loadFailedAt < RETRY_LOAD_AFTER_FAILURE) return;
                VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                if (file == null) {
                    unavailable("it is no file");
                    return;
                }
                base = HeadChanges.getInstance(project).loadContent(change, path, file.getCharset());
                if (base == null) {
                    loadFailedAt = System.currentTimeMillis(); // an edit after a while, or a refresh, tries again
                    unavailable("the patch set could not be loaded");
                    return;
                }
                patchSet = base;
            }
            if (base.length() > MAX_LENGTH) {
                tooLarge();
                return;
            }
            // published before the text is read, so that an edit from then on cancels it
            ProgressIndicator indicator = new EmptyProgressIndicator();
            comparison = indicator;
            // after the load, which may have taken a while: the text as it is now
            CharSequence[] text = new CharSequence[1];
            long stamp = ApplicationManager.getApplication().runReadAction((Computable<Long>) () -> {
                text[0] = document.getImmutableCharSequence();
                return document.getModificationStamp();
            });
            if (text[0].length() > MAX_LENGTH) {
                unavailable("the file is too large to compare");
                return;
            }

            List<Range> ranges;
            try {
                ranges = LocalLineMapping.compare(base, text[0], indicator);
            } catch (DiffTooBigException e) { // a ProcessCanceledException as well
                unavailable("the file differs too much from the patch set to compare");
                return;
            } catch (ProcessCanceledException e) {
                return; // an edit overtook it, and compares again
            }
            int lineCount = StringUtil.countNewLines(base) + 1;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposed || editor.isDisposed()) return;
                if (document.getModificationStamp() != stamp) {
                    // mostly an edit, which compares again anyway; but saving an undone edit sets the stamp quietly
                    compareLater(DIFF_DELAY_MS);
                    return;
                }
                mapping.set(ranges, lineCount);
                comments.relayout();
                if (!available) {
                    available = true;
                    updateAddCommentAction(comments);
                }
            }, project.getDisposed());
        }

        /**
         * No comment can go on the file: its menu item would only ever answer that the comparison is still running.
         * On a pooled thread.
         */
        private void unavailable(String why) {
            if (!available) return; // said so already
            available = false;
            LOG.info("No comment on " + path + " for now, " + why);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!disposed && !editor.isDisposed()) {
                    editor.putUserData(GerritCommentsDiffExtension.ADD_COMMENT_ACTION, null);
                    // rather none than each on a line which has nothing to do with it any more
                    mapping.markUnavailable();
                    comments.relayout();
                }
            }, project.getDisposed());
        }

        private void tooLarge() {
            tooLarge = true;
            unavailable("the file is too large to compare");
        }

        @Override
        public void dispose() {
            disposed = true;
            mapping.markStale(); // for good: a comment form still open must not go by a comparison no longer kept up
            ProgressIndicator running = comparison;
            if (running != null) {
                running.cancel();
            }
            if (comments != null && !editor.isDisposed()) {
                comments.removeEditor(editor);
                editor.putUserData(GerritCommentsDiffExtension.ADD_COMMENT_ACTION, null);
            }
        }
    }

    /** Hands the editors of all projects to the project they belong to. */
    public static final class Installer implements EditorFactoryListener {
        @Override
        public void editorCreated(@NotNull EditorFactoryEvent event) {
            Editor editor = event.getEditor();
            Project project = editor.getProject();
            // created whether wanted or not: while the editors of the last session open, the repositories which
            // decide the account of the project may not be known yet, and the service hears when they are
            if (project != null && !project.isDisposed() && isFileEditor(editor)) {
                getInstance(project).editorCreated(editor);
            }
        }

        @Override
        public void editorReleased(@NotNull EditorFactoryEvent event) {
            Editor editor = event.getEditor();
            Project project = editor.getProject();
            // a closing project disposes the comments along with itself
            if (project != null && !project.isDisposed() && isFileEditor(editor)) {
                EditorComments comments = project.getServiceIfCreated(EditorComments.class);
                if (comments != null) {
                    comments.editorReleased(editor);
                }
            }
        }
    }
}
