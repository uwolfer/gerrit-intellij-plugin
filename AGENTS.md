# AGENTS.md

Instructions for AI coding agents working on this repository.

## Build and test

```
./gradlew build
```

This runs the instrumentation, which weaves the `.form` files into their
classes and adds the `@NotNull` checks, so it is the command that actually
validates a UI change. CI runs the same on JDK 17.

Sandboxed environments differ in what they can reach. Check rather than assume
— both of the limits below have been true at some point and neither is
permanent:

* Maven Central through `repo.maven.apache.org` may answer `429 Too Many
  Requests`, which fails the build before it compiles anything. Gradle treats a
  429 as fatal and will not fall through to the next repository, so point it at
  Google's Maven Central mirror with the init script
  `.claude/hooks/maven-mirror.init.gradle`. In Claude Code on the web the
  `SessionStart` hook `.claude/hooks/session-start.sh` installs it into
  `${GRADLE_USER_HOME:-~/.gradle}/init.d/` whenever the mirror answers, so that
  every Gradle run there picks it up, including the one `run-ide` starts. It
  does not ask Maven Central first, as the limit comes and goes within seconds
  and no answer at session start says what comes later: the cost is that the
  sandbox builds from the mirror while Maven Central is healthy too. Where the
  hook did not install it (`ls ~/.gradle/init.d` shows), pass the script with
  `-I` (`./gradlew -I .claude/hooks/maven-mirror.init.gradle build`), but not
  on top of the installed copy. The mirror belongs to the environment, not to the
  project: it stays an init script, and `build.gradle` stays untouched.

  One mirror, not a list of them: a second one is only reachable after the
  first answers, and a 429 from the first ends the build either way.

* The instrumentation jars come from `cache-redirector.jetbrains.com`. Where
  that host is blocked, `instrumentCode` fails with `taskdef class
  com.intellij.ant.InstrumentIdeaExtensions cannot be found` because only the
  `.pom` files arrive and the Ant classpath ends up empty. Then, and only then,
  fall back to `./gradlew test -x instrumentCode -x instrumentTestCode`, which
  leaves the `.form` files unchecked. One request settles it:

  ```
  BASE=https://cache-redirector.jetbrains.com/intellij-repository/releases
  curl -sSLo /dev/null -w '%{http_code}\n' \
    $BASE/com/jetbrains/intellij/java/java-compiler-ant-tasks/203.8084.24/java-compiler-ant-tasks-203.8084.24.jar
  ```

The first build downloads the Gradle distribution and the ~1.6 GB IntelliJ SDK.

Two parts of `build` are worth knowing about when you change the UI. After
changing a class bound to a `.form`, check that the generated `$$$setupUI$$$()`
still ends up in the constructor you touched — a signature change is silently
fine until it is not:

```
javap -p -c build/instrumented/instrumentCode/<class>.class | grep setupUI
```

And `buildSearchableOptions` starts a headless IDE and walks every
configurable, so it fails on a settings page which cannot be built, and its
output under `build/searchableOptions` shows what the platform made of one.

To see a change in a running IDE, against a local Gerrit, follow
`.claude/skills/run-ide/SKILL.md`. It runs the oldest supported IDE and, after
a one-time download, the newest one on a virtual display, which is the only way
to watch the push dialog integration below actually work.
`.claude/skills/run-ide/scripts/smoke.sh [latest]` drives the main paths
through it, push dialog included, and checks each against Gerrit.

## Bytecode injection and reflection into the platform

The push-dialog integration is the most fragile code here.

`GerritPushExtension` rewrites `git4idea.push.GitPushSupport` with javassist at
application startup, because the platform offers no extension point for the push
dialog. `createOptionsPanel` is the only method it rewrites, and the new body
refers to private members of the platform (`mySettings`, `myVcs`,
`GitVersionSpecialty`). It also copies the classes the panel uses, listed in
`CLASSES_FOR_GIT_PLUGIN`, into the Git plugin class loader: a class which is
missing from that list is a `NoClassDefFoundError` in the push dialog.
The account suggestions of the reviewers and CC fields need the REST client,
which that class loader cannot load: `install()` hands them to the copied panel
by calling its `setAccountCompletion` by name, and a panel without them shows
plain text fields. `GerritPushTargetUpdater` finds the repository rows by
looking in the tree of the dialog, with `com.intellij.dvcs.push` classes:
public, but the dialog's own UI and not an extension point.

`GerritPushExtensionTest` checks the three things above that would otherwise
only fail at startup: that the rewrite compiles against the Git plugin, that
the list holds every class of the plugin or of the Gerrit REST client which the
copied classes use, and that the panel still has the method the suggestions
are handed over with. It runs against the SDK the build uses.
`-PideaVersion=<latest>` fails while resolving `git4idea`, so the newest IDE
is checked with `.claude/skills/run-ide/scripts/smoke.sh latest` instead: it
fails when the push dialog does, or on an error which names the plugin.

`ResetAction` is the other place which reaches into the platform: it calls the
constructor of `GitNewResetDialog`, protected in 2020.3 and public in 2026.2,
and `GitResetOperation.execute`, whose return type differs between IDE
releases, by reflection. No test covers it and it fails only when the action
runs, so try Reset in the IDE after a platform upgrade.

None of this is a documented extension point. Names and signatures change
without notice between IDE releases, and the push dialog fails with a startup
error rather than a compile error. When you touch this code, say in the commit
message which platform version you checked it against.

Crash reports pointing into `javassist.*` are usually javassist bugs. Check what
the *reporting* version bundled before reading plugin code:

```
git show v1.2.6-203:build.gradle | grep javassist
```

Sessions often start from a shallow clone without tags, which also makes
`git log -S` useless. Two seconds fixes it:

```
git fetch --unshallow --tags origin   # omit --unshallow if .git/shallow is gone
```

## Services

Collaborators are platform services: `@Service(Service.Level.APP)` on a final
class plus a static `getInstance()` returning
`ApplicationManager.getApplication().getService(X.class)`. Callers look them up
where they need them; do not pass them through constructors.

`GerritUtil` and `GerritGitUtil` resolve their collaborators per call rather
than in fields, so the unit tests can construct them outside a running IDE.

## Commit messages

Keep them lean.

* Imperative subject line, roughly 50 characters, no trailing period.
* Add a body only when the *why* is not obvious from the diff, wrapped at 72.
* Do not restate the diff or enumerate touched files.
* Keep the `Co-Authored-By` and `Claude-Session` trailers.

## Code comments

Only write a comment that earns its place. A comment explains *why* — a platform
quirk, a constraint, a reason the straightforward approach does not work. Never
narrate what the next line does, and do not add Javadoc to self-evident methods.
The reflection and javassist code is where comments genuinely pay off.

New files get the Apache 2.0 header with the **current year** as the copyright
year. Do not copy the year range from the file you started from.

## Branches and versions

The default branch is `intellij2020.3` and targets the oldest supported IDE
(`since-build="203.4818.26"`, `ideaVersion=IC-2020.3.4`). Pull requests go
there, so fixes reach every newer branch. `gradle.properties` targets Java 11.
