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
# the settings page, the change list, a push for review with a reviewer picked from
# the suggestions of the push dialog, a change action from the context menu, and no error logged by the plugin.
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
"$R" login
echo "ok: account on the settings page"

G="//div[@accessiblename='Gerrit Tool Window']"
[ -n "$("$R" find "$G")" ] ||  # the button toggles, and the IDE may have restored the window open
    "$R" click "//div[contains(@class,'StripeButton') and @accessiblename='Gerrit']"
"$R" wait "$G//div[@accessiblename='Refresh']"
"$R" click "$G//div[@accessiblename='Refresh']"
T="$G//div[@class='TableView']"
# grep -q reads a string, as under pipefail it would fail a pipeline it leaves early
listed() { local rows; rows=$("$R" rows "$T"); grep -q 'Add hello' <<< "$rows" && grep -q 'Add bye' <<< "$rows"; }
until_true "change list shows the seeded changes" listed

subject=smoke-$(date +%s)
P=$WORK/projects/demo
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
"$R" click "//div[@class='EditorComponentImpl' and contains(@tooltiptext,'added as reviewers')]"
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

echo "IDE log, see SKILL.md for what is not the plugin's:"
errors=$("$HERE/ide.sh" errors); echo "$errors"
# what the plugin logs, such as a class the push dialog could not load, or what is blamed on it
if grep -qE ' (ERROR|SEVERE) - .*(Gerrit|urswolfer|javassist|#c\.u\.i\.p\.g\.)' <<< "$errors"; then
    echo "not: no errors from the plugin" >&2; false
fi
echo "smoke passed"
