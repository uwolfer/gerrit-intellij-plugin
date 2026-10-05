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
# Screenshot of the virtual display, small enough to read back as an image.
# Usage: shot.sh <name> [crop WxH+X+Y]   -> prints the path of the PNG
set -euo pipefail
WORK=${RUN_IDE_WORK:-${XDG_CACHE_HOME:-$HOME/.cache}/gerrit-plugin-run-ide}
SHOTS=${RUN_IDE_SHOTS:-$WORK/shots}
mkdir -p "$SHOTS"
out=$SHOTS/${1:?usage: shot.sh <name> [WxH+X+Y]}.png
DISPLAY=${DISPLAY:-:99} import -window root ${2:+-crop "$2" +repage} "$out"
echo "$out"
