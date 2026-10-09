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
# Starts the plugin in an IDE on a virtual display, with the seeded demo project open.
# Without an argument it runs the SDK the plugin compiles against, from Gradle's cache;
# `latest` downloads latestIdeaVersion from gradle.properties, which CI verifies against.
# Usage: ide.sh start [latest|<unpacked IDE dir>] | stop | errors
set -euo pipefail

WORK=${RUN_IDE_WORK:-${XDG_CACHE_HOME:-$HOME/.cache}/gerrit-plugin-run-ide}
REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
HERE=$(cd "$(dirname "$0")" && pwd)
export DISPLAY=${DISPLAY:-:99}
mkdir -p "$WORK"

sdk() {
    local v
    v=$(sed -n 's/^ideaVersion=//p' "$REPO/gradle.properties")  # e.g. IC-2020.3.4
    ls -d "${GRADLE_USER_HOME:-$HOME/.gradle}"/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/idea"${v%%-*}"/"${v#*-}"/*/idea"$v" | head -1
}

cached() { # cached <dir> <url> tar|zip: unpacked aside, so a cut-off download is not taken for the real one
    local dir=$1
    [ ! -d "$dir" ] || return 0
    rm -rf "$dir.tmp"; mkdir -p "$dir.tmp"
    local get=(curl -sSfL --connect-timeout 20 --speed-limit 1000 --speed-time 60 "$2")  # gives up on a stall
    if [ "$3" = zip ]; then
        command -v unzip > /dev/null || { echo "unzip is needed: apt-get install unzip" >&2; return 1; }
        "${get[@]}" -o "$dir.tmp/download.zip" && unzip -q "$dir.tmp/download.zip" -d "$dir.tmp" &&
            rm "$dir.tmp/download.zip" || return 1
    else
        "${get[@]}" | tar xz -C "$dir.tmp" || return 1
    fi
    mv "$dir.tmp" "$dir"
}

latest() {
    local v
    v=$(sed -n 's/^latestIdeaVersion=\([A-Z]*-\)\{0,1\}//p' "$REPO/gradle.properties")  # e.g. IU-2026.2.3
    cached "$WORK/ides/$v" "https://download.jetbrains.com/idea/idea-$v.tar.gz" tar || return 1
    ls -d "$WORK/ides/$v"/idea-* | head -1
}

jbr() { # the SDK comes without the runtime it was released with, and needs that one
    local ide=$1 name
    [ -d "$ide/jbr" ] && { echo "$ide/jbr"; return; }
    name=$(sed -n 's/^jdkBuild=\(.*\)b\(.*\)$/jbr-\1-linux-x64-b\2/p' "$ide/dependencies.txt")
    [ -n "$name" ] || { echo "$ide has no jbr and names none in dependencies.txt" >&2; return 1; }
    cached "$WORK/$name" "https://cache-redirector.jetbrains.com/intellij-jbr/$name.tar.gz" tar || return 1
    echo "$WORK/$name/jbr"
}

robot() { # JetBrains' Remote Robot server plugin, which serves the IDE's Swing tree over HTTP
    local v=0.11.23
    cached "$WORK/robot-server-$v" \
        "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/com/intellij/remoterobot/robot-server-plugin/$v/robot-server-plugin-$v.zip" \
        zip || return 1
    echo "$WORK/robot-server-$v/robot-server-plugin"
}

display() {
    # missing from a fresh sandbox, and not worth installing in every session which never runs an IDE
    if ! command -v xdotool > /dev/null || ! command -v import > /dev/null; then
        [ "$(id -u)" = 0 ] && command -v apt-get > /dev/null &&
            { apt-get install -y -q xdotool imagemagick > "$WORK/apt.log" 2>&1 ||
              { apt-get update -q > /dev/null 2>&1 && apt-get install -y -q xdotool imagemagick >> "$WORK/apt.log" 2>&1; }; }
        command -v xdotool > /dev/null && command -v import > /dev/null ||
            { echo "xdotool and import are needed: apt-get install xdotool imagemagick" >&2; exit 1; }
    fi
    xdotool getdisplaygeometry > /dev/null 2>&1 && return
    # An Xvfb that died, e.g. with the container, leaves its lock behind; after a
    # container restart the pid in it may belong to something else.
    local n=${DISPLAY#:}; n=${n%%.*}
    case $(ps -o comm= -p "$(tr -d ' ' 2> /dev/null < "/tmp/.X$n-lock")" 2> /dev/null) in
        Xvfb | Xorg | X) ;;
        *) rm -f "/tmp/.X$n-lock" "/tmp/.X11-unix/X$n" ;;
    esac
    setsid Xvfb "$DISPLAY" -screen 0 1600x1000x24 -nolisten tcp > "$WORK/xvfb.log" 2>&1 &
    for _ in $(seq 20); do xdotool getdisplaygeometry > /dev/null 2>&1 && return; sleep 0.5; done
    echo "Xvfb did not come up, see $WORK/xvfb.log" >&2; exit 1
}

project() {
    local dir=$WORK/projects/demo tmp=$WORK/projects/demo.tmp
    [ -d "$dir" ] && return
    rm -rf "$tmp"  # set up aside, so a step that failed is retried on the next start
    git clone -q http://localhost:8080/demo "$tmp"
    curl -sSfLo "$tmp/.git/hooks/commit-msg" http://localhost:8080/tools/hooks/commit-msg
    chmod +x "$tmp/.git/hooks/commit-msg"
    git -C "$tmp" config user.name Administrator
    git -C "$tmp" config user.email admin@example.com
    echo .idea >> "$tmp/.git/info/exclude"
    # a push from the IDE would otherwise stop at a credentials prompt
    echo 'http://admin:secret@localhost:8080' > "$WORK/git-credentials"
    git -C "$tmp" config credential.helper "store --file=$WORK/git-credentials"
    mv "$tmp" "$dir"
}

sandbox() {
    local ide=$1 dir=$2
    mkdir -p "$dir/config/options"
    # a copy of the plugin, so that IDEs do not write into Gradle's sandbox nor
    # find their jars rewritten by the next build
    rm -rf "$dir/plugins"; mkdir -p "$dir/plugins"
    cp -r "$REPO/build/idea-sandbox/plugins/gerrit-intellij-plugin" "$dir/plugins/"
    # without it the IDE still runs, for screenshots and xdotool, where packages.jetbrains.team is blocked
    local server
    if ! server=$(robot) || ! cp -r "$server" "$dir/plugins/"; then
        rm -rf "$dir/plugins/robot-server-plugin"; echo "no Remote Robot: robot.py will not work" >&2
    fi
    printf 'idea.%s.path=%s\n' config "$dir/config" system "$dir/system" log "$dir/system/log" \
        plugins "$dir/plugins" > "$dir/idea.properties"
    # newer launchers add this file to the IDE's own, 2020.3 reads only this one
    if grep -q USER_VM_OPTIONS_FILE "$ide/bin/idea.sh"; then
        cp "$HERE/ide.vmoptions" "$dir/idea.vmoptions"
    else
        # the SDK from Maven keeps its defaults under bin/linux, an unpacked IDE under bin
        { cat "$(ls "$ide"/bin/idea64.vmoptions "$ide"/bin/linux/idea64.vmoptions 2> /dev/null | head -1)"
            echo; cat "$HERE/ide.vmoptions"; } > "$dir/idea.vmoptions"
    fi
    # There is no keyring service in the sandbox: the default provider fails to load
    # libsecret and logs an error which names whichever plugin stored the password.
    cat > "$dir/config/options/security.xml" <<'EOF'
<application>
    <component name="PasswordSafe">
        <option name="PROVIDER" value="KEEPASS" />
    </component>
</application>
EOF
    # The IDE picks its proxy from its own settings, not from the JVM's proxy properties, so
    # HttpRequests (avatars, plugin downloads) would go out directly and be refused where only
    # the sandbox's proxy gets out. Written on every start: the proxy's port changes between sessions.
    local proxy=${HTTPS_PROXY:-${https_proxy:-}} host port
    proxy=${proxy#*://}; proxy=${proxy%%/*}; proxy=${proxy##*@}
    host=${proxy%:*} port=${proxy##*:}
    if [ -n "$proxy" ] && [ "$host" != "$proxy" ]; then
        cat > "$dir/config/options/proxy.settings.xml" <<EOF
<application>
    <component name="HttpConfigurable">
        <option name="USE_HTTP_PROXY" value="true" />
        <option name="PROXY_HOST" value="$host" />
        <option name="PROXY_PORT" value="$port" />
        <option name="PROXY_EXCEPTIONS" value="localhost,127.0.0.1" />
    </component>
</application>
EOF
    else
        rm -f "$dir/config/options/proxy.settings.xml"
    fi
}

robot_up() { curl -sf --noproxy '*' --max-time 2 -o /dev/null http://127.0.0.1:8082/hello; }
frame() { xdotool search --name '^demo' > /dev/null; }

start() {
    curl -sf -o /dev/null http://localhost:8080/config/server/version ||
        { echo "Gerrit is not running: gerrit.sh start first" >&2; exit 1; }
    local ide=$1 dir runtime log pid
    [ "$ide" != latest ] || ide=$(latest)
    [ -z "$ide" ] || [ -x "$ide/bin/idea.sh" ] || { echo "no IDE at $ide" >&2; exit 1; }
    stop  # before its sandbox is rewritten
    ! robot_up || { echo "127.0.0.1:8082 is taken, by an IDE ide.sh does not know of?" >&2; exit 1; }
    ( cd "$REPO" && ./gradlew prepareSandbox > "$WORK/gradle.log" 2>&1 ) || { tail -20 "$WORK/gradle.log" >&2; exit 1; }
    ide=${ide:-$(sdk)}  # Gradle downloads the SDK on the first run
    ide=$(cd "$ide" && pwd)
    dir=$WORK/sandbox-$(basename "$ide")
    runtime=$(jbr "$ide")
    rm -f "$dir/system/log/idea.log"  # the IDE appends to it; a previous start would read as this one
    display; project; sandbox "$ide" "$dir"
    # project files written by another IDE version are not this one's to read
    [ "$(cat "$WORK/ide.sandbox" 2> /dev/null)" = "$dir" ] || rm -rf "$WORK/projects/demo/.idea"
    echo "$dir" > "$WORK/ide.sandbox"
    # its own process group, as 2020.3's idea.sh does not exec the JVM
    IDEA_JDK=$runtime IDEA_PROPERTIES=$dir/idea.properties IDEA_VM_OPTIONS=$dir/idea.vmoptions \
        setsid "$ide/bin/idea.sh" "$WORK/projects/demo" > "$dir/stdout.log" 2>&1 &
    pid=$! log=$dir/system/log/idea.log
    echo "$pid" > "$WORK/ide.pid"
    wait_for() { # wait_for <seconds> <command...>, giving up early once the IDE is gone
        local n=$1; shift
        for _ in $(seq "$n"); do "$@" && return; kill -0 "$pid" 2> /dev/null || return 1; sleep 1; done
        return 1
    }
    if wait_for 300 grep -qs "Loaded bundled plugins" "$log"; then
        # the custom plugins are logged right after the bundled ones
        wait_for 10 grep -qs "Loaded custom plugins: .*Gerrit" "$log" ||
            { errors >&2; stop; echo "the IDE did not load the plugin" >&2; exit 1; }
        # 2020.3 shows the frame while its untitled "Loading project" dialog is still up for a few seconds
        if wait_for 300 frame; then
            # no window manager places it: pinned to the display, so every component is on screen
            for w in $(xdotool search --name '^demo'); do  # a loading window can be gone by now
                xdotool windowsize "$w" 1600 1000 windowmove "$w" 0 0 2> /dev/null || true
            done
            [ ! -d "$dir/plugins/robot-server-plugin" ] || wait_for 60 robot_up ||
                echo "the robot server did not answer on 127.0.0.1:8082: robot.py will not work" >&2
            sleep 5
            kill -0 "$pid" 2> /dev/null && { echo "IDE up: log $log"; return; }
        fi
    fi
    tail -20 "$dir/stdout.log" >&2; stop
    echo "IDE did not come up, see $log" >&2; exit 1
}

stop() {
    local pid
    pid=$(cat "$WORK/ide.pid" 2> /dev/null) || return 0
    rm -f "$WORK/ide.pid"
    # a stale file must not take down whatever process group reuses the number
    pgrep -g "$pid" -f 'com\.intellij\.idea\.Main' > /dev/null || return 0
    kill -TERM -- -"$pid" 2> /dev/null || true  # SIGTERM lets the IDE save its state
    for _ in $(seq 30); do kill -0 -- -"$pid" 2> /dev/null || break; sleep 1; done
    kill -KILL -- -"$pid" 2> /dev/null || true
}

errors() { # every error with the plugin it blames, and every warning about this plugin
    # The plugin's logger category reads #c.u.i.p.g.… on new IDEs, while 2020.3 keeps
    # only its last 30 characters, so those are matched per class.
    local log
    [ -f "$WORK/ide.sandbox" ] || return 0
    log=$(cat "$WORK/ide.sandbox")/system/log/idea.log
    { echo ' (ERROR|SEVERE) - | WARN - +#c\.u\.i\.p\.g\.| WARN - .*(urswolfer|javassist)'
        find "$REPO/src/main/java" -name '*.java' |
            sed 's|.*/java/||; s|\.java$||; s|/|.|g; s|.*\(.\{30\}\)$|\1|; s|\.|\\.|g; s|^| WARN - |; s|$| - |'
    } | grep -E -f - "$log" | grep -vE ' - (IntelliJ IDEA .* Build #|JDK: |OS: |Last Action: )' || true
}

case ${1:-} in
    start) start "${2:-}" ;;
    stop) stop ;;
    errors) errors ;;
    *) echo "usage: $0 start [latest|<ide dir>] | stop | errors" >&2; exit 2 ;;
esac
