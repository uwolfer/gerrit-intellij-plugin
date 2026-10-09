# AGENTS.md

Instructions for AI coding agents working on this repository.

## Branches and versions

The default branch `intellij2020.3` targets the oldest supported IDE
(`since-build="203.4818.26"`, `ideaVersion=IC-2020.3.4`, Java 11). Pull
requests go there, so fixes reach every newer IDE. `latestIdeaVersion` in
`gradle.properties` is the newest IDE, which CI verifies the plugin against.

## Platform API: plan against the newest IDE

The plugin compiles against 2020.3, but most users run a current IDE, and the
2020.3 SDK is the wrong place to learn how the platform wants something done.
Before planning a change that touches the platform, read how the newest IDE
does it:

* the platform API and implementation you are about to call, extend or imitate;
* the bundled plugins solving the same problem: `plugins/github` and
  `plugins/gitlab` with `platform/collaboration-tools` for review UI, accounts,
  avatars and the like, and `plugins/git4idea`, `platform/dvcs-api` and
  `platform/dvcs-impl` for Git and the push dialog.

A sparse clone of intellij-community `master` takes seconds:

```
git clone -q --depth 1 --filter=blob:none --sparse \
  https://github.com/JetBrains/intellij-community <scratchpad>/intellij-community
git -C <scratchpad>/intellij-community sparse-checkout set \
  plugins/git4idea plugins/github plugins/gitlab \
  platform/dvcs-api platform/dvcs-impl platform/collaboration-tools
```

Modules move between releases (`GitPushSupport` is in `plugins/git4idea/backend`
by now), so find a file with `git ls-tree -r --name-only HEAD | grep`, which
needs no checkout; then `git show HEAD:<path>` reads it, or
`git sparse-checkout add <dir>` checks out its module. The tag of the SDK the
build uses is `idea/203.8084.24`.

Bundled plugins may use API marked `@ApiStatus.Internal` or `Experimental`
(`AsyncImageIcon` in 2026.2, for one), which a third-party plugin should not
adopt. Many modules list their experimental API in `api-dump-experimental.txt`,
and the verifier does not fail on internal or experimental API: check yourself.

Use the newest API wherever 2020.3 has it too. Where it does not, write it the
2020.3 way and start the comment at that spot the way the code already does, so
that the next bump of the minimum IDE finds it:

```
TODO once the minimum IDE has <API> (2020.3 has not, <newest IDE> has): <what to use, and what to drop>
```

Name the exact class or method, what goes away with it, and any catch (a Kotlin
suspend function, a module `plugin.xml` must declare). `AvatarIcons` and
`RepositoryChangesBrowserProvider` have examples. When the minimum moves,
`grep -rn "TODO once the minimum IDE" src` lists what to revisit: read each
note in full, conditions included. Mark reflection which only exists to link
against both versions the same way; `ResetAction` predates the marker.

## Build and test

```
./gradlew build
```

This is what the CI `build` job runs, on JDK 17. It instruments the classes,
which weaves the `.form` files into them and adds the `@NotNull` checks, so it is
the command that validates a UI change. The CI `verify-latest-ide` job runs the
plugin verifier against `latestIdeaVersion`, a full Ultimate download, so run it
locally only for a change which touches the platform:

```
./gradlew buildPlugin runPluginVerifier -x buildSearchableOptions
```

The first build downloads the Gradle distribution and the ~1.6 GB IntelliJ SDK.

After changing a class bound to a `.form`, check that the generated
`$$$setupUI$$$()` still ends up in the constructor you touched; a signature
change is silently fine until it is not:

```
javap -p -c build/instrumented/instrumentCode/<class>.class | grep setupUI
```

`buildSearchableOptions` starts a headless IDE and walks every configurable, so
it fails on a settings page which cannot be built, and its output under
`build/searchableOptions` shows what the platform made of one.

### Sandboxes

What a sandbox can reach changes over time. Check rather than assume:

* Maven Central (`repo.maven.apache.org`) may answer `429 Too Many Requests`,
  which Gradle treats as fatal instead of trying the next repository.
  `.claude/hooks/maven-mirror.init.gradle` points Gradle at Google's mirror
  instead. In Claude Code on the web the `SessionStart` hook
  `.claude/hooks/session-start.sh` installs it into
  `${GRADLE_USER_HOME:-~/.gradle}/init.d/` whenever the mirror answers, without
  asking Maven Central first: its limit comes and goes within seconds. Where
  `ls ${GRADLE_USER_HOME:-~/.gradle}/init.d` shows no `maven-mirror.gradle`,
  pass the script with `-I .claude/hooks/maven-mirror.init.gradle`, but not on
  top of an installed one. The mirror belongs to the environment: keep it an
  init script and leave `build.gradle` alone. Use one mirror, not a list: a 429
  from the first ends the build either way.

* The instrumentation jars come from `cache-redirector.jetbrains.com`. Where
  that host is blocked, `instrumentCode` fails with `taskdef class
  com.intellij.ant.InstrumentIdeaExtensions cannot be found`, because only the
  `.pom` files arrive and the Ant classpath ends up empty. Then, and only
  then, fall back to `./gradlew test -x instrumentCode -x instrumentTestCode`,
  which leaves the `.form` files unchecked. Not as a shortcut: once a build
  has instrumented the classes, the tests run from `build/instrumented`, so
  skipping those tasks tests stale classes, and a new test method silently
  does not run. One request settles it:

  ```
  BASE=https://cache-redirector.jetbrains.com/intellij-repository/releases
  curl -sSLo /dev/null -w '%{http_code}\n' \
    $BASE/com/jetbrains/intellij/java/java-compiler-ant-tasks/203.8084.24/java-compiler-ant-tasks-203.8084.24.jar
  ```

* `runPluginVerifier` looks up the IDE releases on `jb.gg` and downloads the
  IDE, and it reads the Java version from `java -version`, whose output a
  sandbox's `JAVA_TOOL_OPTIONS` banner breaks. Where `jb.gg` is blocked, verify
  against the IDE `.claude/skills/run-ide/scripts/ide.sh start latest`
  unpacked. The first run fetches the verifier, through the proxy which
  `JAVA_TOOL_OPTIONS` configures, and then fails on that banner; the second
  runs offline without it:

  ```
  export VERIFY_IDE=$(ls -d ~/.cache/gerrit-plugin-run-ide/ides/*/idea-* | tail -1)
  I=.claude/skills/run-ide/scripts/verify-local.init.gradle
  ./gradlew buildPlugin runPluginVerifier -x buildSearchableOptions -I $I
  env -u JAVA_TOOL_OPTIONS ./gradlew --offline runPluginVerifier -x buildSearchableOptions -I $I
  ```

* Sessions often start from a shallow clone without tags, which breaks
  `git log -S` and `git show <tag>:...`. `git fetch --unshallow --tags origin`
  fixes it (drop `--unshallow` once `.git/shallow` is gone).

### Running the plugin

`.claude/skills/run-ide/SKILL.md` runs the plugin against a throwaway local
Gerrit, in the oldest supported IDE and, after a one-time download, in the
newest one, on a virtual display. It is the only way to watch the push dialog
integration below actually work. `.claude/skills/run-ide/scripts/smoke.sh
[latest]` drives the main paths through it, push dialog included, and checks
each against Gerrit.

Run the IDE once, at the end: after the review loop stops raising new
findings, as each fix it brings voids an earlier run. `smoke.sh latest` is
enough; run 2020.3 too only for a reason, such as an API which behaves
differently there.

## Bytecode injection and reflection into the platform

None of this is a documented extension point. Names and signatures change
without notice between IDE releases, and a break shows as a startup or runtime
error rather than a compile error. When you touch this code, check it against
the newest IDE and say in the commit message which platform version you checked
it against.

2020.3 offers no extension point for the push dialog, so
`GerritPushExtension` rewrites `git4idea.push.GitPushSupport` with javassist at
application startup:

* `createOptionsPanel` is the only method it rewrites. The new body refers to
  private members of the platform (`mySettings`, `myVcs`,
  `GitVersionSpecialty`).
* The classes the panel uses, listed in `CLASSES_FOR_GIT_PLUGIN`, are copied
  into the Git plugin class loader. A class missing from that list is a
  `NoClassDefFoundError` in the push dialog.
* The copies are not the classes this plugin loaded, so their static setters
  are called by name: `GerritPushOptionsPanel.setPushToGerritByDefault`, also
  whenever the setting changes, `GerritPushOptionsPanel.setEnabledForProject`,
  which hands over whether a project uses Gerrit at all, and
  `GerritPushExtensionPanel.setAccountCompletion`, which hands over the account
  suggestions of the reviewers and CC fields. The suggestions need the REST
  client, which the Git plugin class loader cannot load. Without the project
  setting, a panel shows the Gerrit options in every project; without the
  suggestions, plain text fields.
* `GerritPushTargetUpdater` finds the repository rows in the dialog's component
  tree, with `com.intellij.dvcs.push` classes: public, but the dialog's own UI.

`GerritPushExtensionTest` covers what would otherwise only fail at startup: that
the rewrite compiles against the Git plugin, that the list holds every class of
the plugin or the Gerrit REST client the copied classes use, and that the panels
still have the setters called by name. It runs against the SDK the build uses;
`-PideaVersion=<latest>` fails while resolving `git4idea`, so check the newest
IDE with `.claude/skills/run-ide/scripts/smoke.sh latest`, which fails when the
push dialog does or on an error which names the plugin.

`ResetAction` calls the constructor of `GitNewResetDialog` (protected in 2020.3,
public in 2026.2) and `GitResetOperation.execute` (its return type differs
between releases) by reflection. No test covers it, so try Reset in the IDE
after a platform upgrade.

Crash reports pointing into `javassist.*` are usually javassist bugs. Check what
the *reporting* version bundled before reading plugin code (needs the tags, see
Sandboxes):

```
git show v1.2.6-203:build.gradle | grep javassist
```

## Services

Collaborators are platform services: `@Service(Service.Level.APP)` or
`@Service(Service.Level.PROJECT)` on a final class, plus a static
`getInstance()` or `getInstance(Project)` returning `getService(X.class)` of the
application or the project. Callers look them up where they need them; do not
pass them through constructors.

`GerritUtil` and `GerritGitUtil` resolve their collaborators per call rather
than in fields, so the unit tests can construct them outside a running IDE.

## Code review

`REVIEW.md` says what a review should look for, and which decisions it should
not raise again. Add to it when a review keeps raising a point that was settled.

## Commit messages

Keep them lean.

* Imperative subject line, roughly 50 characters, no trailing period.
* Add a body only when the *why* is not obvious from the diff, wrapped at 72.
* Do not restate the diff or enumerate touched files.
* Keep the `Co-Authored-By` and `Claude-Session` trailers.

## Code comments

Only write a comment that earns its place. A comment explains *why*: a platform
quirk, a constraint, a reason the straightforward approach does not work. Never
narrate what the next line does, and do not add Javadoc to self-evident methods.
The reflection and javassist code, and the `TODO once the minimum IDE` notes,
are where comments genuinely pay off.

New files get the Apache 2.0 header with the **current year** as the copyright
year. Do not copy the year range from the file you started from.
