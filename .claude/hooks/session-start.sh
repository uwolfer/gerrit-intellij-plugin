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
# SessionStart hook for Claude Code on the web. It installs the init script which points Gradle at Google's
# Maven Central mirror (see AGENTS.md), as long as the mirror answers. Maven Central itself is not asked: it
# answers 429 and 200 within seconds of each other, so no answer at session start says what it will answer later.
set -euo pipefail

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

HERE=$(cd "$(dirname "$0")" && pwd)
INIT_DIR=${GRADLE_USER_HOME:-$HOME/.gradle}/init.d
INIT=$INIT_DIR/maven-mirror.gradle
MIRROR=https://maven-central.storage-download.googleapis.com/maven2
PROBE=junit/junit/maven-metadata.xml

# one flaky answer must not decide: it would remove a copy which works, or leave Gradle without one
for attempt in 1 2 3; do
    code=$(curl -sS -o /dev/null --connect-timeout 5 -m 10 -w '%{http_code}' "$MIRROR/$PROBE" 2> /dev/null || true)
    [ "$code" = 200 ] && break
    [ "$attempt" = 3 ] || sleep 2
done
code=${code:-none}
if [ "$code" != 200 ]; then
    # a copy from an earlier session would leave Gradle with a dead mirror and no Maven Central
    rm -f "$INIT"
    echo "The Maven Central mirror answers $code: no mirror installed" >&2
    exit 0
fi

mkdir -p "$INIT_DIR"
cp -f "$HERE/maven-mirror.init.gradle" "$INIT"
echo "Gradle uses the Maven Central mirror through $INIT" >&2
