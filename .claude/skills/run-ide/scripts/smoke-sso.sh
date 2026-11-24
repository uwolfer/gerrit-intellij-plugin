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
# The sign-in through the IDE's browser, against sso-proxy.py in front of the Gerrit of gerrit.sh: what the
# account dialog says about the sign-in page, a wrong code and the sign-in, the change list and a change action
# through the proxy, the sign-in again from the notification once the proxy forgot it, the sign-in kept over a
# restart, and that neither the Gerrit password nor the proxy's cookie went anywhere it should not.
# Only for 2026.2 and later, which have the sign-in; smoke.sh checks that the IDEs without it show none of it.
# Leaves the IDE running with the project on the account behind the proxy; smoke.sh switches it back.
# Usage: smoke-sso.sh [latest|<unpacked IDE dir>]
set -eEuo pipefail  # -E: the ERR trap fires inside the functions too

WORK=${RUN_IDE_WORK:-${XDG_CACHE_HOME:-$HOME/.cache}/gerrit-plugin-run-ide}
HERE=$(cd "$(dirname "$0")" && pwd)
R=$HERE/robot.py
export DISPLAY=${DISPLAY:-:99}
trap 'echo "smoke-sso FAILED; the screen: $("$HERE/shot.sh" smoke-sso-failed)" >&2' ERR

PROXY=http://localhost:8090
SIGN_IN=http://127.0.0.1:8091
ACCOUNT="admin@$PROXY"
gerrit() { curl -sSf -u admin:secret "${@:2}" "http://localhost:8080/a$1" | tail -n +2; }
sso() { curl -sSf --noproxy '*' "$@"; }
until_true() { # until_true <what> <command...>
    local what=$1; shift
    for _ in $(seq 30); do "$@" && { echo "ok: $what"; return; }; sleep 1; done
    echo "not: $what" >&2; false
}
mark() { sso "$PROXY/.sso/log" | python3 -c 'import json, sys; print(len(json.load(sys.stdin)))'; }
# requests <mark>: what the proxy and the sign-in page got since, one line each: side method path auth= cookie=
requests() { sso "$PROXY/.sso/log" | python3 -c 'import json, sys
for e in json.load(sys.stdin)[int(sys.argv[1]):]:
    print(e["side"], e["method"], e["path"], "auth=%s" % bool(e["authorization"]), "cookie=%s" % (e["cookie"] or ""))' "$1"; }
last_token() { sso "$PROXY/.sso/tokens" | python3 -c 'import json, sys; t = json.load(sys.stdin); print(t[-1] if t else "")'; }

if ! sso -o /dev/null "$PROXY/.sso/log" 2> /dev/null; then
    setsid python3 -I "$HERE/sso-proxy.py" > "$WORK/sso-proxy.log" 2>&1 &
    until_true "the SSO proxy answers" sso -o /dev/null "$PROXY/.sso/log"
fi
sso -X POST "$PROXY/.sso/revoke"  # a sign-in of an earlier run must not let this one through
sso -X POST "$PROXY/.sso/mode/redirect"
START=$(mark)
"$HERE/gerrit.sh" start
RUN_IDE_JCEF=1 "$HERE/ide.sh" start "${1:-latest}"

FRAME="//div[@class='IdeFrameImpl']"
ACCOUNTS="//div[@accessiblename='Gerrit Accounts']//div[@class='JBList']"
D="//div[@class='MyDialog' and contains(@accessiblename,'Gerrit Account')]"
SD="//div[@class='MyDialog' and contains(@accessiblename,'Sign In to')]"
BUTTON="$D//div[@class='JButton' and @accessiblename='Sign In with Browser…']"
G="//div[@accessiblename='Gerrit Tool Window']"
T="$G//div[@class='TableView']"

# message <title> <text>: a message dialog with that text, closed again
message() {
    local m="//div[@class='MyDialog' and @accessiblename='$1']"
    "$R" wait "$m"
    [ -n "$("$R" find "$m//div[@class='JEditorPane' and contains(@accessiblename,'$2')]")" ] ||
        { echo "not: '$1' says '$2'" >&2; "$R" tree "$1" >&2; false; }
    echo "ok: '$1' says '$2'"
    "$R" click "$m//div[@class='JButton' and @accessiblename='OK']"
}
# sign_in_page_since <mark>: the browser asked the sign-in page for itself
sign_in_page_since() { grep -q "^sign-in GET /authorize" <<< "$(requests "$1")"; }
# type_into_sign_in <user> <password> <code>: into the page of the sign-in window, whose first field has the focus;
# the robot does not reach into the page, so the keys go through the X server
type_into_sign_in() {
    local since=$1; shift
    "$R" wait "$SD"
    until_true "the sign-in page in the browser" sign_in_page_since "$since"
    sleep 1  # requested is not painted
    "$R" get "$SD//div[@class='JBCefOsrComponent']" "component.requestFocusInWindow()" > /dev/null
    xdotool type --delay 30 "$1"; xdotool key Tab
    xdotool type --delay 30 "$2"; xdotool key Tab
    xdotool type --delay 30 "$3"; xdotool key Return
}
sign_in_closed() { [ -z "$("$R" find "$SD")" ]; }
gerrit_notifications() { "$R" get "$FRAME" "(function () {
    var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
    var all = com.intellij.notification.NotificationsManager.getNotificationsManager()
        .getNotificationsOfType(com.intellij.notification.Notification, project);
    var out = [];
    for (var i = 0; i < all.length; i++) {
        var n = all[i];
        if (n.getGroupId() != 'gerrit' || n.isExpired()) continue;
        $1
    }
    return out.join('\n');
})()"; }

# The account dialog on a Gerrit behind the proxy, before anyone signed in
"$R" settings
if grep -qxF "$ACCOUNT" <<< "$("$R" rows "$ACCOUNTS")"; then
    "$R" item "$ACCOUNTS" "$ACCOUNT" --double
else
    "$R" click "//div[@class='ActionButton' and @accessiblename='Add']"
fi
"$R" wait "$D"
for field in "Web URL:|$PROXY" "Login:|admin"; do
    "$R" click "$D//div[@class='JBTextField' and @accessiblename='${field%%|*}']"
    "$R" key ctrl+A
    "$R" type "${field#*|}"
done
"$R" click "$D//div[@class='JPasswordField']"
"$R" key ctrl+A
"$R" type secret
[ -n "$("$R" find "$BUTTON")" ] || { echo "not: a Sign In with Browser button" >&2; false; }
echo "ok: a Sign In with Browser button"
"$R" click "$D//div[@class='JButton' and @accessiblename='Test']"
message 'Login Failure' "answered with a web page from $SIGN_IN"
sso -X POST "$PROXY/.sso/mode/direct"  # some proxies show their sign-in page right away
"$R" click "$D//div[@class='JButton' and @accessiblename='Test']"
message 'Login Failure' "answered with a web page from $PROXY"
sso -X POST "$PROXY/.sso/mode/redirect"

# A wrong code keeps the window open on the page which says so, and Cancel stores nothing
tokens_before=$(last_token)
since=$(mark)
"$R" click "$BUTTON"
type_into_sign_in "$since" sso-user sso-pass 000000
refused() { grep -q "^sign-in POST /login" <<< "$(requests "$since")"; }
until_true "the wrong code sent" refused
sleep 2
[ -n "$("$R" find "$SD")" ] || { echo "not: the sign-in window open after a wrong code" >&2; false; }
echo "ok: the sign-in window open after a wrong code"
"$R" click "$SD//div[@class='JButton' and @accessiblename='Cancel']"
until_true "the sign-in window closed by Cancel" sign_in_closed
[ "$(last_token)" = "$tokens_before" ] || { echo "not: no session from a wrong code" >&2; false; }

# The sign-in, which the dialog tests right away
since=$(mark)
"$R" click "$BUTTON"
type_into_sign_in "$since" sso-user sso-pass 123456
until_true "the sign-in window closed by itself" sign_in_closed
message 'Success' 'Connection successful'
"$R" click "$D//div[@class='JButton' and @accessiblename='OK']"
"$R" wait "$ACCOUNTS"
"$R" item "$ACCOUNTS" "$ACCOUNT"  # with several accounts the edited one may not stay selected
"$R" click "//div[@class='ActionButton' and @accessiblename='Use for This Project']"
"$R" check "//div[@class='JCheckBox' and @accessiblename='Use Gerrit in this project']" on
"$R" click "//div[@class='JButton' and @accessiblename='OK']"
settings_closed() { [ -z "$("$R" find "//div[@accessiblename='Gerrit Accounts']")" ]; }
until_true "settings saved" settings_closed

# The change list and a change action through the proxy, with the password for Gerrit and the cookie for the proxy
token=$(last_token)
[ -n "$token" ] || { echo "not: a session from the sign-in" >&2; false; }
through() { grep -q "^proxy GET /a/changes/.* auth=True cookie=.*SSOProxyAccess=$token" <<< "$(requests "$1")"; }
since=$(mark)
[ -n "$("$R" find "$G")" ] || "$R" click "//div[contains(@class,'StripeButton') and @accessiblename='Gerrit']"
"$R" wait "$G//div[@accessiblename='Refresh']"
"$R" click "$G//div[@accessiblename='Refresh']"
listed() { local rows; rows=$("$R" rows "$T"); grep -q 'Add hello' <<< "$rows" && grep -q 'Add bye' <<< "$rows"; }
until_true "change list through the proxy" listed
until_true "the change list asked with the login and the cookie" through "$since"
starred() { local s; s=$(gerrit '/changes/?q=project:demo+is:starred') || return 1; grep -q '"subject":"Add bye"' <<< "$s" && echo yes || echo no; }
was=$(starred)
"$R" item "$T" 'Add bye' --right
"$R" click "//div[@class='ActionMenuItem' and @accessiblename='Star']"
toggled() { local now; now=$(starred) && [ "$now" != "$was" ]; }
until_true "Star through the proxy" toggled

# The proxy forgets the session: the notification signs in again, and the list loads again
gerrit_notifications 'n.expire();' > /dev/null
sso -X POST "$PROXY/.sso/revoke"
"$R" click "$G//div[@accessiblename='Refresh']"
offered() { grep -q 'answered with a web page' <<< "$(gerrit_notifications \
    "if (n.getActions().size() > 0) out.push(n.getContent() + ' | ' + n.getActions().get(n.getActions().size() - 1).getTemplatePresentation().getText());")"; }
until_true "a notification offers to sign in again" offered
since=$(mark)
gerrit_notifications "var a = n.getActions(); for (var j = 0; j < a.size(); j++) {
        if (a.get(j).getTemplatePresentation().getText() == 'Sign in with browser…') {
            var notification = n, action = a.get(j);
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(function () {
                com.intellij.notification.Notification.fire(notification, action, null); });
            return 'fired';
        } }" > /dev/null
type_into_sign_in "$since" sso-user sso-pass 123456
until_true "the sign-in window closed by itself" sign_in_closed
token=$(last_token)
until_true "the change list asked again with the new cookie" through "$since"

# Over a restart, the cookie comes from the credential store. The sandbox's is KeePass, which writes its file when
# the IDE saves its settings, and a stop by signal does not always get that far
"$R" get "$FRAME" "com.intellij.openapi.application.ApplicationManager.getApplication().saveSettings()" > /dev/null
RUN_IDE_JCEF=1 "$HERE/ide.sh" start "${1:-latest}"
since=$(mark)
[ -n "$("$R" find "$G")" ] || "$R" click "//div[contains(@class,'StripeButton') and @accessiblename='Gerrit']"
"$R" wait "$G//div[@accessiblename='Refresh']"
"$R" click "$G//div[@accessiblename='Refresh']"
until_true "change list through the proxy after a restart" listed
until_true "the change list asked with the stored cookie" through "$since"
! sign_in_page_since "$since" || { echo "not: no sign-in after a restart" >&2; false; }

# Nothing went where it should not: the sign-in page got neither the Gerrit password nor the proxy's cookie, and no
# cookie the proxy handed out is in the IDE's log or settings (the credential store is encrypted)
leaked=$(requests "$START" | grep -E '^sign-in .* (auth=True|cookie=.*SSOProxyAccess)' || true)
[ -z "$leaked" ] || { echo "not: nothing sent to the sign-in page: $leaked" >&2; false; }
echo "ok: no password or proxy cookie sent to the sign-in page"
SANDBOX=$(cat "$WORK/ide.sandbox")
for t in $(sso "$PROXY/.sso/tokens" | python3 -c 'import json, sys; print(*json.load(sys.stdin))'); do
    if grep -rqF "$t" "$SANDBOX/system/log" "$SANDBOX/config/options" 2> /dev/null; then
        echo "not: the proxy's cookie $t in $(grep -rlF "$t" "$SANDBOX/system/log" "$SANDBOX/config/options")" >&2; false
    fi
done
echo "ok: no proxy cookie in the IDE's log or settings"

echo "IDE log, see SKILL.md for what is not the plugin's:"
errors=$("$HERE/ide.sh" errors); echo "$errors"
if grep -qE ' (ERROR|SEVERE) - .*(Gerrit|urswolfer|javassist|#c\.u\.i\.p\.g\.)' <<< "$errors"; then
    echo "not: no errors from the plugin" >&2; false
fi
echo "smoke-sso passed"
