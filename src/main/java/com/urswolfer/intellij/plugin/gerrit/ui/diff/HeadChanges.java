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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.urswolfer.intellij.plugin.gerrit.GerritAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectSettings;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.GerritChangesListener;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The open Gerrit changes which HEAD carries in each repository of the project: the commits on HEAD which its upstream
 * does not have, as checking out a change, or a stack of them, leaves it. A commit is the change whose patch set it
 * is, or else, as after amending it locally, the current patch set of the change its Change-Id names.
 *
 * Gerrit is asked in the background when HEAD or its upstream moves and on a refresh, never on the event dispatch
 * thread and never for each editor. A failure is only logged: nobody asked for this, so nobody is to be bothered.
 */
@Service(Service.Level.PROJECT)
public final class HeadChanges implements Disposable {
    private static final Logger LOG = Logger.getInstance(HeadChanges.class);

    public interface Listener {
        Topic<Listener> TOPIC = Topic.create("Gerrit changes on HEAD", Listener.class);

        /** On the event dispatch thread. */
        void headChangesChanged();
    }

    // a stack deeper than this is more likely a branch which tracks something unexpected
    private static final int MAX_STACK = 10;
    private static final long RETRY_AFTER_FAILURE = TimeUnit.MINUTES.toMillis(1);
    // doubled with each failure in a row, so that an unreachable Gerrit is not asked every minute all day
    private static final long MAX_RETRY_AFTER_FAILURE = TimeUnit.MINUTES.toMillis(30);
    private static final Pattern CHANGE_ID =
        Pattern.compile("^Change-Id:[ \\t]*(I[0-9a-f]{40})[ \\t]*$", Pattern.MULTILINE);

    private final Project project;
    // one at a time, so that a burst of repository events cannot fan out into parallel requests
    private final ExecutorService executor =
        AppExecutorUtil.createBoundedApplicationPoolExecutor("Gerrit changes on HEAD", 1);
    private final Map<GitRepository, State> states = new ConcurrentHashMap<>();
    // the repositories an editor asked about, which follow the moves of HEAD; the others may never have a file open
    private final Set<GitRepository> watched = ConcurrentHashMap.newKeySet();
    // with the project, so that a retry half an hour away does not keep a closed one
    private final Alarm retries = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    // the lookup asked for next, one per repository
    private final Map<GitRepository, Request> pending = new ConcurrentHashMap<>();
    // the one under way, until its answer is in; by the request, not its key, as a refresh for the same key may be
    // under way next, and is not this one's to end
    private final Map<GitRepository, Request> running = new ConcurrentHashMap<>();
    // when the user last saved or removed a comment of a change, which an answer asked for before does not have
    private final AtomicInteger edits = new AtomicInteger();
    private final Map<Integer, Integer> lastEditOf = new ConcurrentHashMap<>();

    public HeadChanges(Project project) {
        this.project = project;
        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, repository -> {
            if (watched.contains(repository)) {
                update(repository, false);
            }
        });
        // such as a review, which publishes the drafts
        connection.subscribe(GerritChangesListener.TOPIC, new GerritChangesListener() {
            @Override
            public void changesModified() {
                if (isWanted(project)) {
                    refresh();
                }
            }

            @Override
            public void changeModified(String changeId, Consumer<ChangeInfo> update) {
            }
        });
        connection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, (VcsRepositoryMappingListener)
            () -> {
                List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
                states.keySet().retainAll(repositories);
                watched.retainAll(repositories);
            });
    }

    public static HeadChanges getInstance(Project project) {
        return project.getService(HeadChanges.class);
    }

    /** A file a change on HEAD changes. */
    static final class FileOnHead {
        final HeadChange change;
        /** Relative to the root of the repository, as Gerrit names it. */
        final String path;

        /**
         * Whether the change is known to be on HEAD as it is now; until the lookup for HEAD is in, it is the one
         * found before, which takes no new comment, as HEAD may be another change by now.
         */
        final boolean current;

        FileOnHead(HeadChange change, String path, boolean current) {
            this.change = change;
            this.path = path;
            this.current = current;
        }
    }

    static final class HeadChange {
        final ChangeInfo change;
        final String revisionId;
        final Set<String> files;
        /** By file, with the drafts of the user; updated when the user saves or deletes one. */
        final Map<String, List<CommentInfo>> comments;
        /** Where the patch set is a commit on HEAD, which git has without asking Gerrit; null if it is not. */
        @Nullable
        private final GitRepository local;
        private final Map<String, String> contents;

        HeadChange(ChangeInfo change, String revisionId, @Nullable GitRepository local, Set<String> files,
                   Map<String, List<CommentInfo>> comments, Map<String, String> contents) {
            this.change = change;
            this.revisionId = revisionId;
            this.local = local;
            this.files = files;
            this.contents = contents;
            this.comments = new ConcurrentHashMap<>();
            comments.forEach((path, list) ->
                this.comments.put(path, Collections.synchronizedList(new ArrayList<>(list))));
        }
    }

    private static final class State {
        final String key;
        final List<HeadChange> changes;
        final long failedAt;
        final int failures;
        /** The changes of an earlier HEAD on the same branch, kept as the lookup for this one failed. */
        final boolean provisional;

        State(String key, List<HeadChange> changes, long failedAt, int failures, boolean provisional) {
            this.key = key;
            this.changes = changes;
            this.failedAt = failedAt;
            this.failures = failures;
            this.provisional = provisional;
        }

        long retryAfter() {
            return Math.min(RETRY_AFTER_FAILURE << Math.max(0, Math.min(failures - 1, 10)), MAX_RETRY_AFTER_FAILURE);
        }
    }

    /**
     * The change nearest to HEAD which changes the file. Null if there is none, or if it is not known yet, which it
     * then asks Gerrit for; the listeners hear when the answer is in. Until then, after HEAD moved, it is what was
     * there before: a commit or an amend mostly keeps the change, and the comments should not blink.
     */
    @Nullable
    FileOnHead find(VirtualFile file) {
        GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(file);
        if (repository == null) return null;
        String path = VfsUtilCore.getRelativePath(file, repository.getRoot());
        if (path == null) return null;
        watched.add(repository);

        update(repository, false);
        State state = states.get(repository);
        if (state == null || !state.key.startsWith(reusableKey(repository))) return null;
        boolean current = state.key.equals(keyOf(repository)) && !state.provisional;
        for (HeadChange change : state.changes) {
            if (change.files.contains(path)) {
                return new FileOnHead(change, path, current);
            }
        }
        return null;
    }

    /** A comment the user saved just now, which replaces the one with its id, as an edited draft does. */
    void saved(int changeNr, CommentSide side, CommentInfo comment) {
        lastEditOf.put(changeNr, edits.incrementAndGet());
        List<CommentInfo> comments = commentsOf(changeNr, side);
        if (comments == null) return;
        synchronized (comments) {
            comments.removeIf(existing -> existing.id != null && existing.id.equals(comment.id));
            comments.add(comment);
        }
    }

    void removed(int changeNr, CommentSide side, String commentId) {
        lastEditOf.put(changeNr, edits.incrementAndGet());
        List<CommentInfo> comments = commentsOf(changeNr, side);
        if (comments == null) return;
        comments.removeIf(existing -> commentId.equals(existing.id));
    }

    @Nullable
    private List<CommentInfo> commentsOf(int changeNr, CommentSide side) {
        for (State state : states.values()) {
            for (HeadChange change : state.changes) {
                if (change.change._number == changeNr && change.revisionId.equals(side.revisionId)) {
                    return change.comments.computeIfAbsent(side.filePath,
                        path -> Collections.synchronizedList(new ArrayList<>()));
                }
            }
        }
        return null;
    }

    /**
     * After the comments changed on Gerrit other than through the editor, such as drafts a review published, or
     * maybe, as reviewers comment too. Nothing to do while no editor has asked for comments.
     */
    public static void refreshIfCreated(Project project) {
        HeadChanges headChanges = project.getServiceIfCreated(HeadChanges.class);
        if (headChanges != null && isWanted(project)) {
            headChanges.refresh();
        }
    }

    /**
     * Asks Gerrit again for every repository with a file open, as the comments may have changed. The others are
     * forgotten, and asked for again once a file of theirs opens.
     */
    public void refresh() {
        Set<GitRepository> open = openRepositories();
        states.keySet().retainAll(open);
        watched.retainAll(open);
        watched.addAll(open);
        for (GitRepository repository : open) {
            update(repository, true);
        }
    }

    private Set<GitRepository> openRepositories() {
        Set<GitRepository> open = new HashSet<>();
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            VirtualFile file = editor.getProject() == project && EditorComments.isFileEditor(editor)
                ? FileDocumentManager.getInstance().getFile(editor.getDocument()) : null;
            GitRepository repository = file != null ? repositoryManager.getRepositoryForFileQuick(file) : null;
            if (repository != null) {
                open.add(repository);
            }
        }
        return open;
    }

    /**
     * The content of a file of the patch set, from git where it has the commit, else from Gerrit. Runs in the calling
     * thread, which must not be the event dispatch thread, and keeps it for as long as HEAD stays.
     *
     * @return null if neither had it
     */
    @Nullable
    String loadContent(HeadChange change, String path, Charset charset) {
        LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread(), "runs git or asks Gerrit");
        String content = change.contents.get(path);
        if (content != null) return content;
        try {
            byte[] bytes = null;
            if (change.local != null) {
                try {
                    bytes = GitFileUtils.getFileContent(project, change.local.getRoot(), change.revisionId, path);
                } catch (VcsException e) {
                    LOG.info("Failed to read " + path + " of " + change.revisionId + " with git, asking Gerrit", e);
                }
            }
            if (bytes == null) {
                bytes = GerritUtil.getInstance()
                    .getFileContent(change.change._number, change.revisionId, path, project);
            }
            content = StringUtil.convertLineSeparators(new String(bytes, charset));
            if (content.startsWith("\uFEFF")) { // which the document of the file does not have
                content = content.substring(1);
            }
            change.contents.put(path, content);
            return content;
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RestApiException | IOException | RuntimeException e) {
            LOG.info("Failed to load " + path + " of change " + change.change._number, e);
            return null;
        }
    }

    private void update(GitRepository repository, boolean force) {
        if (!isWanted(project)) {
            forget();
            return;
        }
        String key = keyOf(repository);
        if (key == null) {
            if (states.remove(repository) != null) {
                publish();
            }
            return;
        }
        State state = states.get(repository);
        if (!force && state != null && state.key.equals(key)
            && (state.failedAt == 0 || System.currentTimeMillis() - state.failedAt < state.retryAfter())) {
            return;
        }
        // in one step, as the repository events come in on another thread than the editors: a request which has not
        // started yet takes this one in, the latest key and forced if either is, and one under way makes it moot
        boolean[] submit = {false};
        pending.compute(repository, (it, waiting) -> {
            if (waiting == null) {
                Request under = running.get(repository);
                if (!force && under != null && key.equals(under.key)) return null;
                submit[0] = true;
                return new Request(key, force);
            }
            return new Request(key, force || waiting.force);
        });
        if (submit[0]) {
            executor.execute(() -> run(repository));
        }
    }

    private static final class Request {
        final String key;
        final boolean force;

        Request(String key, boolean force) {
            this.key = key;
            this.force = force;
        }
    }

    /** On the executor. */
    private void run(GitRepository repository) {
        // under way in the same step it stops waiting, or an update in between would ask for it again
        Request[] taken = {null};
        pending.computeIfPresent(repository, (it, request) -> {
            taken[0] = request;
            running.put(repository, request);
            return null;
        });
        Request request = taken[0];
        if (request == null) return;
        String key = request.key;
        if (project.isDisposed() || !isWanted(project)) { // switched off since it was asked for
            running.remove(repository, request);
            return;
        }
        int editsBefore = edits.get();
        State resolved;
        try {
            resolved = resolve(repository, key, !request.force);
        } catch (Throwable e) { // or the key stays running, and nothing asks for it again
            running.remove(repository, request);
            throw e;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                apply(repository, request, resolved, editsBefore);
            } finally { // only once the state is in, or an update from another thread would ask again
                running.remove(repository, request);
            }
        }, project.getDisposed());
    }

    private void apply(GitRepository repository, Request request, State resolved, int editsBefore) {
        if (!isWanted(project)) return; // switched off meanwhile, which dropped what there was
        if (!request.key.equals(keyOf(repository))) { // HEAD moved meanwhile; a refresh still is one
            update(repository, request.force);
            return;
        }
        if (resolved.changes.stream()
            .anyMatch(change -> lastEditOf.getOrDefault(change.change._number, 0) > editsBefore)) {
            update(repository, true); // for the comment saved meanwhile, which would vanish until then
            return;
        }
        if (!GitUtil.getRepositoryManager(project).getRepositories().contains(repository)) {
            return; // no longer a root of the project
        }
        states.put(repository, resolved);
        publish();
        if (resolved.failedAt != 0) {
            // nothing else might ask again for a long while; the listeners do, for the files they show, so that it
            // ends once no file of the repository is open
            retries.addRequest(this::publish, resolved.retryAfter() + 1000);
        }
    }

    /** Drops what is known, as the comments have moved on by the time they are wanted again. */
    void forget() {
        states.clear();
    }

    /**
     * What has to stay for the changes found before to be shown until the new ones are in. Of another account they
     * are another server's, to which no comment must go. On another branch they are most likely another change: a
     * commit or an amend keeps the branch, CheckoutAction makes one of its own.
     */
    private String reusableKey(GitRepository repository) {
        String branch = repository.getCurrentBranchName();
        // a detached HEAD has no branch to tell, so it is the commit which has to stay
        return accountKey() + "\n" + (branch != null ? branch : repository.getCurrentRevision()) + "\n";
    }

    private String accountKey() {
        GerritAccount account = GerritProjectAccount.getInstance(project).get();
        return account != null ? account.id + " " + account.host + " " + account.login : "";
    }

    /** Whether to look for changes on HEAD at all: never in a project which is not reviewed on Gerrit. */
    public static boolean isWanted(Project project) {
        return GerritProjectSettings.isEnabled(project) && GerritSettings.getInstance().getShowCommentsInEditor()
            && !GerritProjectAccount.getInstance(project).getHost().isEmpty();
    }

    private void publish() {
        ApplicationManager.getApplication().invokeLater(
            () -> project.getMessageBus().syncPublisher(Listener.TOPIC).headChangesChanged(), project.getDisposed());
    }

    /**
     * What the changes on HEAD depend on: the account they come from, and what the platform knows of the repository
     * without asking git.
     */
    @Nullable
    private String keyOf(GitRepository repository) {
        String head = repository.getCurrentRevision();
        if (head == null) return null;
        String key = reusableKey(repository) + head;
        GitRemoteBranch upstream = upstreamOf(repository);
        // not its commit: a fetch moves it all the time and mostly leaves the changes on HEAD as they are
        return upstream == null ? key : key + " " + upstream.getFullName();
    }

    @Nullable
    private static GitRemoteBranch upstreamOf(GitRepository repository) {
        GitBranchTrackInfo trackInfo = GitUtil.getTrackInfoForCurrentBranch(repository);
        // a branch whose upstream was pruned, or never fetched, is on its own: git has nothing to leave out
        return trackInfo != null && repository.getBranches().getHash(trackInfo.getRemoteBranch()) != null
            ? trackInfo.getRemoteBranch() : null;
    }

    /**
     * @param reuse whether a change found before which is still on HEAD with the same patch set keeps its files and
     *              comments, as after a commit on top or an amend; a refresh asks for them again
     */
    private State resolve(GitRepository repository, String key, boolean reuse) {
        State previous = states.get(repository);
        try {
            GitRemoteBranch upstream = upstreamOf(repository);
            List<Pair<String, String>> commits = GerritGitUtil.getInstance().getCommitMessagesOnHead(
                repository, upstream != null ? upstream.getFullName() : null, MAX_STACK);
            List<String> terms = new ArrayList<>();
            for (Pair<String, String> commit : commits) {
                terms.add("commit:" + commit.first);
                String changeId = changeIdOf(commit.second);
                if (changeId != null) {
                    terms.add("change:" + changeId);
                }
            }
            if (terms.isEmpty()) {
                return new State(key, Collections.emptyList(), 0, 0, false);
            }

            GerritUtil gerritUtil = GerritUtil.getInstance();
            List<ChangeInfo> found = gerritUtil.queryChangesWithFiles(
                "is:open+(" + String.join("+OR+", terms) + ")", project);
            GerritGitUtil gerritGitUtil = GerritGitUtil.getInstance();
            Set<String> onHead = commits.stream().map(commit -> commit.first).collect(Collectors.toSet());
            List<HeadChange> changes = new ArrayList<>();
            for (Pair<ChangeInfo, String> match : match(commits, found,
                upstream != null ? upstream.getNameForRemoteOperations() : null,
                change -> gerritGitUtil.getRepositoryForChange(project, change).equals(Optional.of(repository)))) {
                ChangeInfo change = match.first;
                String revision = match.second;
                HeadChange known = previous != null ? previous.changes.stream()
                    .filter(it -> it.change._number == change._number && it.revisionId.equals(revision))
                    .findFirst().orElse(null) : null;
                if (known != null && reuse) {
                    changes.add(known);
                    continue;
                }
                // a patch set never changes, only its comments do
                Set<String> files = known != null ? known.files : filesOf(change, revision, gerritUtil);
                changes.add(new HeadChange(change, revision, onHead.contains(revision) ? repository : null, files,
                    gerritUtil.loadComments(change._number, revision, project, true, true),
                    known != null ? known.contents : new ConcurrentHashMap<>()));
            }
            return new State(key, changes, 0, 0, false);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (VcsException | RestApiException | RuntimeException e) {
            boolean again = previous != null && previous.key.equals(key);
            int failures = again ? previous.failures + 1 : 1;
            String message = "Failed to find the Gerrit changes on HEAD of " + repository.getRoot();
            if (failures == 1) {
                LOG.info(message, e);
            } else {
                LOG.info(message + ", " + failures + " times in a row: " + e);
            }
            // a refresh which fails keeps showing what there was rather than nothing, and so does one after a commit
            // or an amend, though without taking comments: HEAD may be another change by now
            boolean kept = previous != null && previous.key.startsWith(reusableKey(repository));
            return new State(key, kept ? previous.changes : Collections.emptyList(), System.currentTimeMillis(),
                failures, kept && (!again || previous.provisional));
        }
    }

    /**
     * For each commit, from HEAD down: the change and the patch set which is the commit, or else the current patch
     * set of the change its Change-Id names on the branch HEAD is meant for. A change is taken once, by the commit
     * nearest to HEAD.
     *
     * @param commits hash and message
     * @param branch which the changes are meant for, null if not known
     * @param inRepository whether a change is of this repository, as its remotes tell; a commit is proof enough
     */
    @VisibleForTesting
    static List<Pair<ChangeInfo, String>> match(List<Pair<String, String>> commits, List<ChangeInfo> changes,
                                               @Nullable String branch, Predicate<ChangeInfo> inRepository) {
        List<Pair<ChangeInfo, String>> matches = new ArrayList<>();
        Set<Integer> taken = new HashSet<>();
        for (Pair<String, String> commit : commits) {
            ChangeInfo match = null;
            String revision = null;
            // the commit is proof enough, but it may have been pushed to another project as well
            for (ChangeInfo change : changes) {
                if (change.revisions == null || !change.revisions.containsKey(commit.first)) continue;
                boolean mine = inRepository.test(change);
                if (match == null || mine) {
                    match = change;
                    revision = commit.first;
                    if (mine) break;
                }
            }
            String changeId = changeIdOf(commit.second);
            if (match == null && changeId != null) {
                // the same Change-Id is a change of its own on every branch it was cherry-picked to; without the
                // branch HEAD is meant for, only one such change tells which it is
                List<ChangeInfo> candidates = changes.stream()
                    .filter(change -> changeId.equals(change.changeId)
                        && (branch == null || branch.equals(change.branch))
                        && change.currentRevision != null && inRepository.test(change))
                    .collect(Collectors.toList());
                if (candidates.size() == 1) {
                    match = candidates.get(0);
                    revision = match.currentRevision;
                }
            }
            if (match != null && taken.add(match._number)) {
                matches.add(Pair.create(match, revision));
            }
        }
        return matches;
    }

    private Set<String> filesOf(ChangeInfo change, String revision, GerritUtil gerritUtil) throws RestApiException {
        RevisionInfo revisionInfo = change.revisions.get(revision);
        // Gerrit lists the files of the current patch set only, HEAD may be an earlier one
        return filesOf(revisionInfo != null && revisionInfo.files != null ? revisionInfo.files
            : gerritUtil.getRevisionFiles(change._number, revision, project));
    }

    /** Those of the files Gerrit lists which the patch set leaves in place, without the ones Gerrit makes up. */
    @VisibleForTesting
    static Set<String> filesOf(Map<String, FileInfo> files) {
        Set<String> paths = new HashSet<>();
        files.forEach((path, file) -> {
            if (!path.startsWith("/") && (file.status == null || file.status != 'D')) {
                paths.add(path);
            }
        });
        return paths;
    }

    /** The last Change-Id footer of a commit message, as Gerrit reads it. */
    @VisibleForTesting
    @Nullable
    static String changeIdOf(String message) {
        // Gerrit reads the footer, the last paragraph, but not the subject of a message which has no other
        String trimmed = message.trim();
        int footerStart = trimmed.lastIndexOf("\n\n");
        int subjectEnd = trimmed.indexOf('\n');
        String footer = footerStart >= 0 ? trimmed.substring(footerStart + 2)
            : subjectEnd >= 0 ? trimmed.substring(subjectEnd + 1) : "";
        String changeId = null;
        Matcher matcher = CHANGE_ID.matcher(footer);
        while (matcher.find()) {
            changeId = matcher.group(1);
        }
        return changeId;
    }

    @Override
    public void dispose() {
        // what is queued still runs, and finds the project disposed
    }
}
