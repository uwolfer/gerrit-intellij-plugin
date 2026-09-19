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
  Google's Maven Central mirror with an init script passed via `-I`. The mirror
  belongs to the environment, not to the project: keep it outside the
  repository and never commit it, and do not touch `build.gradle`.

  ```groovy
  def mirror = 'https://maven-central.storage-download.googleapis.com/maven2'
  beforeSettings { settings ->
      settings.pluginManagement.repositories {
          maven { url mirror }
          gradlePluginPortal()
      }
  }
  beforeProject { project ->
      project.buildscript.repositories { maven { url mirror } }
      project.repositories {
          maven {
              url mirror
              content { // the SDK and the bundled plugins are not on Maven Central
                  excludeGroupByRegex 'com\\.jetbrains.*'
                  excludeGroupByRegex 'unzipped\\..*'
                  excludeGroupByRegex 'org\\.jetbrains\\.intellij.*'
              }
          }
      }
      // drop the frontend that 429s, or those same groups fall through to it
      project.afterEvaluate {
          project.repositories.removeAll { r ->
              r.hasProperty('url') && r.url.toString().contains('repo.maven.apache.org')
          }
      }
  }
  ```

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
    $BASE/com/jetbrains/intellij/java/java-compiler-ant-tasks/233.13135.103/java-compiler-ant-tasks-233.13135.103.jar
  ```

The first build downloads the Gradle distribution and the IntelliJ SDK, which
needs a good 2 GB of disk.

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

## The push dialog

The push-dialog integration is the code most exposed to platform changes.

The settings are an options panel which the dialog picks up through the
experimental `com.intellij.customPushOptionsPanelFactory`, which is what
`since-build` is pinned to. Nothing hands the repository rows to a plugin, so
`GerritPushTargetUpdater` looks them up in the tree of the dialog the panel is
shown in - from `addNotify`, not from a constructor - and drives them with
`RepositoryNode.forceUpdateUiModelWithTypedText` and `fireOnChange`, the calls
the IDE uses when all push targets are edited at once.

Those classes are public but belong to `dvcs-impl`, so a change on either side
fails silently: no rows are found and the settings do nothing. That is why the
panel reports it and `GerritPrePushHandler` stops the push instead of letting it
go out without them. Say in the commit message which platform version you
checked such a change against.

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
The push-dialog code is where comments genuinely pay off.

New files get the Apache 2.0 header with the **current year** as the copyright
year. Do not copy the year range from the file you started from.

## Branches and versions

The default branch is still called `intellij2020.3`, but targets the oldest
supported IDE, 2023.3.2 (`since-build="233.13135"`, `ideaVersion=IC-2023.3.2`,
Java 17). Pull requests go there, so fixes reach every newer branch.
