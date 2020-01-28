gerrit-intellij-plugin
======================

[![Build Status](https://travis-ci.org/uwolfer/gerrit-intellij-plugin.svg)](https://travis-ci.org/uwolfer/gerrit-intellij-plugin)
[![Version](http://phpstorm.espend.de/badge/7272/version)](https://plugins.jetbrains.com/plugin/7272)
[![Downloads](http://phpstorm.espend.de/badge/7272/downloads)](https://plugins.jetbrains.com/plugin/7272)

Introduction
-----------

Unofficial [IntelliJ Platform](http://www.jetbrains.com/idea/) plugin for the
[Gerrit Code Review](https://www.gerritcodereview.com/) tool. It supports any product based on the IntelliJ platform:
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

*Compiled with Java 1.6*

Only Gerrit 2.6 or newer is supported (missing / incomplete REST API in older versions).

Installation
------------
- Using IDE built-in plugin system (suggested: you'll get notified when an update is available):
  - <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> >
  <kbd>Search for "Gerrit"</kbd> > <kbd>Install Plugin</kbd>
- Manually:
  - Download the [release](https://github.com/uwolfer/gerrit-intellij-plugin/releases)
  matching your IntelliJ version and install it manually using
  <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>

Restart your IDE.

###### Pre-Releases
If you want to get new releases earlier, you can subscribe to the release-candidate plugin channel:

1. Copy the following URL: https://plugins.jetbrains.com/plugins/rc/7272
2. Use the copied URL as the [Custom Plugin Repository](https://www.jetbrains.com/idea/help/managing-enterprise-plugin-repositories.html)
3. Reload the list of plugins
4. Search for the 'Gerrit' plugin in the plugin manager and install it

Your Support
------------
If you like this plugin, you can support it:
* Spread it: Tell your friends who are using IntelliJ and Gerrit about this plugin (or even encourage them to use these fantastic products!)
* Vote for it: Write your review and vote for it at the [IntelliJ plugin repository](http://plugins.jetbrains.com/plugin/7272).
* Star it: [Star it at GitHub](https://github.com/uwolfer/gerrit-intellij-plugin). GitHub account required.
* Improve it: Report bugs or feature requests. Or even fix / implement them by yourself - everything is open source!
* Donate: You can find donation-possibilities at the bottom of this file.

Troubleshooting
---------------
### List of changes is empty
By default, you will only see changes to Git repositories that are configured in the current project of your IntelliJ IDE.
* Make sure that Git repositories are configured in the 'Version Control' settings.
* Make sure that the Git repository remote url (at least one of them) is on the same host as configured in Gerrit plugin settings. Or:
* Set the 'Clone Base URL' if it differs from the Gerrit web url. Or:
* Add a remote whose name equals the Gerrit project name with Gerrit web url as remote url.

### Error-message when clicking a change: "VcsException: fatal: bad object"
In Gerrit 2.8, fetch information was pulled out of default functionality into a plugin.
You need to install the plugin <code>download-commands</code>. When you run the Gerrit update procedure, it asks you to install
this plugin (but it isn't selected by default). Just run the update script again if you have not installed it yet.

When installing Gerrit 2.8 (or newer) from scratch (rather than using the update script) the following command will install the
<code>download-commands</code> plugin (for a new installation or an existing Gerrit instance):

    $ java -jar gerrit.war init -d {gerrit-instance} --install-plugin=download-commands


### Error-message when loading changes: "SSLException: Received fatal alert: bad_record_mac"
There are two workarounds for this issue:
* allow TLSv1 (instead of SSLv3 only) connections in your reverse-proxy in front of Gerrit. SSLv3 is considered insecure, therefore TLS should be the default in any case.
* use a recent Java setup (> 1.6)

### Error-message when loading changes: "Bad Request. Status-Code: 400. Content: too many terms in query."
Open plugin settings and enable the option "List all Gerrit changes (instead of changes from currently open project only)".

### Checking out from VCS with Gerrit plugin does not work
Checking out directly with the Gerrit plugin does not work for some authentication methods. If you get an authentication
error or checking out does not properly finish, you can try to:
* use SSH clone URL in checkout dialog (you can find the SSH URL in the Gerrit Web UI project settings)
* or: check out with the default Git plugin and set up the Gerrit plugin manually afterwards

You can find background information about this issue in a [Gerrit mailing list topic](https://groups.google.com/forum/#!topic/repo-discuss/UnQd3HsL820).

### Loading file-diff-list is slow
Diff viewing is based on Git operations (i.e. it fetches the commit from the Gerrit remote). When loading the file list
takes a lot of time, you can run a local "[git gc](https://www.kernel.org/pub/software/scm/git/docs/git-gc.html)"
and ask your Gerrit administrator to do run a "[gerrit gc](https://gerrit-review.googlesource.com/Documentation/cmd-gc.html)".

### Authenticate against *-review.googlesource.com
It's a bit of manual work to do:
<kbd>Settings</kbd> -> <kbd>HTTP Credentials</kbd> -> <kbd>Obtain password</kbd>

Then search for the line in the text area starting with `*-review.googlesource.com` (e.g. `gerrit-review.googlesource.com`) and extract username and password:

gerrit-review.googlesource.com,FALSE,/,TRUE,12345678,o,**git-username.gmail.com**=**password-until-end-of-line**

Architecture
------------
### IntelliJ Integration
The plugin is integrated into the IntelliJ IDE with a [tool window](http://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Tool+Windows).
See package <code>com.urswolfer.intellij.plugin.gerrit.ui</code>.

### REST API
Most of the communication between the plugin and a Gerrit instance is based on the [Gerrit REST API](https://gerrit-review.googlesource.com/Documentation/rest-api.html).
The REST specific part is available as [standalone implementation](https://github.com/uwolfer/gerrit-rest-java-client).
See package <code>com.urswolfer.intellij.plugin.gerrit.rest</code>.

### Git
Some actions like comparing and listing files are based on Git operations.
[IntelliJ Git4Idea](http://git.jetbrains.org/?p=idea/community.git;a=tree;f=plugins/git4idea) is used for these operations.
See package <code>com.urswolfer.intellij.plugin.gerrit.git</code>.


Build (and develop!) the Plugin
------------------

It's very easy to set it up as an IntelliJ project.

1. Activate plugins ```Gradle```, ```Plugin DevKit``` and ```UI Designer``` in IntelliJ.
2. ```git clone https://github.com/uwolfer/gerrit-intellij-plugin``` (probably switch to ```intellij{version}``` branch, but keep in mind that pull-requests should be against the default branch ("intellij13" and older are not supported anymore))
3. Open checked out project in IntelliJ ("File" -> "New" -> "Project from Existing Sources" -> select file ```build.gradle``` in ```gerrit-intellij-plugin``` folder and press "OK")
4. Create a new run configuration: "Gradle" -> "Gradle project": select the only project -> "Tasks": "runIde"
5. Press "Debug" button. IntelliJ should start with a clean workspace (development sandbox). You need to checkout a
   project to see changes (it shows only changes for Git repositories that are set up in current workspace by default).

Once ```build.gradle``` gets updated, you need to "Refresh all Gradle projects" in the Gradle panel.


Contributing
------------
Check the [`CONTRIBUTING.md`](./CONTRIBUTING.md) file.


Credits
------
* IntelliJ Github plugin (some code of this plugin is based on its code)

Thanks to [JetBrains](https://www.jetbrains.com/) for providing a free licence for developing this project.

Donations
--------
If you like this work, you can support it with
[this donation link](https://www.paypal.com/webscr?cmd=_s-xclick&hosted_button_id=8F2GZVBCVEDUQ).
If you don't like Paypal (Paypal takes 2.9% plus $0.30 per transaction fee from your donation), please contact me.
Please only use the link from github.com/uwolfer/gerrit-intellij-plugin to verify that it is correct.


Copyright and license
--------------------

Copyright 2013 - 2018 Urs Wolfer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

  [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
