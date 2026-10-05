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
# Runs a throwaway Gerrit on http://localhost:8080, seeded on its first start with
# project demo. Logins: admin/secret and reviewer/reviewer.
# Usage: gerrit.sh start|stop
set -eEuo pipefail  # -E: the ERR trap in seed also fires inside rest

WORK=${RUN_IDE_WORK:-${XDG_CACHE_HOME:-$HOME/.cache}/gerrit-plugin-run-ide}
GERRIT_VERSION=${GERRIT_VERSION:-3.14.4}
SITE=$WORK/gerrit/site
URL=http://localhost:8080

rest() { # rest <method> <path> [json], as admin; Gerrit rejects an empty JSON body
    local data='{}'
    [ $# -lt 3 ] || data=$3
    curl -sS --fail-with-body -u admin:secret -X "$1" -H 'Content-Type: application/json' \
        -d "$data" "$URL/a$2" >&2
}

change_id() { git log -1 --format=%B | sed -n 's/^Change-Id: //p'; }

init() {
    local war=$WORK/gerrit/gerrit-$GERRIT_VERSION.war
    if [ ! -f "$war" ]; then
        mkdir -p "$WORK/gerrit"
        curl -sSfLo "$war.tmp" "https://gerrit-releases.storage.googleapis.com/gerrit-$GERRIT_VERSION.war"
        mv "$war.tmp" "$war"
    fi
    stop; "$(dirname "$0")/ide.sh" stop
    rm -rf "$SITE" "$WORK/projects"; mkdir -p "$SITE/etc"  # the IDE's clone belongs to the old site
    # SSH stays off: the sandbox has no ssh-keygen, which init needs for host
    # keys, and the plugin only talks HTTP.
    cat > "$SITE/etc/gerrit.config" <<EOF
[gerrit]
	canonicalWebUrl = $URL/
[auth]
	type = DEVELOPMENT_BECOME_ANY_ACCOUNT
[httpd]
	listenUrl = $URL/
[sshd]
	listenAddress = off
[sendemail]
	enable = false
[container]
	javaOptions = -Xmx1g
EOF
    java -jar "$war" init --batch --dev --no-auto-start -d "$SITE" > "$WORK/gerrit/init.log" 2>&1 ||
        { tail -20 "$WORK/gerrit/init.log" >&2; exit 1; }
}

seed() {
    # init --dev creates account 1000000 "admin"; the dev login hands out a
    # session cookie without a password, which is enough to set one.
    local jar=$SITE/cookies repo=$SITE/seed-demo
    exec 3>&2 2> "$WORK/gerrit/seed.log"
    trap 'tail -5 "$WORK/gerrit/seed.log" >&3' ERR
    curl -sSf -c "$jar" -b "$jar" -o /dev/null "$URL/login/?account_id=1000000"
    curl -sSf -c "$jar" -b "$jar" -o /dev/null "$URL/"
    curl -sSf -b "$jar" -H "X-Gerrit-Auth: $(awk '$6=="XSRF_TOKEN"{print $7}' "$jar")" \
        -H 'Content-Type: application/json' -X PUT -d '{"http_password":"secret"}' \
        -o /dev/null "$URL/accounts/self/password.http"

    rest PUT /projects/demo '{"create_empty_commit":true}'
    rest PUT /accounts/reviewer '{"name":"Rita Reviewer","email":"reviewer@example.com","http_password":"reviewer"}'

    git clone -q "http://admin:secret@localhost:8080/a/demo" "$repo"
    cd "$repo"
    git config user.name Administrator; git config user.email admin@example.com
    curl -sSfLo .git/hooks/commit-msg "$URL/tools/hooks/commit-msg"; chmod +x .git/hooks/commit-msg

    # admin may not push to master by default, so the base goes in as a change
    printf 'Demo\n' > README.md; git add README.md; git commit -qm 'Add README'
    git push -q origin HEAD:refs/for/master
    rest POST "/changes/demo~master~$(change_id)/revisions/current/review" '{"labels":{"Code-Review":2}}'
    rest POST "/changes/demo~master~$(change_id)/submit"

    printf 'hello\n' > hello.txt; git add hello.txt; git commit -qm 'Add hello'
    git push -q origin HEAD:refs/for/master
    printf 'hello\nworld\n' > hello.txt; git commit -qa --amend --no-edit
    git push -q origin HEAD:refs/for/master
    curl -sS --fail-with-body -u reviewer:reviewer -H 'Content-Type: application/json' \
        -d '{"message":"Looks fine","labels":{"Code-Review":1},
                  "comments":{"hello.txt":[{"line":2,"message":"Capitalise World?","unresolved":true}]}}' \
        "$URL/a/changes/demo~master~$(change_id)/revisions/current/review" >&2

    git reset -q --hard HEAD~1
    printf 'bye\n' > bye.txt; git add bye.txt; git commit -qm 'Add bye'
    git push -q origin HEAD:refs/for/master%r=reviewer
}

# After a container restart gerrit.pid may name an unrelated process, which
# Gerrit's own script would take for a running Gerrit and refuse to start.
running() { ps -o args= -p "$(cat "$SITE/logs/gerrit.pid" 2> /dev/null)" 2> /dev/null | grep -q GerritCodeReview; }

start() {
    # .ready only once init and seed both went through: a site that failed or was
    # killed half-way through either is set up from scratch on the next start
    local fresh=
    if ! running; then  # checked before init, which would wipe the site for nothing
        rm -f "$SITE/logs/gerrit.pid"
        if curl -sf -o /dev/null "$URL/config/server/version"; then
            echo "another server answers on $URL, stop it first" >&2; exit 1
        fi
    fi
    [ "$(cat "$SITE/.ready" 2> /dev/null)" = "$GERRIT_VERSION" ] || { init; fresh=1; }
    "$SITE/bin/gerrit.sh" start > "$WORK/gerrit/start.log" 2>&1 || true
    for _ in $(seq 180); do
        curl -sf -o /dev/null "$URL/config/server/version" && break
        running || break
        sleep 1
    done
    curl -sSf -o /dev/null "$URL/config/server/version" ||
        { tail -5 "$WORK/gerrit/start.log" "$SITE/logs/error_log" >&2; exit 1; }
    # last in the list, or set -e would not apply inside the subshell
    [ -z "$fresh" ] || { ( seed ); echo "$GERRIT_VERSION" > "$SITE/.ready"; }
    echo "Gerrit $GERRIT_VERSION on $URL"
}

stop() { ! running || "$SITE/bin/gerrit.sh" stop > /dev/null; }

case ${1:-} in
    start) start ;;
    stop) stop ;;
    *) echo "usage: $0 start|stop" >&2; exit 2 ;;
esac
