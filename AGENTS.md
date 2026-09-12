# AGENTS.md

Instructions for AI coding agents working on this repository.

## Build and test

```
./gradlew test -x instrumentCode -x instrumentTestCode
```

Plain `./gradlew build` fails in sandboxed environments at `instrumentCode`
with `taskdef class com.intellij.ant.InstrumentIdeaExtensions cannot be found`:
the instrumentation jars are served through `cache-redirector.jetbrains.com`,
which redirects to an egress-blocked host, so only the `.pom` files arrive and
the Ant classpath ends up empty. That is an environment limit — do not "fix" it
in `build.gradle`. CI runs the full build on JDK 17, where instrumentation does
run and is needed for the `.form` files and `@NotNull` checks.

The first build downloads the Gradle distribution and the ~1.6 GB IntelliJ SDK.

## Bytecode injection and reflection into the platform

The push-dialog integration is the most fragile code here.

`GerritPushExtension` rewrites `git4idea.push.GitPushSupport` with javassist at
application startup, because the platform offers no extension point for the push
dialog. `GerritPushTargetPanel` then reaches into private platform state:

* `GitPushTargetPanel.myFireOnChangeAction` and `myTargetEditor`
* `val$repoPanel` and `val$repoNode` — synthetic fields of the anonymous
  `Runnable` held in `myFireOnChangeAction`

None of this is API. Field names, the anonymous class and the
`createTargetPanel` signature change without notice between IDE releases, and
the failure is a startup error rather than a compile error. When you touch this
code, say in the commit message which platform version you checked it against.

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
