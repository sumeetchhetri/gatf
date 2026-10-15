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
# GATF installer — macOS (Intel / Apple Silicon) and Linux (x64 / arm64, glibc or musl).
#   online :  curl -fsSL https://github.com/sumeetchhetri/gatf/releases/latest/download/install.sh | bash
#   offline:  unzip gatf-<ver>.zip && ./gatf-<ver>/install.sh
# Per-user, no sudo. Needs Java 21+ — a JRE is enough (tests compile with the bundled ECJ); only the
# online debugger needs a JDK (jdk.jdi + jdk.jdwp.agent). Detects installed Java (preferring a JDK), else
# offers a private Temurin 21 download (JDK by default, --jre for the smaller runtime).
# Options: --version X | --zip FILE | --jdk/--java DIR | --jre | --yes | --with-tools wperfk,vegeta,autocannon
#          --no-tools | --uninstall [--purge]
# bash 3.2 compatible (stock macOS bash).
set -eu

REPO_RELEASES="${GATF_RELEASE_BASE:-https://github.com/sumeetchhetri/gatf/releases}"
ADOPTIUM_API="${GATF_ADOPTIUM_API:-https://api.adoptium.net/v3}"
PREFIX="${GATF_HOME:-$HOME/.gatf}"
BIN_DIR="${GATF_BIN_DIR:-$HOME/.local/bin}"
MIN_JAVA=21

VERSION="latest"; ZIP=""; JDK=""; JRE=0; YES=0; TOOLS=""; NO_TOOLS=0; UNINSTALL=0; PURGE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --zip) ZIP="$2"; shift 2 ;;
    --jdk|--java) JDK="$2"; shift 2 ;;
    --jre) JRE=1; shift ;;
    --yes|-y) YES=1; shift ;;
    --with-tools) TOOLS="$2"; shift 2 ;;
    --no-tools) NO_TOOLS=1; shift ;;
    --uninstall) UNINSTALL=1; shift ;;
    --purge) PURGE=1; shift ;;
    -h|--help) sed -n '16,25p' "$0" 2>/dev/null || true; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

say()  { printf '%s\n' "$*"; }
say_err() { printf '%s\n' "$*" >&2; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# yes/no prompt on the terminal even when this script is piped into bash
ask() {
  [ "$YES" = 1 ] && return 0
  if [ -r /dev/tty ] && [ -w /dev/tty ]; then
    printf '%s [y/N] ' "$1" >/dev/tty
    a=""; read -r a </dev/tty || a=""
    case "$a" in y|Y|yes|YES) return 0 ;; esac
  fi
  return 1
}
ask_line() {
  a=""
  if [ -r /dev/tty ] && [ -w /dev/tty ]; then printf '%s ' "$1" >/dev/tty; read -r a </dev/tty || a=""; fi
  printf '%s' "$a"
}

fetch() { # url -> file
  if command -v curl >/dev/null 2>&1; then curl -fsSL --retry 2 -o "$2" "$1"
  elif command -v wget >/dev/null 2>&1; then wget -q -O "$2" "$1"
  else die "need curl or wget"; fi
}
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1
  else shasum -a 256 "$1" | cut -d' ' -f1; fi
}
unzip_to() { # zip dir
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$1" -d "$2"
  elif [ "$(uname -s)" = Darwin ]; then ditto -x -k "$1" "$2"
  elif command -v python3 >/dev/null 2>&1; then python3 -m zipfile -e "$1" "$2"
  else die "need unzip (or python3) to extract $1"; fi
}

# ---------------------------------------------------------------- uninstall
if [ "$UNINSTALL" = 1 ]; then
  [ -L "$BIN_DIR/gatf" ] && rm -f "$BIN_DIR/gatf"
  if [ "$PURGE" = 1 ]; then rm -rf "$PREFIX"; say "Removed $PREFIX (GATF, private JDK, tool cache)."
  else rm -rf "$PREFIX/versions" "$PREFIX/current" "$PREFIX/gatf.conf"; say "Removed GATF (kept $PREFIX/jdk and $PREFIX/tools; --purge removes all)."; fi
  exit 0
fi

# ---------------------------------------------------------------- platform
OS="$(uname -s)"; ARCH="$(uname -m)"
case "$OS" in Darwin) AOS=mac ;; Linux) AOS=linux ;; *) die "unsupported OS $OS (use install.ps1 on Windows)" ;; esac
if [ "$OS" = Darwin ] && [ "$(sysctl -in sysctl.proc_translated 2>/dev/null || echo 0)" = 1 ]; then
  warn "this shell runs under Rosetta; installing for the native Apple Silicon (arm64) architecture"
  ARCH=arm64
fi
case "$ARCH" in x86_64|amd64) AARCH=x64 ;; arm64|aarch64) AARCH=aarch64 ;; *) die "unsupported CPU $ARCH" ;; esac
if [ "$AOS" = linux ] && { [ -f /etc/alpine-release ] || ldd --version 2>&1 | grep -qi musl; }; then AOS=alpine-linux; fi

# ---------------------------------------------------------------- JDK detection
java_major() { # java binary -> major version (or empty)
  "$1" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2; exit}' | cut -d. -f1
}
jdk_ok() { # JAVA_HOME dir -> 0 if Java >= 21 (JRE is fine) and native arch on macOS
  j="$1/bin/java"
  [ -x "$j" ] || return 1
  m="$(java_major "$j")"
  [ -n "$m" ] && [ "$m" -ge "$MIN_JAVA" ] 2>/dev/null || return 1
  if [ "$OS" = Darwin ] && [ "$AARCH" = aarch64 ]; then
    ja="$("$j" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/os.arch/ {print $2; exit}')"
    if [ "$ja" = x86_64 ] || [ "$ja" = amd64 ]; then warn "skipping x86_64 JDK at $1 (would run under Rosetta)"; return 1; fi
  fi
  return 0
}
has_debugger() { # the online debugger needs these JDK-only modules
  mods="$("$1/bin/java" --list-modules 2>/dev/null)"
  printf '%s\n' "$mods" | grep -q '^jdk.jdi@' && printf '%s\n' "$mods" | grep -q '^jdk.jdwp.agent@'
}
home_of() { # path to a java binary -> its JAVA_HOME (follows symlinks, portable)
  p="$1"
  # shellcheck disable=SC2012  # portable symlink read (macOS has no readlink -f)
  while [ -h "$p" ]; do l="$(ls -ld "$p" | sed 's/.* -> //')"; case "$l" in /*) p="$l" ;; *) p="$(dirname "$p")/$l" ;; esac; done
  (cd "$(dirname "$p")/.." 2>/dev/null && pwd)
}
candidates() {
  if [ "${GATF_NO_SYSTEM_JDK:-0}" = 1 ]; then   # installer self-test hook: only the private JDK dir
    for d in "$PREFIX"/jdk/*/Contents/Home "$PREFIX"/jdk/*; do [ -d "$d" ] && echo "$d"; done
    return 0
  fi
  [ -n "${GATF_JAVA_HOME:-}" ] && echo "$GATF_JAVA_HOME"
  [ -n "${JAVA_HOME:-}" ] && echo "$JAVA_HOME"
  if [ "$OS" = Darwin ] && [ -x /usr/libexec/java_home ]; then /usr/libexec/java_home -v "$MIN_JAVA+" 2>/dev/null || true; fi
  if command -v java >/dev/null 2>&1; then home_of "$(command -v java)" || true; fi
  for d in "$PREFIX"/jdk/*/Contents/Home "$PREFIX"/jdk/* /Library/Java/JavaVirtualMachines/*/Contents/Home \
           "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home /usr/lib/jvm/* /usr/java/* /opt/java/* \
           "$HOME"/.sdkman/candidates/java/*; do
    [ -d "$d" ] && echo "$d"
  done
  return 0
}
find_jdk() { # prefer a runtime that can also run the debugger, else any Java 21+
  best="$(candidates | while IFS= read -r c; do [ -n "$c" ] && jdk_ok "$c" && has_debugger "$c" && { echo "$c"; break; }; done)"
  [ -n "$best" ] && { echo "$best"; return; }
  candidates | while IFS= read -r c; do [ -n "$c" ] && jdk_ok "$c" && { echo "$c"; break; }; done
}

download_jdk() {
  meta="$(mktemp)"
  say_err "Fetching Temurin $MIN_JAVA ($AOS/$AARCH) metadata from Adoptium..."
  IMG=jdk; [ "$JRE" = 1 ] && IMG=jre
  fetch "$ADOPTIUM_API/assets/latest/$MIN_JAVA/hotspot?os=$AOS&architecture=$AARCH&image_type=$IMG&vendor=eclipse" "$meta" || die "Adoptium API unreachable"
  # the archive lives under binary.package (binary.installer is the .pkg/.msi) — flatten, then cut that object
  pkg="$(tr -d '\n\r' < "$meta" | sed -n 's/.*"package"[[:space:]]*:[[:space:]]*{\([^}]*\)}.*/\1/p')"
  rm -f "$meta"
  link="$(printf '%s' "$pkg" | grep -o '"link"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')"
  sum="$(printf '%s' "$pkg" | grep -o '"checksum"[[:space:]]*:[[:space:]]*"[a-fA-F0-9]\{64\}"' | head -1 | grep -o '[a-fA-F0-9]\{64\}')"
  [ -n "$link" ] && [ -n "$sum" ] || die "could not find a Temurin $MIN_JAVA $IMG for $AOS/$AARCH"
  mkdir -p "$PREFIX/jdk"
  arc="$PREFIX/jdk/.download.tar.gz"
  say_err "Downloading $link"
  fetch "$link" "$arc"
  [ "$(sha256_of "$arc")" = "$(printf '%s' "$sum" | tr 'A-F' 'a-f')" ] || { rm -f "$arc"; die "Java download checksum mismatch"; }
  top="$(tar tzf "$arc" | head -1 | cut -d/ -f1)"
  tar xzf "$arc" -C "$PREFIX/jdk" && rm -f "$arc"
  h="$PREFIX/jdk/$top"; [ -d "$h/Contents/Home" ] && h="$h/Contents/Home"
  jdk_ok "$h" || die "downloaded Java at $h failed the checks"
  echo "$h"
}

# ---------------------------------------------------------------- locate the GATF payload
SCRIPT_DIR=""
case "${BASH_SOURCE:-$0}" in */*) SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE:-$0}")" && pwd)" ;; esac
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
SRC=""
if [ -n "$ZIP" ]; then
  [ -f "$ZIP" ] || die "no such zip: $ZIP"
  unzip_to "$ZIP" "$WORK"
elif [ -n "$SCRIPT_DIR" ] && [ -f "$SCRIPT_DIR/lib/gatf.jar" ]; then
  SRC="$SCRIPT_DIR"                                      # running from an extracted zip (offline)
else
  if [ "$VERSION" = latest ]; then base="$REPO_RELEASES/latest/download"; else base="$REPO_RELEASES/download/$VERSION"; fi
  say "Downloading GATF ($VERSION)..."
  fetch "$base/gatf.zip" "$WORK/gatf.zip"
  fetch "$base/gatf.zip.sha256" "$WORK/gatf.zip.sha256"
  [ "$(sha256_of "$WORK/gatf.zip")" = "$(cut -d' ' -f1 < "$WORK/gatf.zip.sha256")" ] || die "gatf.zip checksum mismatch"
  unzip_to "$WORK/gatf.zip" "$WORK"
fi
if [ -z "$SRC" ]; then
  for d in "$WORK"/gatf-* "$WORK"; do [ -f "$d/lib/gatf.jar" ] && { SRC="$d"; break; }; done
fi
[ -n "$SRC" ] || die "payload not found (expected lib/gatf.jar)"
VER="$(cat "$SRC/VERSION" 2>/dev/null || echo unknown)"

# ---------------------------------------------------------------- JDK
if [ -n "$JDK" ]; then
  jdk_ok "$JDK" || die "--jdk $JDK is not a Java $MIN_JAVA+ runtime"
  JH="$JDK"
else
  JH="$(find_jdk || true)"
fi
if [ -z "$JH" ]; then
  say "No Java $MIN_JAVA+ found."
  if [ -n "$ZIP" ] || [ "$SRC" = "$SCRIPT_DIR" ]; then
    p="$(ask_line "Path to a Java $MIN_JAVA+ home (JAVA_HOME), or empty to download Temurin $MIN_JAVA:")"
    if [ -n "$p" ]; then jdk_ok "$p" || die "$p is not a Java $MIN_JAVA+ runtime"; JH="$p"; fi
  fi
  if [ -z "$JH" ]; then
    what="JDK (includes the online debugger)"; [ "$JRE" = 1 ] && what="JRE (smaller; no online debugger)"
    ask "Download Eclipse Temurin $MIN_JAVA $what into $PREFIX/jdk (private, no system changes)?" || die "Java $MIN_JAVA+ is required — rerun with --jdk <dir> or --yes"
    JH="$(download_jdk)"
  fi
fi
say "Using Java: $JH"
has_debugger "$JH" || warn "this Java has no jdk.jdi/jdk.jdwp.agent: everything works except the online debugger (use a JDK via --jdk DIR for that)"

# ---------------------------------------------------------------- install
DEST="$PREFIX/versions/$VER"
rm -rf "$DEST"; mkdir -p "$DEST"
cp -R "$SRC/lib" "$SRC/bin" "$DEST/"
cp "$SRC/VERSION" "$DEST/" 2>/dev/null || echo "$VER" > "$DEST/VERSION"
[ -f "$SRC/LICENSE" ] && cp "$SRC/LICENSE" "$DEST/"
chmod +x "$DEST/bin/gatf"
ln -sfn "$DEST" "$PREFIX/current"
q="$(printf '%s' "$JH" | sed "s/'/'\\\\''/g")"
printf "GATF_JAVA_HOME='%s'\n" "$q" > "$PREFIX/gatf.conf"
mkdir -p "$BIN_DIR"
ln -sf "$PREFIX/current/bin/gatf" "$BIN_DIR/gatf"
say "Installed GATF $VER → $DEST  (launcher: $BIN_DIR/gatf)"

case ":$PATH:" in
  *":$BIN_DIR:"*) ;;
  *)
    case "${SHELL:-}" in */zsh) rc="$HOME/.zshrc" ;; */bash) if [ "$OS" = Darwin ]; then rc="$HOME/.bash_profile"; else rc="$HOME/.bashrc"; fi ;; *) rc="$HOME/.profile" ;; esac
    if ! grep -qs "GATF launcher" "$rc"; then
      # shellcheck disable=SC2016  # $PATH is written literally into the rc file
      printf '\n# GATF launcher\nexport PATH="%s:$PATH"\n' "$BIN_DIR" >> "$rc"
      say "Added $BIN_DIR to PATH in $rc — open a new terminal (or: export PATH=\"$BIN_DIR:\$PATH\")"
    fi ;;
esac

# ---------------------------------------------------------------- optional load tools
if [ "$NO_TOOLS" = 0 ]; then
  if [ -z "$TOOLS" ] && ask "Also set up the load-test tools (wperfk, the default engine; vegeta; and autocannon with a private Node.js)?"; then TOOLS="wperfk,vegeta,autocannon"; fi
  if [ -n "$TOOLS" ]; then
    # shellcheck disable=SC2046
    "$BIN_DIR/gatf" tools install $(printf '%s' "$TOOLS" | tr ',' ' ') || warn "tool setup failed — they will auto-provision on first use"
  fi
fi
say "Done. Try:  gatf --version   |   gatf ui   (then open http://127.0.0.1:9080/)"
