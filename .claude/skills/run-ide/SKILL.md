---
name: run-ide
description: Run the plugin in a real IDE on a virtual display against a throwaway local Gerrit, then drive it through Remote Robot and look at screenshots. Use to see a change working in the IDE (settings page, Gerrit tool window, push dialog, change actions), or to check the javassist and reflection code against the oldest and the newest IDE.
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

Everything lives under `$RUN_IDE_WORK` (default
`~/.cache/gerrit-plugin-run-ide`), where the downloads are kept for later
sessions in the same container. Point `RUN_IDE_SHOTS` at the session's
scratchpad so the user can open the screenshots. Shell state does not carry
over between commands, so repeat the `export` line in each one. The SDK's
runtime comes from `cache-redirector.jetbrains.com`, which must be reachable,
and the Remote Robot plugin from `packages.jetbrains.team`, without which only
`xdotool` drives the IDE. Gerrit needs Java 21 on the `PATH`. A first start
downloads Gerrit, the SDK, its runtime and the robot and can take several
minutes: give `gerrit.sh start` and `ide.sh start` a 10 minute timeout, or run
them in the background.

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

`ide.sh start latest` runs the newest IDE, `latestIdeaVersion` in
`gradle.properties`, downloaded on first use; `ide.sh start <dir>` runs any
unpacked one. Run the newest unless a change has a reason to need 2020.3: most
users run it, and the push dialog, javassist and reflection break on new IDEs,
not on 2020.3.

`smoke.sh [latest|<dir>]` starts Gerrit and that IDE, then walks the plugin's
main paths and checks each against Gerrit: the account on the settings page,
the change list, a push for review with a reviewer through the push dialog, Star
from the context menu, comments on removed, added and unchanged lines of a diff
in the side-by-side and the unified viewer, comments in the editor of a checked
out change as its text is edited, and the project switched off and on again,
without the tool window and the push options in between, and fails on an error
the plugin logged. It takes about a minute once everything is downloaded, and on
failure prints a screenshot path.

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

The IDE runs JetBrains' Remote Robot server plugin, and `robot.py` finds its
components by XPath over the Swing tree, so there are no screen coordinates to
measure and the same commands work on 2020.3 and 2026.2. Its usage line lists
every command, and `smoke.sh` is the worked example to copy from. Each new
sandbox needs the account once, which `login` puts in:

```
R=.claude/skills/run-ide/scripts/robot.py
$R login                                    # the seeded Gerrit, on the Gerrit settings page
$R tree gerrit                              # what there is: class, accessible name, text
$R rows "//div[@class='TableView']"         # the change list, one row per line
```

What `smoke.sh` works around, worth knowing for anything new:

* Class names differ between versions, `ProjectViewTree` against
  `MyProjectViewTree` or `StripeButton` against `SquareStripeButton`, so match
  them with `contains()`. Name the tool window in the XPath, as other tool
  windows have a Refresh too; `(XPATH)[2]` picks the second of several matches.
* Tool window buttons toggle, and the IDE restores a window open, so check with
  `find` before clicking one. The push dialog remembers "Push to Gerrit" per
  project: set it with `check XPATH on` rather than clicking it.
* Keys go to the focused frame: `focus` before `key` after anything else had
  the focus.
* A dialog or popup the IDE shows of its own accord takes the keys and clicks
  until it is gone. `ide.vmoptions` turns off the ones seen so far, such as the
  theme tour of a first 2025.3+ start, with its registry key.

`get XPATH EXPR` evaluates JavaScript over the component for anything the
commands do not cover. Screenshots still show what the robot cannot, such as
painting and layout. The frame is pinned to the 1600x1000 display on start, so
screenshots of different runs line up; `$S/shot.sh detail 800x480+400+20` crops
one to read small text. The details panel of the Gerrit tool window starts
small: `key ctrl+shift+QUOTE` maximises the window, and a `get` which scrolls
to a text position (`modelToView`, then `scrollRectToVisible`) beats dragging
the splitter; read what it shows back with `get` too. Without the robot, where
`packages.jetbrains.team` is blocked, `xdotool` still clicks at screen
coordinates and types once the IDE has been clicked (`xdotool mousemove X Y
click 1`, `xdotool key ctrl+alt+s`); the account then goes in by hand:
`http://localhost:8080`, `admin`, `secret`.

Gerrit 3.14 has no assignee endpoint (`PUT /changes/N/assignee` is a 404), so
"Set Assignee" can only be checked for its dialog and error balloon here.

## Noise that is not the plugin's

* Balloons about `JAVA_TOOL_OPTIONS` (the sandbox's proxy) and, on new IDEs,
  about the script launcher.
* On 2026.2 a SEVERE from `DaemonClient` blaming "JetBrains OS Integration".
