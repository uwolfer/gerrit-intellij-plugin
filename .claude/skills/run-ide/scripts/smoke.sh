#!/usr/bin/env bash
#
# Copyright 2026 Urs Wolfer
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# The plugin's main paths through one IDE, each checked against Gerrit: the account on
# the settings page, no sign-in through the browser, which smoke-sso.sh runs, the change list, a push for review with a reviewer picked from
# the suggestions of the push dialog, a change action from the context menu, comments in
# a diff in both viewers and the shortcuts from one to the next, the files, diff and
# comments of a project the IDE has no clone of, comments in the editor of a checked out
# change, the project switched off and on again, and no error logged by the plugin.
# Leaves the IDE running, and only abandoned changes and a toggled star behind, so that
# runs do not push the seeded changes off the change list.
# Usage: smoke.sh [latest|<unpacked IDE dir>]
set -eEuo pipefail  # -E: the ERR trap fires inside the functions too

WORK=${RUN_IDE_WORK:-${XDG_CACHE_HOME:-$HOME/.cache}/gerrit-plugin-run-ide}
HERE=$(cd "$(dirname "$0")" && pwd)
R=$HERE/robot.py
trap 'echo "smoke FAILED; the screen: $("$HERE/shot.sh" smoke-failed)" >&2' ERR

gerrit() { curl -sSf -u admin:secret "${@:2}" "http://localhost:8080/a$1" | tail -n +2; }
until_true() { # until_true <what> <command...>: Gerrit and the change list update asynchronously
    local what=$1; shift
    for _ in $(seq 30); do "$@" && { echo "ok: $what"; return; }; sleep 1; done
    echo "not: $what" >&2; false
}

"$HERE/gerrit.sh" start
"$HERE/ide.sh" start "${1:-}"
# the robot calls in on the event dispatch thread, where newer IDEs allow no change of the model
later() { "$R" get "//div[@class='IdeFrameImpl']" "com.intellij.openapi.application.ApplicationManager.getApplication()
    .invokeLater(function () { $1 })" > /dev/null; }
# ide_on <branch>: what the IDE knows of the demo project, which it only learns from a refresh
ide_on() { [ "$("$R" get "//div[@class='IdeFrameImpl']" "(function () {
    // the robot's class loader has no Git classes, and those of the Git plugin are in modules of their own by now
    var gerrit = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
        com.intellij.openapi.extensions.PluginId.getId('com.urswolfer.intellij.plugin.gerrit')).getPluginClassLoader();
    return com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
        .getService(gerrit.loadClass('git4idea.repo.GitRepositoryManager')).getRepositories().get(0)
        .getCurrentBranchName();
})()")" = "$1" ]; }
refresh_git() {
    later "com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath('$P/.git').refresh(false, true);"
    until_true "the IDE on $1" ide_on "$1"
}
"$R" login
echo "ok: account on the settings page"

# The sign-in through the browser, which smoke-sso.sh runs, is not there where the IDE cannot run it: before 2026.2,
# whose descriptor never loads it, and with the browser switched off, as it is here
none=$("$R" get "//div[@class='IdeFrameImpl']" "(function () {
    var gerrit = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
        com.intellij.openapi.extensions.PluginId.getId('com.urswolfer.intellij.plugin.gerrit')).getPluginClassLoader();
    return gerrit.loadClass('com.urswolfer.intellij.plugin.gerrit.signin.BrowserSignIn').getMethod('find').invoke(null) == null;
})()")
[ "$none" = true ] || { echo "not: no browser sign-in in this IDE" >&2; false; }
"$R" settings
"$R" item "//div[@accessiblename='Gerrit Accounts']//div[@class='JBList']" 'admin@http://localhost:8080' --double
ACCOUNT_DIALOG="//div[@class='MyDialog' and contains(@accessiblename,'Gerrit Account')]"
"$R" wait "$ACCOUNT_DIALOG//div[@class='JButton' and @accessiblename='Test']"
[ -z "$("$R" find "$ACCOUNT_DIALOG//div[@class='JButton' and @accessiblename='Sign In with Browser…']")" ] ||
    { echo "not: no Sign In with Browser button" >&2; false; }
"$R" click "$ACCOUNT_DIALOG//div[@class='JButton' and @accessiblename='Cancel']"
"$R" click "//div[@class='JButton' and @accessiblename='Cancel']"
echo "ok: no browser sign-in in this IDE"

G="//div[@accessiblename='Gerrit Tool Window']"
[ -n "$("$R" find "$G")" ] ||  # the button toggles, and the IDE may have restored the window open
    "$R" click "//div[contains(@class,'StripeButton') and @accessiblename='Gerrit']"
"$R" wait "$G//div[@accessiblename='Refresh']"
"$R" click "$G//div[@accessiblename='Refresh']"
T="$G//div[@class='TableView']"
# grep -q reads a string, as under pipefail it would fail a pipeline it leaves early
listed() { local rows; rows=$("$R" rows "$T"); grep -q 'Add hello' <<< "$rows" && grep -q 'Add bye' <<< "$rows"; }
until_true "change list shows the seeded changes" listed

# Keyboard in the list: the Refresh shortcut reloads it, Enter and a double-click open the compare dialog. A change
# made through REST turns up only on a reload, and is abandoned again.
compare() { [ -n "$("$R" find "//div[contains(@visible_text,'Commits that exist in')]")" ]; }
gone() { ! compare; }
probe=$(gerrit /changes/ -X POST -H 'Content-Type: application/json' \
    -d "{\"project\":\"demo\",\"branch\":\"master\",\"subject\":\"smoke-kbd-$(date +%s)\"}" |
    python3 -c 'import json, sys; print(json.load(sys.stdin)["_number"])')
"$R" item "$T" 'Add bye'
"$R" key ctrl+F5  # the shortcut of Refresh, with the focus in the list
reloaded() { grep -q 'smoke-kbd' <<< "$("$R" rows "$T")"; }
abandon_probe() { gerrit "/changes/$probe/abandon" -X POST -H 'Content-Type: application/json' -d '{}' > /dev/null; }
until_true "Refresh shortcut in the list reloads it" reloaded || { abandon_probe; false; }
abandon_probe
"$R" item "$T" 'Add bye'  # the reload may have moved the selection
"$R" key ENTER
until_true "Enter opens the compare dialog" compare
"$R" key ESCAPE
until_true "compare dialog closed" gone
"$R" item "$T" 'Add bye' --double
until_true "double-click opens the compare dialog" compare
"$R" key ESCAPE
until_true "compare dialog closed" gone

checked() { [ "$("$R" get "$1" 'component.isSelected()')" = "$2" ]; }
subject=smoke-$(date +%s)
P=$WORK/projects/demo
# leaves the change a run which failed in the editor checked out, and nothing else
if [ "$(git -C "$P" rev-parse --abbrev-ref HEAD)" = smoke-review ]; then
    git -C "$P" checkout -qf -B master origin/master
    git -C "$P" branch -qD smoke-review
    refresh_git master
fi
# drops the commit of a run which failed before abandoning it, and nothing else
own=$({ git -C "$P" status --porcelain --untracked-files=no; git -C "$P" log --format=%s master HEAD --not origin/master; } |
    grep -vE '(^| )smoke-[0-9]+(\.txt)?$' || true)
[ -z "$own" ] || { echo "the demo project has work of its own, which smoke.sh would drop: $own" >&2; false; }
git -C "$P" checkout -qf -B master origin/master
echo "$subject" > "$P/$subject.txt"; git -C "$P" add "$subject.txt"; git -C "$P" commit -qm "$subject"
"$R" focus
"$R" key ctrl+shift+K
"$R" wait "//div[@class='JCheckBox' and @accessiblename='Push to Gerrit']"
"$R" check "//div[@class='JCheckBox' and @accessiblename='Push to Gerrit']" on
# the Alt mnemonics of the push options: each toggles or focuses its control, and the push target follows
GERRIT_BOX="//div[@class='JCheckBox' and @accessiblename='Push to Gerrit']"
WIP_BOX="//div[@class='JCheckBox' and contains(@accessiblename,'WIP')]"
target_has() { grep -q -- "$1" <<< "$("$R" rows "//div[@class='CheckboxTree']")"; }
target_lacks() { ! target_has "$1"; }
"$R" key alt+W
until_true "Alt+W ticks WIP" checked "$WIP_BOX" true
until_true "WIP in the push target" target_has '%wip'
"$R" key alt+W
until_true "Alt+W unticks WIP" checked "$WIP_BOX" false
"$R" key alt+G
until_true "Alt+G unticks Push to Gerrit" checked "$GERRIT_BOX" false
"$R" key alt+G
until_true "Alt+G ticks Push to Gerrit" checked "$GERRIT_BOX" true
"$R" key alt+O
"$R" type smoke-topic
until_true "Alt+O focuses the topic field" target_has 'topic=smoke-topic'
"$R" key ctrl+A DELETE
until_true "topic cleared again" target_lacks 'topic='
"$R" key alt+I  # the reviewers field, which the typing below goes into
"$R" type Revi  # of the surname: Gerrit suggests "Rita Reviewer", and ENTER inserts her username
"$R" wait "//div[contains(@class,'List') and contains(@visible_text,'Rita')]"  # JBList, LookupList in 2026.2
"$R" key ENTER
target() { grep -q 'refs/for/master%r=reviewer$' <<< "$("$R" rows "//div[@class='CheckboxTree']")"; }
until_true "push target refs/for/master%r=reviewer" target
"$R" click "//div[@class='MainButton' and @accessiblename='Push']"
reviewed() { grep -q '"username":"reviewer"' <<< "$(gerrit "/changes/?q=project:demo+subject:$subject&o=DETAILED_LABELS&o=DETAILED_ACCOUNTS")"; }
until_true "pushed for review with reviewer" reviewed
gerrit "/changes/demo~master~$(git -C "$P" log -1 --format=%B | sed -n 's/^Change-Id: //p')/abandon" \
    -X POST -H 'Content-Type: application/json' -d '{}' > /dev/null
git -C "$P" checkout -qf -B master origin/master  # an abandoned change cannot be pushed again

starred() { local s; s=$(gerrit '/changes/?q=project:demo+is:starred') || return 1; grep -q '"subject":"Add bye"' <<< "$s" && echo yes || echo no; }
was=$(starred)  # Star toggles, and earlier runs leave it either way
"$R" item "$T" 'Add bye' --right
"$R" click "//div[@class='ActionMenuItem' and @accessiblename='Star']"
toggled() { local now; now=$(starred) && [ "$now" != "$was" ]; }
until_true "Star from the context menu" toggled

# The mnemonics of the menus and dialogs. A dialog is closed with Cancel whatever it holds, so nothing is posted.
cancel() { "$R" click "(//div[@class='JButton' and @accessiblename='Cancel'])[last()]"; }
focused() { # focused <label xpath>: the focus is in the field of the label, not on an icon in it
    [ "$("$R" get "$1" '(function () {
        var owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return javax.swing.SwingUtilities.isDescendingFrom(owner, component.getLabelFor())
            && String(owner.getClass().getName()).indexOf("IconButton") < 0;
    })()')" = true ]; }
# label_keys <dialog> <class> <letter:label>...: each Alt+letter puts the focus in the field of its label
label_keys() {
    local what=$1 class=$2; shift 2
    local pair
    for pair in "$@"; do
        "$R" key "alt+${pair%%:*}"
        until_true "$what: Alt+${pair%%:*} focuses ${pair#*:}" focused "//div[@class='$class' and @accessiblename='${pair#*:}']"
    done
}
# box_keys <what> <class> <letter:text>...: each Alt+letter toggles its check box
toggles() { local before; before=$("$R" get "$1" 'component.isSelected()'); "$R" key "alt+$2"; sleep 0.5
    [ "$("$R" get "$1" 'component.isSelected()')" != "$before" ]; }
box_keys() {
    local what=$1 class=$2; shift 2
    local pair
    for pair in "$@"; do
        until_true "$what: Alt+${pair%%:*} toggles ${pair#*:}" toggles "//div[@class='$class' and contains(@accessiblename,'${pair#*:}')]" "${pair%%:*}"
    done
}

# the menu of the change list: each item has its own mnemonic, and a key opens it
menu() { "$R" item "$T" 'Add bye' --right; "$R" wait "//div[@class='ActionMenuItem' and @accessiblename='Star']"; }
menu_mnemonics() { "$R" get "//div[@class='ActionMenuItem' and @accessiblename='Star']" "(function () {
    var out = [], items = component.getParent().getComponents();
    for (var i = 0; i < items.length; i++) {
        if (items[i].getMnemonic && items[i].getMnemonic() != 0)
            out.push(String.fromCharCode(items[i].getMnemonic()) + ' ' + items[i].getText());
    }
    return out.join('\\n');
})()"; }
expected_menu=$'M Compare with Branch\nC Checkout\nN Inspect Code...\nP Cherry-Pick\nK Cherry-Pick (No Commit)\nH Reset Current Branch to Here...\nS Star\nV Add Reviewers\nI Set Assignee...\nL Publish Draft\nD Delete Draft\nE Reply...\nU Submit\nA Abandon\nB Rebase\nT Restore\nR Revert\nY Copy Change-ID\nG Copy Change URL\nF Refresh Feature Merge Patch Set\nO Open in Gerrit'
menu_ok() { [ "$(menu_mnemonics)" = "$expected_menu" ]; }
menu
until_true "menu of the change list: a mnemonic on each item, none twice" menu_ok
"$R" key ESCAPE
was=$(starred); menu; "$R" key S
until_true "menu: S toggles Star" toggled
was=$(starred); menu; "$R" key S  # and back, for the next run
until_true "menu: S toggles Star back" toggled

# Reply opens the review dialog with no vote
menu; "$R" key E
NOTIFY_BOX="//div[@class='JCheckBox' and @accessiblename='Send Notification Mails']"
SUBMIT_BOX="//div[@class='JCheckBox' and @accessiblename='Submit Change']"
"$R" wait "$NOTIFY_BOX"
until_true "review dialog: Send Notification Mails starts ticked" checked "$NOTIFY_BOX" true
"$R" key alt+N
until_true "review dialog: Alt+N unticks Send Notification Mails" checked "$NOTIFY_BOX" false
"$R" key alt+C
until_true "review dialog: Alt+C ticks Submit Change" checked "$SUBMIT_BOX" true
cancel

# Rebase; the change is on the tip, so the first option is off and its T cannot be tried
menu; "$R" key B
"$R" wait "//div[@class='JBCheckBox' and contains(@accessiblename,'conflicts')]"
"$R" key alt+S
until_true "rebase dialog: Alt+S picks the specific base" checked "//div[@class='JBRadioButton' and contains(@accessiblename,'specific')]" true
"$R" key alt+C
until_true "rebase dialog: Alt+C ticks Allow rebase with conflicts" checked "//div[@class='JBCheckBox' and contains(@accessiblename,'conflicts')]" true
cancel

# Set Assignee: the focus leaves the field, and Alt+A brings it back
menu; "$R" key I
ASSIGNEE="//div[@class='JBLabel' and contains(@accessiblename,'Assignee')]"
"$R" wait "$ASSIGNEE"
"$R" key TAB
"$R" key alt+A
until_true "assignee dialog: Alt+A focuses the assignee field" focused "$ASSIGNEE"
cancel

# Add Reviewers has a field and no label, so only its menu key is tried
menu; "$R" key V
"$R" wait "//div[@class='MyDialog' and @accessiblename='Add Reviewers to Change']"
echo "ok: menu: V opens Add Reviewers"
cancel

# the settings page
"$R" settings
"$R" wait "//div[@class='JCheckBox' and @accessiblename='Use Gerrit in this project']"
box_keys "settings" JCheckBox p:"Use Gerrit in this" l:"List all Gerrit" v:"Show avatars" e:"Check for new" \
    r:"Show notifications" b:"Push commits" c:"Show the comments" f:"Show comments in full"
# the account dialog, opened on the account of the seeded Gerrit
"$R" item "//div[@accessiblename='Gerrit Accounts']//div[@class='JBList']" 'admin@http://localhost:8080' --double
"$R" wait "//div[@class='JBLabel' and @accessiblename='Web URL:']"
label_keys "account dialog" JBLabel w:"Web URL:" l:"Login:" p:"Password:" u:"Clone base URL:" g:"Gitiles URL:"
cancel
"$R" click "//div[@class='JButton' and @accessiblename='Cancel']"

# the clone dialog: Directory takes a mnemonic though a browse icon sits in its field
gerrit_clone() { "$R" get "//div[@class='IdeFrameImpl']" "com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(function () {
    var l = com.intellij.openapi.vcs.CheckoutProvider.EXTENSION_POINT_NAME.getExtensionList();
    for (var i = 0; i < l.size(); i++) {
        if (String(l.get(i).getVcsName()) == 'Gerrit')
            l.get(i).doCheckout(com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0], null);
    }
})" > /dev/null; }
gerrit_clone
"$R" wait "//div[@class='JBLabel' and @accessiblename='Directory:']"
"$R" click "//div[@class='TextFieldWithAutoCompletion']"
label_keys "clone dialog" JBLabel d:"Directory:"
label_keys "clone dialog" JBLabel u:"URL:"
cancel

# Create Feature Merge Change
"$R" click "//div[@class='ActionButton' and @accessiblename='Create Feature Merge Change']"
"$R" wait "//div[@class='JLabel' and @accessiblename='Gerrit project:']"
label_keys "feature merge dialog" JLabel p:"Gerrit project:" s:"Source branch:" t:"Target branch:" u:"Subject:" \
    i:"Topic (optional):" r:"Reviewers (comma separated, optional):" c:"CC (comma separated, optional):"
cancel

# Comments in a diff, on a change of its own whose patch sets differ in removed, added and unchanged lines, and in
# one which only its trailing space tells apart
dsubject=$subject-diff
for left in $(gerrit '/changes/?q=project:demo+status:open+subject:diff' | python3 -c 'import json, re, sys
print(*(c["_number"] for c in json.load(sys.stdin) if re.fullmatch(r"smoke-[0-9]+-diff", c["subject"])))'); do
    gerrit "/changes/$left/abandon" -X POST -H 'Content-Type: application/json' -d '{}' > /dev/null  # of a failed run
done
# in a worktree of its own, which a run that stops half-way leaves behind rather than a changed project
W=$WORK/smoke-diff
git -C "$P" worktree remove -f "$W" 2> /dev/null || git -C "$P" worktree prune
git -C "$P" worktree add -q --detach "$W" origin/master
printf 'Demo\nkeep\nold\ntail\n' > "$W/README.md"
git -C "$W" commit -qam "$dsubject"; git -C "$W" push -q origin HEAD:refs/for/master
printf 'Intro\nkeep\nnew\ntail \n' > "$W/README.md"
git -C "$W" commit -qa --amend --no-edit; git -C "$W" push -q origin HEAD:refs/for/master
dchange=demo~master~$(git -C "$W" log -1 --format=%B | sed -n 's/^Change-Id: //p')
git -C "$P" worktree remove -f "$W"
# the balloons of the push lie over the files of the change, and take the clicks meant for them
"$R" notifications
"$R" click "$G//div[@accessiblename='Refresh']"
dlisted() { grep -q "$dsubject" <<< "$("$R" rows "$T")"; }
until_true "change list shows the diff change" dlisted
"$R" item "$T" "$dsubject"
"$R" click "$G//div[@class='JLabel' and @accessiblename='Diff against: ']/following-sibling::div[1]"  # against patch set 1
L="//div[contains(@class,'List') and contains(@visible_text,'Base')]"
"$R" wait "$L"
"$R" item "$L" "$("$R" rows "$L" | grep '^1: ')"
F="//div[contains(@class,'ChangesBrowserTreeList')]"  # AsyncChangesBrowserTreeList in 2026.2
file() { "$R" rows "$F" | grep -m1 'README.md$'; }
listed_file() { file > /dev/null; }
until_true "files of the change" listed_file
"$R" item "$F" "$(file)" --double
D="//div[contains(@class,'DiffPanel')]"
"$R" wait "$D//div[@class='EditorComponentImpl']"
viewer() { # viewer Side-by-side|Unified: picked unless it is the one shown
    local panel=SimpleDiffPanel button=1
    [ "$1" = Side-by-side ] || { panel=UnifiedDiffPanel; button=2; }
    if [ -z "$("$R" find "$D[@class='$panel']")" ]; then
        local combo="//div[contains(@class,'ComboBoxButton') and contains(@accessiblename,' viewer')]"
        if [ -n "$("$R" find "$combo")" ]; then
            "$R" click "$combo"
            "$R" item "//div[contains(@class,'List') and contains(@visible_text,'$1 viewer')]" "$1 viewer"
        else  # 2026.2 has two buttons without a name instead
            "$R" click "(//div[@class='SegmentedButtonComponent']/div[@class='SegmentedButton'])[$button]"
        fi
    fi
    "$R" wait "$D[@class='$panel']"
}
# comment <editor> <line> <text> [<end line>]: through the action of the editor, as its shortcut and menu do,
# on a line or from the start of one line to the end of another, both counted from 0
comment() {
    local select="e.getSelectionModel().removeSelection(); e.getCaretModel().moveToOffset(d.getLineStartOffset($2));"
    [ $# -lt 4 ] || select="e.getSelectionModel().setSelection(d.getLineStartOffset($2), d.getLineEndOffset($4));"
    "$R" get "$1" "(function () {
        var e = component.getEditor(), d = e.getDocument(), K = java.awt.event.KeyEvent;
        $select
        var action = e.getUserData(com.intellij.openapi.util.Key.findKeyByName('gerrit.AddCommentAction'));
        com.intellij.openapi.actionSystem.ActionManager.getInstance().tryToExecute(action,
            new K(component, K.KEY_PRESSED, java.lang.System.currentTimeMillis(), 0, K.VK_C, K.CHAR_UNDEFINED),
            component, 'smoke', true);
        return action;
    })()" > /dev/null
    [ -n "$3" ] || return 0
    "$R" wait "//div[@class='CommentForm']"
    "$R" type "$3"
    "$R" key ctrl+ENTER
}
# "<patch set> <side> <line> <message>" of each draft
drafts() { gerrit "/changes/$dchange/drafts" | python3 -c 'import json, sys
for c in json.load(sys.stdin).get("README.md", []): print(c["patch_set"], c.get("side", "REVISION"), c["line"], c["message"])'; }
drafted() { grep -qx "$1" <<< "$(drafts)"; }
# "<line> <message>" of each comment icon in an editor, the line counted from 1
icons() { "$R" get "$1" "(function () {
    var e = component.getEditor(), out = [];
    e.getMarkupModel().getAllHighlighters().forEach(function (h) {
        var r = h.getGutterIconRenderer();
        if (r != null && String(r.getClass().getName()).indexOf('urswolfer') >= 0)
            out.push((e.getDocument().getLineNumber(h.getStartOffset()) + 1) + ' ' + String(r.getTooltipText()).replace(/^.*<br\/>/, ''));
    });
    return out.sort().join('\\n');
})()"; }
viewer Side-by-side
comment "($D//div[@class='EditorComponentImpl'])[1]" 2 old-left
until_true "side-by-side: comment on the left on patch set 1" drafted "1 REVISION 3 old-left"
comment "($D//div[@class='EditorComponentImpl'])[2]" 2 new-right
until_true "side-by-side: comment on the right on patch set 2" drafted "2 REVISION 3 new-right"
# Next and Previous Gerrit Comment, by the shortcuts they share with Next and Previous Occurrence
# caret <editor> <line>: there, counted from 0, and in the focus, for the keys to reach it
caret() { "$R" get "$1" "(function () {
    component.getEditor().getCaretModel().moveToOffset(component.getEditor().getDocument().getLineStartOffset($2));
    com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance().requestFocus(component, true);
})()" > /dev/null; }
# at <editor> <line>: the caret there, and the editor in the focus
at() { [ "$("$R" get "$1" "component.getEditor().getCaretModel().getLogicalPosition().line + ' ' + component.hasFocus()")" = "$2 true" ]; }
SL="($D//div[@class='EditorComponentImpl'])[1]" SR="($D//div[@class='EditorComponentImpl'])[2]"
until_true "side-by-side: the comments shown" eval '[ "$(icons "$SL")$(icons "$SR")" = "3 old-left3 new-right" ]'
caret "$SL" 0
until_true "side-by-side: the caret on the first line" at "$SL" 0
"$R" key ctrl+alt+DOWN
until_true "side-by-side: next comment, on the left" at "$SL" 2
"$R" key ctrl+alt+DOWN
until_true "side-by-side: next comment, on the right next to it" at "$SR" 2
"$R" key ctrl+alt+DOWN
"$R" wait "//div[contains(@visible_text,'No comment further down')]" 10
echo "ok: side-by-side: a hint after the last comment"
"$R" key ESCAPE
"$R" key ctrl+alt+UP
until_true "side-by-side: previous comment, back on the left" at "$SL" 2
viewer Unified
U="$D//div[@class='EditorComponentImpl']"
# ignore <policy>: what the viewer compares, set as its settings do; it rebuilds the text in a rediff
ignore() { "$R" get "$U" "com.intellij.ide.DataManager.getInstance().getDataContext(component)
    .getData(com.intellij.diff.tools.util.DiffDataKeys.DIFF_VIEWER).getTextSettings()
    .setIgnorePolicy(com.intellij.diff.tools.util.base.IgnorePolicy.$1)" > /dev/null; }
ignore DEFAULT  # the viewer remembers the policy
# its lines: Demo, Intro, keep, old, tail, new, "tail "
shows() { [ "$(icons "$U")" = "$1" ]; }
until_true "unified: the comments of both sides" shows $'4 old-left\n6 new-right'
comment "$U" 0 removed
until_true "unified: a removed line on patch set 1" drafted "1 REVISION 1 removed"
comment "$U" 1 added
until_true "unified: an added line on patch set 2" drafted "2 REVISION 1 added"
comment "$U" 2 unchanged
until_true "unified: an unchanged line on patch set 2" drafted "2 REVISION 2 unchanged"
comment "$U" 0 removed-range 2
until_true "unified: a range over removed and unchanged lines on patch set 1" drafted "1 REVISION 2 removed-range"
comment "$U" 0 "" 1
"$R" wait "//div[contains(@visible_text,'on lines of the same side')]" 10
echo "ok: unified: a selection from a removed to an added line gets a hint"
placed=$'1 removed\n2 added\n3 removed-range\n3 unchanged\n4 old-left\n6 new-right'
until_true "unified: each comment on its line" shows "$placed"
caret "$U" 3
until_true "unified: the caret on a comment" at "$U" 3
"$R" key ctrl+alt+DOWN
until_true "unified: next comment, on the other side" at "$U" 5
"$R" key ctrl+alt+UP
"$R" key ctrl+alt+UP
until_true "unified: previous comment, past the one at the caret" at "$U" 2
# the text of each range, as highlighted: of the left side, it shows the added line in between as well
ranges() { "$R" get "$1" "(function () {
    var e = component.getEditor(), out = [];
    e.getMarkupModel().getAllHighlighters().forEach(function (h) {
        if (String(h.getTextAttributesKey()).indexOf('GERRIT_COMMENT_RANGE') >= 0)
            out.push(String(e.getDocument().getText().substring(h.getStartOffset(), h.getEndOffset())));
    });
    return out.join('|');
})()"; }
highlighted() { [ "$(ranges "$U")" = "$1" ]; }
until_true "unified: the range of the comment highlighted" highlighted $'Demo\nIntro\nkeep'
# trimmed, the tails are the same line, shown once: the lines after the old one move up
ignore TRIM_WHITESPACES
until_true "unified: the comments on their lines after a rediff" shows "${placed/6 new-right/5 new-right}"
ignore DEFAULT
until_true "unified: the comments back on their lines" shows "$placed"
# the Alt mnemonics of the comment form: Resolved ticks, Save posts the draft as resolved
comment "$U" 0 ""
RESOLVED_BOX="//div[@class='JCheckBox' and @accessiblename='Resolved']"
"$R" wait "$RESOLVED_BOX"
"$R" type alt-s-comment  # while the focus is in the text, which Alt+R leaves where it is
until_true "comment form: Resolved starts unticked" checked "$RESOLVED_BOX" false
"$R" key alt+R
until_true "comment form: Alt+R ticks Resolved" checked "$RESOLVED_BOX" true
"$R" key alt+S
resolved_draft() { gerrit "/changes/$dchange/drafts" | python3 -c 'import json, sys
sys.exit(not any(c["message"] == "alt-s-comment" and not c.get("unresolved") for l in json.load(sys.stdin).values() for c in l))'; }
until_true "comment form: Alt+S saves the draft as resolved" resolved_draft
gerrit "/changes/$dchange/abandon" -X POST -H 'Content-Type: application/json' -d '{}' > /dev/null

# setting <check box> on|off <what>: on the Gerrit settings page, saved
setting() {
    "$R" settings
    "$R" check "$1" "$2"
    "$R" click "//div[@class='JButton' and @accessiblename='OK']"
    closed() { [ -z "$("$R" find "//div[@accessiblename='Gerrit Accounts']")" ]; }
    until_true "settings saved: $3" closed
}

# A change of project library, which the IDE has no clone of: its files and their diff come from Gerrit
ALL="//div[@class='JCheckBox' and contains(@accessiblename,'List all Gerrit changes')]"
setting "$ALL" on "all changes listed"
"$R" click "$G//div[@accessiblename='Refresh']"
llisted() { grep -q 'Greet strangers' <<< "$("$R" rows "$T")"; }
until_true "change list shows the change of a project without a clone" llisted
"$R" item "$T" 'Greet strangers'
lfiles() { local rows; rows=$("$R" rows "$F"); grep -q 'Greeter.java' <<< "$rows" && grep -q 'CHANGES.txt' <<< "$rows" &&
    grep -q 'obsolete.txt' <<< "$rows" && grep -q 'logo.png' <<< "$rows"; }
until_true "files of the change from Gerrit" lfiles
# Revert and the like would write the file of the same path in the project open; the toolbar updates a moment later
norevert() { [ -z "$("$R" find "$G//div[@accessiblename='Revert Selected Changes']")" ]; }
until_true "no Revert on files without a local copy" norevert
"$R" item "$F" "$("$R" rows "$F" | grep -m1 'Greeter.java$')" --double
"$R" wait "$D//div[@class='EditorComponentImpl']"
viewer Side-by-side
lshows() { [ "$(icons "$SR")" = '5 A blank name too?' ]; }
until_true "the comment of the patch set in the diff from Gerrit" lshows
lchange=library~master~$(gerrit '/changes/?q=project:library+status:open' | python3 -c 'import json, sys
print(json.load(sys.stdin)[0]["change_id"])')
comment "$SR" 7 from-gerrit
ldrafted() { gerrit "/changes/$lchange/drafts" | python3 -c 'import json, sys
for c in json.load(sys.stdin).get("src/lib/Greeter.java", []): print(c["line"], c["message"])' | grep -qx '8 from-gerrit'; }
until_true "a comment in the diff from Gerrit on its line" ldrafted
for id in $(gerrit "/changes/$lchange/drafts" | python3 -c 'import json, sys
print(*(c["id"] for c in json.load(sys.stdin).get("src/lib/Greeter.java", [])))'); do
    curl -sSf -u admin:secret -X DELETE "http://localhost:8080/a/changes/$lchange/revisions/current/drafts/$id" > /dev/null
done
setting "$ALL" off "only the project's changes listed"

# Comments in the editor of a file of a checked out change: on the line of the patch set, as the text is edited
read -r hnumber hrevision href <<< "$(gerrit '/changes/?q=project:demo+status:open+subject:hello&o=CURRENT_REVISION' |
    python3 -c 'import json, sys
c = json.load(sys.stdin)[0]; print(c["_number"], c["current_revision"], c["revisions"][c["current_revision"]]["ref"])')"
git -C "$P" fetch -q origin "$href"
git -C "$P" checkout -qf -B smoke-review FETCH_HEAD  # as CheckoutAction leaves it: the patch set, tracking its branch
git -C "$P" branch -q --set-upstream-to=origin/master
refresh_git smoke-review
later "com.intellij.openapi.fileEditor.FileEditorManager.getInstance(
        com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0])
    .openFile(com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath('$P/hello.txt'), true);"
E="//div[@class='EditorComponentImpl' and not(ancestor::div[contains(@class,'DiffPanel')])]"
"$R" wait "$E"
eshows() { [ "$(icons "$E")" = "$1" ]; }
until_true "editor: the comment of the patch set on its line" eshows '2 Capitalise World?'
hdrafted() { gerrit "/changes/$hnumber/drafts" | python3 -c 'import json, sys
for c in json.load(sys.stdin).get("hello.txt", []): print(c["patch_set"], c["line"], c["message"])' | grep -qx "$1"; }
# a click in the editor while the form is open, as to look something up, leaves the comment on its line
comment "$E" 0 ""
"$R" wait "//div[@class='CommentForm']"
"$R" get "$E" "component.getEditor().getCaretModel().moveToOffset(component.getEditor().getDocument().getLineStartOffset(1))" > /dev/null
"$R" type clicked-away
"$R" key ctrl+ENTER
until_true "editor: a click away leaves the comment on its line" hdrafted "2 1 clicked-away"
edit() { "$R" get "$E" "(function () {
    var e = component.getEditor(), d = e.getDocument();
    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(function () {
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(e.getProject(),
            new java.lang.Runnable({ run: function () { $1 } }));
    });
    return d.getTextLength();
})()" > /dev/null; }
edit "d.insertString(0, 'one\\ntwo\\n');"
until_true "editor: the comment moves with lines inserted above" eshows $'3 clicked-away\n4 Capitalise World?'
edit "d.replaceString(d.getLineStartOffset(3), d.getLineEndOffset(3), 'World');"
until_true "editor: the comment stays on its rewritten line" eshows $'3 clicked-away\n4 Capitalise World?'
# the icons move with the text at once, but a comment waits for the text to be compared again, a moment after an edit
form() { comment "$E" 2 ""; sleep 1; [ -n "$("$R" find "//div[@class='CommentForm']")" ]; }
until_true "editor: the edits compared" form
"$R" type on-hello
"$R" key ctrl+ENTER
until_true "editor: an unchanged line takes a comment on its line of the patch set" hdrafted "2 1 on-hello"
until_true "editor: the new comment on its line" eshows $'3 clicked-away\n3 on-hello\n4 Capitalise World?'
comment "$E" 0 ""
"$R" wait "//div[contains(@visible_text,'not in the patch set')]" 10
echo "ok: editor: a line added locally gets a hint"
# back to the text on disk, or the IDE asks which one to keep once the checkout below changes it
edit "d.setText('hello\\nworld\\n');"
later "com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments();"
saved() { [ "$("$R" get "$E" "com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
    .getUnsavedDocuments().length")" = 0 ]; }
until_true "editor: the text back as on disk" saved
for id in $(gerrit "/changes/$hnumber/drafts" | python3 -c 'import json, sys
print(*(c["id"] for c in json.load(sys.stdin).get("hello.txt", []) if c["message"] in ("on-hello", "clicked-away")))'); do
    curl -sSf -u admin:secret -X DELETE "http://localhost:8080/a/changes/$hnumber/revisions/$hrevision/drafts/$id" > /dev/null
done
git -C "$P" checkout -qf -B master origin/master
git -C "$P" branch -qD smoke-review
later "com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath('$P').refresh(false, true);"
until_true "the IDE on master" ide_on master
until_true "editor: no comments once HEAD left the change" eshows ''

USE="//div[@class='JCheckBox' and @accessiblename='Use Gerrit in this project']"
STRIPE="//div[contains(@class,'StripeButton') and @accessiblename='Gerrit']"
use_gerrit() { setting "$USE" "$1" "Gerrit $1"; }
use_gerrit off
hidden() { [ -z "$("$R" find "$STRIPE")" ]; }
until_true "no Gerrit tool window in a project switched off" hidden
echo "$subject" > "$P/$subject.txt"; git -C "$P" add "$subject.txt"; git -C "$P" commit -qm "$subject"
"$R" focus
"$R" key ctrl+shift+K
"$R" wait "//div[@class='MainButton' and @accessiblename='Push']"
plain() { grep -q 'master → origin' <<< "$("$R" rows "//div[@class='CheckboxTree']")"; }
until_true "the platform's push target" plain
# what the panel would have added is only there once the tree has rows, which plain waited for
[ -z "$("$R" find "//div[@class='JCheckBox' and @accessiblename='Push to Gerrit']")" ] ||
    { echo "not: no Gerrit push options in a project switched off" >&2; false; }
echo "ok: no Gerrit push options in a project switched off"
"$R" click "//div[@class='JButton' and @accessiblename='Cancel']"  # a push here would land on master
git -C "$P" checkout -qf -B master origin/master
use_gerrit on
shown() { [ -n "$("$R" find "$STRIPE")" ]; }
until_true "Gerrit tool window back in a project switched on" shown
[ -n "$("$R" find "$G")" ] || "$R" click "$STRIPE"
"$R" wait "$G//div[@accessiblename='Refresh']"
"$R" click "$G//div[@accessiblename='Refresh']"
until_true "change list loads again" listed

echo "IDE log, see SKILL.md for what is not the plugin's:"
errors=$("$HERE/ide.sh" errors); echo "$errors"
# what the plugin logs, such as a class the push dialog could not load, or what is blamed on it
if grep -qE ' (ERROR|SEVERE) - .*(Gerrit|urswolfer|javassist|#c\.u\.i\.p\.g\.)' <<< "$errors"; then
    echo "not: no errors from the plugin" >&2; false
fi
echo "smoke passed"
