# Gerrit for IntelliJ

[![Version](https://img.shields.io/jetbrains/plugin/v/7272)](https://plugins.jetbrains.com/plugin/7272)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/7272)](https://plugins.jetbrains.com/plugin/7272)

Unofficial [IntelliJ Platform](https://www.jetbrains.com/idea/) plugin for
[Gerrit Code Review](https://www.gerritcodereview.com/): review, fetch and push
changes without leaving your IDE.

## Features

* Review changes in the IDE: vote, reply and comment on files and lines
* See and answer the comments of a checked out change in the editor, on their
  lines as you edit the file
* Diff changes against your local clone, with the IDE's syntax highlighting and
  navigation
* List and query changes, and get notified of new changes waiting for your
  review
* Push to Gerrit from the push dialog: target branch, topic, reviewers and CC
  with account suggestions, and on Gerrit 2.15+ a hashtag, WIP and private
  changes
* Check out, cherry-pick and reset to changes in your local clone
* Submit, abandon, star changes and add reviewers; publish and delete draft
  changes (Gerrit older than 2.15)
* Run the IDE's inspections on the files a change touches
* Clone Gerrit projects from the IDE, with the commit-msg hook installed
* Open files and commits in Gitiles, and the Gerrit changes of commits selected
  in the VCS log
* Several Gerrit accounts, one per project

## Requirements

* Gerrit 2.9 or newer (older versions lack parts of the REST API)
* Any IDE based on the IntelliJ Platform 2020.3 or newer:
  * IntelliJ IDEA
  * IntelliJ IDEA CE
  * RubyMine
  * WebStorm
  * PhpStorm
  * PyCharm
  * PyCharm CE
  * AppCode
  * Android Studio
  * DataGrip
  * CLion
  * GoLand
  * Rider
  * MPS

## Installation

* From the IDE (recommended, you get notified of updates):
  <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd>, search for
  "Gerrit" and <kbd>Install</kbd>.
* Manually: download the [release](https://github.com/uwolfer/gerrit-intellij-plugin/releases)
  matching your IDE and install it with <kbd>Settings</kbd> > <kbd>Plugins</kbd> >
  <kbd>⚙</kbd> > <kbd>Install Plugin from Disk...</kbd>.

Then restart the IDE.

## Getting started

1. Open <kbd>Settings</kbd> > <kbd>Version Control</kbd> > <kbd>Gerrit</kbd> and
   add an account with the web URL of your Gerrit, your login and your HTTP
   password (in the Gerrit web UI under <kbd>Settings</kbd> >
   <kbd>HTTP Credentials</kbd>, or <kbd>HTTP Password</kbd> in older versions). <kbd>Test</kbd> checks the connection.
2. Open the <kbd>Gerrit</kbd> tool window. It lists the changes of the Gerrit
   projects whose Git repositories are part of your IDE project.
3. Push as usual: the push dialog has a "Push to Gerrit" section for the
   Gerrit options. "Push commits to Gerrit by default" in the settings ticks it
   in new push dialogs; after that, each project keeps your last choice.

With accounts on several Gerrit instances, add all of them and pick the one a
project talks to with <kbd>Use for This Project</kbd>. A project whose remotes
point at only one of them picks it by itself.

To clone a project from Gerrit, use <kbd>Get from VCS</kbd> and pick
<kbd>Gerrit</kbd> as version control.

## Troubleshooting

### List of changes is empty

By default, the list only shows changes of the Git repositories in your IDE
project.

* Check that the repositories are registered under <kbd>Settings</kbd> >
  <kbd>Version Control</kbd>.
* Check that at least one remote of each repository points at the host of your
  Gerrit account.
* If Gerrit is cloned from another host than its web UI, set the "Clone base
  URL" of the account.
* Or add a remote named like the Gerrit project, with the Gerrit web URL as its
  URL.

### Error-message when clicking a change: "No repository found for Gerrit project"

With "List all Gerrit changes (instead of changes from the currently open
project only)", the list also shows changes of projects that are not part of
your IDE project. You can vote on and submit these, but showing the files and
diff of a change, checking it out and cherry-picking it fetch the change into
the local clone of its project. Clone the project and add it to your IDE
project to see its diff. Reviewing without a local clone is tracked in
[#76](https://github.com/uwolfer/gerrit-intellij-plugin/issues/76).

### Error-message when clicking a change: "Cannot fetch changes"

Gerrit 2.9 and 2.10 tell the plugin where to fetch a change from only with the
`download-commands` plugin installed; newer versions do without it. The Gerrit
update procedure offers to install it, but it isn't selected by default, so run
the update again if you skipped it. For an existing or new instance, this
installs it:

    java -jar gerrit.war init -d {gerrit-instance} --install-plugin=download-commands

### Error-message when loading changes: "Bad Request. Status-Code: 400. Content: too many terms in query."

Enable "List all Gerrit changes (instead of changes from the currently open
project only)" in the plugin settings.

### Error-message when loading changes: "SSLException: Received fatal alert: bad_record_mac"

Your Gerrit (or the reverse proxy in front of it) only accepts SSLv3. Enable
TLS there; SSLv3 is insecure anyway.

### Checking out from VCS with Gerrit plugin does not work

Cloning from the plugin does not work with every authentication method. If it
fails to authenticate or does not finish:

* use the SSH clone URL in the clone dialog (the Gerrit web UI shows it in the
  project settings), or
* clone with the Git plugin and add the Gerrit account afterwards.

Background is in this
[Gerrit mailing list topic](https://groups.google.com/forum/#!topic/repo-discuss/UnQd3HsL820).

### Loading file-diff-list is slow

The file list and diffs come from Git: the plugin fetches the change from
Gerrit. Running [`git gc`](https://git-scm.com/docs/git-gc) locally and
[`gerrit gc`](https://gerrit-review.googlesource.com/Documentation/cmd-gc.html)
on the server (ask your Gerrit administrator) usually helps.

### Authenticate against *-review.googlesource.com

In the Gerrit web UI, open <kbd>Settings</kbd> > <kbd>HTTP Credentials</kbd> >
<kbd>Obtain password</kbd>. In the text shown, find the line starting with your
host (e.g. `gerrit-review.googlesource.com`) and take login and password from
it:

    gerrit-review.googlesource.com,FALSE,/,TRUE,12345678,o,git-username.gmail.com=password-until-end-of-line

Here the login is `git-username.gmail.com` and the password is everything after
the `=`.

## How it works

* **IDE integration:** a [tool window](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
  lists the changes, see `com.urswolfer.intellij.plugin.gerrit.ui`. The push
  dialog has no extension point, so the plugin adds its options to Git's push
  dialog at startup, see `com.urswolfer.intellij.plugin.gerrit.push`.
* **REST API:** most of the communication with Gerrit uses the
  [Gerrit REST API](https://gerrit-review.googlesource.com/Documentation/rest-api.html),
  through the standalone [gerrit-rest-java-client](https://github.com/uwolfer/gerrit-rest-java-client),
  see `com.urswolfer.intellij.plugin.gerrit.rest`.
* **Git:** listing the files of a change, diffs, checkout and cherry-pick run on
  the local clone with the IDE's [Git plugin](https://github.com/JetBrains/intellij-community/tree/master/plugins/git4idea),
  see `com.urswolfer.intellij.plugin.gerrit.git`.

## Development

Open the project in IntelliJ IDEA with the Gradle, Plugin DevKit and UI
Designer plugins enabled. [`CONTRIBUTING.md`](./CONTRIBUTING.md#building-and-running)
explains how to build, test and run it in a sandbox IDE. Pull requests go to the
default branch `intellij2020.3`, which targets the oldest supported IDE, so a
change reaches every newer one.

## Support the project

If you like this plugin, you can support it:

* Spread it: tell your friends who use IntelliJ and Gerrit about it.
* Rate it on the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7272-gerrit).
* [Star it on GitHub](https://github.com/uwolfer/gerrit-intellij-plugin).
* Improve it: report bugs and request features, or fix and build them
  yourself, everything is open source.
* Donate, see below.

### Donations

You can support this work with
[this donation link](https://www.paypal.com/webscr?cmd=_s-xclick&hosted_button_id=8F2GZVBCVEDUQ).
If you would rather not use PayPal (it takes 2.9% plus $0.30 of each donation),
please contact me. Only trust the link on
github.com/uwolfer/gerrit-intellij-plugin.

## Credits

* Parts of this plugin are based on the code of the IntelliJ GitHub plugin.

Thanks to [JetBrains](https://www.jetbrains.com/) for providing a free license
for developing this project.

## Copyright and license

Copyright 2013 - 2026 Urs Wolfer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

  [https://www.apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
