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
# Build the distributable:  dist/build-dist.sh <gatf-alldep-jar> <version> [outdir]
# Produces in outdir: gatf-<ver>.zip, gatf.zip (same bytes, stable name for /releases/latest/download),
# their .sha256 files, and install.sh / install.ps1 (uploaded standalone for the one-line installers).
set -euo pipefail
jar="$1"; ver="$2"; out="${3:-target-dist}"
root="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$jar" ] || { echo "no jar: $jar" >&2; exit 1; }
rm -rf "$out"; stage="$out/gatf-$ver"; mkdir -p "$stage/lib" "$stage/bin"
cp "$jar" "$stage/lib/gatf.jar"
cp "$root/dist/bin/gatf" "$root/dist/bin/gatf.cmd" "$stage/bin/"
cp "$root/dist/install.sh" "$root/dist/install.ps1" "$stage/"
chmod +x "$stage/bin/gatf" "$stage/install.sh"
cp "$root/LICENSE" "$stage/"
printf '%s\n' "$ver" > "$stage/VERSION"
(
  cd "$out"
  zip -qrX "gatf-$ver.zip" "gatf-$ver"
  cp "gatf-$ver.zip" gatf.zip
  for f in "gatf-$ver.zip" gatf.zip; do
    if command -v sha256sum >/dev/null; then sha256sum "$f" > "$f.sha256"; else shasum -a 256 "$f" > "$f.sha256"; fi
  done
  cp "$root/dist/install.sh" "$root/dist/install.ps1" .
)
echo "built $out/gatf-$ver.zip"
