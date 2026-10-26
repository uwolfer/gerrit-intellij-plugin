---
name: marketplace-screenshots
description: Take the JetBrains Marketplace screenshots of the plugin in the newest IDE, against a local Gerrit seeded with shop test data. Use when the user asks for Marketplace, store or README screenshots, or when the UI changed enough that the listing is out of date.
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

# Marketplace screenshots

Load `run-ide` first: it starts Gerrit and the IDE and documents `robot.py` and `shot.sh`.
Use the newest IDE (`ide.sh start latest`). The first start downloads several GB: start it
in the background and poll `ide.log` for `IDE up`.

```
export RUN_IDE_SHOTS=<scratchpad>/shots DISPLAY=:99
S=.claude/skills/run-ide/scripts; R=$S/robot.py
$S/gerrit.sh start
.claude/skills/marketplace-screenshots/scripts/seed-shop.sh   # users, 4 changes, votes, comments
$S/ide.sh start latest
$R login
```

The seed is untested as a whole after its extraction from the session that wrote it: run
`bash -x` if it fails, and check the change numbers it assumes (4 to 7) against the base seed.

## Make the project look real

* The IDE opens `~/.cache/gerrit-plugin-run-ide/projects/demo`. Fetch a change into it
  (`git fetch origin refs/changes/04/4/2 && git checkout -b feature/shopping-cart FETCH_HEAD`)
  so the branch widget and project tree show code, and a detached HEAD warning is avoided.
* Dismiss the two start balloons: `robot.py click` on the `LinkLabel` and the `JButton` with
  accessible name `Don't show again`.
* Make a local commit (`git commit` in that clone) so the push dialog has something to push.

## The shots (1600x1000 captures, then 1200x760)

1. **Change list and details**: open the Gerrit tool window (click its `StripeButton`), select
   a change with votes (`item ... TableView`), maximise with `ctrl+shift+QUOTE`, and give the
   list more room with
   `get "(//div[@class='OnePixelSplitter'])[2]" "component.setProportion(0.3)"` (no `;`: the
   robot's JavaScript takes one expression). Save as `final-01.png`.
2. **Diff with inline comments**: `item` fails on the changes tree, so double-click the file
   with `xdotool mousemove X Y click --repeat 2 --delay 80 1`. For a real side-by-side diff,
   pick `Diff against` patch set 1 first: click the combo, then `key DOWN`, `key ENTER`
   (clicking the popup row does nothing). Then `shift+ESCAPE` to hide the tool window and
   click the `Project` stripe button to widen the diff. Save as `final-02.png`.
3. **Change actions**: right-click a change (`item ... --right`), capture with the menu open.
   Save as `final-03.png`.
4. **Push dialog**: `focus`, then `ctrl+shift+K` from the editor (the Gerrit tool window eats
   it). Set `check "//div[@text='Push to Gerrit']" on`, the fields by `(//div[@class='JTextField'])[N]`
   (1 branch, 2 topic, 4 patch set description) and `(//div[@class='TextFieldWithCompletion'])[1|2]`
   (reviewers, CC; type full e-mail addresses). Click another field instead of `ESCAPE` to close
   the completion popup: a second `ESCAPE` cancels the dialog. Save as `push-raw.png`.
5. **Settings**: `robot.py settings`, save as `settings-raw.png`.

`scripts/compose.py $RUN_IDE_SHOTS` puts the two dialogs on a gradient and writes
`marketplace-01..05.png` at 1200x760, the size JetBrains recommends (the 1600x1000 display
is a shade wider, which the resize absorbs). Look at every result with Read: the dialog crop boxes in the script were measured for
one run. Do not commit the images: they are uploaded to the Marketplace in the plugin's
settings there. Hand them to the user with `SendUserFile`, named like
`01-change-list-and-details.png`.

The Reply dialog renders clipped over the tool window; skip it. The push dialog has no title
bar on the virtual display and is taller than the screen, so its top is cut.
