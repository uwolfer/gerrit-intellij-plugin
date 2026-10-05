---
name: run-ide
description: Run the plugin in a real IDE on a virtual display against a throwaway local Gerrit, then drive it with xdotool and look at screenshots. Use to see a change working in the IDE (settings page, Gerrit tool window, push dialog, change actions), or to check the javassist and reflection code against the oldest and the newest IDE.
---

<!--
Copyright 2026 Urs Wolfer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Running the plugin in an IDE

Everything lives under `$RUN_IDE_WORK` (default `~/.cache/gerrit-plugin-run-ide`),
where the downloads are kept for later sessions in the same container. Point
`RUN_IDE_SHOTS` at the session's scratchpad so the user can open the screenshots.
Shell state does not carry over between commands, so repeat the `export` line
in each one. The SDK's runtime comes from `cache-redirector.jetbrains.com`, so
that host must be reachable, and Gerrit needs Java 21 on the `PATH`. A first
start downloads Gerrit, the SDK and its runtime and can take several minutes:
give `gerrit.sh start` and `ide.sh start` a 10 minute timeout, or run them in
the background.

```
export RUN_IDE_SHOTS=<scratchpad>/shots DISPLAY=:99
S=.claude/skills/run-ide/scripts
$S/gerrit.sh start    # Gerrit on localhost:8080, seeded on its first start
$S/ide.sh start       # the SDK the plugin compiles against (2020.3)
$S/shot.sh overview   # prints a PNG path: Read it to see the screen
$S/ide.sh errors      # errors, and warnings about this plugin, from idea.log
$S/ide.sh stop
$S/gerrit.sh stop
```

`ide.sh start` builds the plugin and gives each IDE its own copy, so after a
code change run it again to restart the IDE with the new build.

`ide.sh start <dir>` runs an unpacked IDE instead. Use the newest one,
`latestIdeaVersion` in `gradle.properties`, when touching the push dialog,
javassist or reflection: that code breaks on new IDEs, not on 2020.3.

```
S=.claude/skills/run-ide/scripts
V=$(sed -n 's/^latestIdeaVersion=\([A-Z]*-\)\{0,1\}//p' gradle.properties)
I=${RUN_IDE_WORK:-${XDG_CACHE_HOME:-$HOME/.cache}/gerrit-plugin-run-ide}/ides/$V   # unpacked aside, so a cut-off download is not taken for an IDE
[ -d $I ] || { rm -rf $I.tmp; mkdir -p $I.tmp
  curl -sSfL https://download.jetbrains.com/idea/idea-$V.tar.gz | tar xz -C $I.tmp && mv $I.tmp $I; }
$S/ide.sh start $I/idea-*
```

## The seeded Gerrit

Project `demo` has one merged change and two open ones. "Add hello" has two
patch sets, a Code-Review +1 and an inline comment from `reviewer`; "Add bye"
has `reviewer` added. Logins are `admin`/`secret` and `reviewer`/`reviewer`.
The IDE opens a clone of `demo` with the commit-msg hook installed, so a commit
made there with `git` can be pushed for review from the IDE. If seeding fails,
the next start sets the site up from scratch.

Check what the IDE did through REST rather than trusting a screenshot:

```
curl -sS -u admin:secret 'http://localhost:8080/a/changes/?q=project:demo+status:open&o=DETAILED_LABELS' | tail -n +2
```

`gerrit-review.googlesource.com` answers anonymous REST, for read-only checks
against a large real server. Never write to it.

## Driving the IDE

There is no window manager. `xdotool windowactivate` fails, but keyboard input
reaches the IDE once it has been clicked. Dialogs have no title bar.

```
WID=$(xdotool search --name '^demo' | head -1)
xdotool windowsize $WID 1600 1000 windowmove $WID 0 0   # same coordinates on every start
xdotool mousemove 800 300 click 1                       # focus the editor
xdotool key ctrl+alt+s; xdotool type Gerrit             # Settings, search for the Gerrit page
xdotool key ctrl+shift+k                                # push dialog
$S/shot.sh detail 800x480+400+20                        # crop, to read small text
```

Take coordinates from a screenshot; they differ between IDE versions. Each new
sandbox needs the account once: on the Gerrit settings page enter
`http://localhost:8080`, `admin` and `secret`, then OK. The first start of a new
IDE shows a theme tour; dismiss it with "Skip".

## Noise that is not the plugin's

* Balloons about `JAVA_TOOL_OPTIONS` (the sandbox's proxy) and, on new IDEs,
  about the script launcher.
* On 2026.2 a SEVERE from `DaemonClient` blaming "JetBrains OS Integration".
