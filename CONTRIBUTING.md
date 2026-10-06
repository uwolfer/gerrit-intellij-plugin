# Contributing

Contributions are very welcome.

Please check out the following points for code contributions:
* Licence for your contribution must be Apache 2.0.
* Please try to follow coding style of the file you are editing.
* When possible, create pull requests against the GitHub default branch
  (which is the plugin for the oldest supported IntelliJ version).
  This way your feature / fix will be included in all future versions.

## Building and running

```
./gradlew build     # compiles, instruments the .form files, runs the tests
./gradlew runIde    # a sandbox IDE with the plugin, the oldest supported one
```

IntelliJ IDEA picks up the run configurations in `.run/` when you open the
project:

* **Run IDE** starts the sandbox IDE, or debugs it with the Debug button.
* **Tests** runs the unit tests.
* **Build** is what the CI `build` job runs, which includes starting a headless
  IDE to build every settings page.
* **Verify latest IDE** runs the plugin verifier against the newest IDE, like
  the CI `verify-latest-ide` job, and leaves out the settings page build which
  **Build** covers. It runs on the Gradle JVM: the CI uses JDK 17, and the
  JBR 11 of the 2020.3 SDK is too old for it.

To try a change against a Gerrit of your own, `.claude/skills/run-ide/` starts
a throwaway one and an IDE which opens a clone of it.
