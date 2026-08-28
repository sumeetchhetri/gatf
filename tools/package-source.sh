#!/usr/bin/env bash
#    Copyright 2013-2026, Sumeet Chhetri
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
# package-source.sh — snapshot the GATF source tree into a zip for handing to a fresh session.
#
# Excludes build/output/VCS cruft (node_modules, Maven target/, Vite dist/, .git, IDE + OS files,
# generated poms, runtime control/report files, and previously packaged delta/src zips) so the
# archive is just source + docs (docs/deltas/*, sample-project/, tools/, frontend/src, Java, poms).
#
# Usage:
#   tools/package-source.sh                 # -> <repo-parent>/gatf-src-YYYYMMDD-HHMM.zip
#   tools/package-source.sh /path/out.zip   # explicit output path
#
# Runnable from anywhere: the repo root is resolved from this script's location (tools/..).

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

TS="$(date +%Y%m%d-%H%M)"
OUT_ARG="${1:-$REPO_ROOT/../gatf-src-$TS.zip}"
OUT_DIR="$(cd -- "$(dirname -- "$OUT_ARG")" && pwd)"
OUT="$OUT_DIR/$(basename -- "$OUT_ARG")"

command -v zip >/dev/null 2>&1 || { echo "error: 'zip' is not installed" >&2; exit 1; }

# Exclusions. In zip's -x globs, '*' matches across '/', so '*<name>/*' catches that dir at any depth
# (top-level included). Keep each dir pattern on its own line for readability.
EXCLUDES=(
  '*node_modules/*'                 # frontend deps (npm ci re-creates)
  '*target/*' 'artifacts/*'                       # Maven build output
  '*.mvn-node/*'                    # frontend-maven-plugin's node install (frontend/.mvn-node)
  '*/dist/*' 'dist/*'               # Vite build output
  '.git/*' '*/.git/*'               # git history
  '*/deltas/*' 'deltas/*'           # delta changelogs / packaged deltas
  'sample-project/out/*' 'sample-project/app/*'   # sample run output + extracted SPA
  'out/*' '*/out/*'                 # any other report/run output dir
  '*/.settings/*' '*/.idea/*' '*.iml' '*/.vscode/*'   # IDE metadata
  '*.class' '*.log'                 # compiled / log artifacts
  '*.DS_Store' '*/Thumbs.db'        # OS cruft
  'dependency-reduced-pom.xml'      # shade-plugin generated pom
  'gatf.ctrl'                       # runtime server control file
  'gatf-src-*.zip' 'gatf-delta-*.zip'   # don't nest previously packaged zips
)

XARGS=()
for p in "${EXCLUDES[@]}"; do XARGS+=( -x "$p" ); done

echo "Repo root : $REPO_ROOT"
echo "Output    : $OUT"
rm -f "$OUT"

( cd -- "$REPO_ROOT" && zip -r -q -X "$OUT" . "${XARGS[@]}" )

# Self-check: fail loudly if any excluded heavyweight slipped in.
LEAK="$(unzip -Z1 "$OUT" | grep -E '(^|/)(node_modules|target|dist|\.mvn-node|deltas)/|(^|/)\.git/|^sample-project/(out|app)/' || true)"
if [ -n "$LEAK" ]; then
  echo "WARNING: excluded paths leaked into the archive:" >&2
  echo "$LEAK" | head >&2
  exit 2
fi

COUNT="$(unzip -Z1 "$OUT" | wc -l | tr -d ' ')"
SIZE="$(du -h "$OUT" | cut -f1)"
echo
echo "OK — $COUNT entries, $SIZE"
echo "Hand this to a new session:  $OUT"
