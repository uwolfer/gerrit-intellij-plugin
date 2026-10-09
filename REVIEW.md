# REVIEW.md

What a code review of this repository should look for, and what it should not
raise again. AGENTS.md has the conventions; this file has the decisions behind
code which a review would otherwise keep questioning.

## Look for

* Correctness on both the oldest supported IDE (2020.3) and the newest
  (`latestIdeaVersion`): an API which one of them lacks or changed.
* Threading: no network, git or full-file work on the event dispatch thread;
  model changes only where the platform allows them.
* Leaks: listeners, alarms and highlighters tied to a disposable, and nothing
  which keeps a closed project or editor reachable.
* A comment, draft or action going to the wrong change, patch set or line.

## Settled, do not raise

* **API the minimum IDE lacks, or which is internal or experimental.** The
  plugin compiles against 2020.3 and does not adopt `@ApiStatus.Internal` or
  `Experimental` API (AGENTS.md). That rules out, among others, the review in
  the editor of collaboration-tools (`ReviewInEditorUtil`,
  `CodeReviewEditorDocumentUtil`, `trackDocumentDiffSync`, component inlays)
  and `DocumentTracker`, whose `Lock` is internal by now. Where such API would
  replace our code, the code carries a `TODO once the minimum IDE has` note;
  that note is the answer, not a finding.
* **Kotlin or coroutines.** The plugin is Java; suspend functions and flows
  are out of reach until the minimum IDE moves.
* **Commit messages of a branch under review.** Work-in-progress commits are
  squashed, and the message written, before the merge.

A decision which concerns one class lives in a comment on that class, where a
review reading the code finds it; read those before raising a point about it.
