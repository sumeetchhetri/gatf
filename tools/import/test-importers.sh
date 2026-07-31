#!/usr/bin/env bash
# Self-contained check for the Phase 11 importers. Regenerates from the fixtures,
# validates the output (seleasy via sel-lint, JMeter output as well-formed GATF
# XML), and asserts non-empty conversions. No JDK / Maven / browser needed.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "== Playwright -> seleasy =="
python3 "$here/pw-import.py" "$here/fixtures/login.spec.ts" -o "$tmp/login"
python3 "$root/tools/smoke/sel-lint.py" --strict "$tmp/login.sel"
test -s "$tmp/login.sel"
grep -q '^click ' "$tmp/login.sel"
grep -q '^type "' "$tmp/login.sel"
grep -q '^goto ' "$tmp/login.sel"

echo "== JMeter -> GATF =="
python3 "$here/jmx-import.py" "$here/fixtures/sample-plan.jmx" -o "$tmp/plan"
python3 - "$tmp/plan.xml" "$tmp/plan.gatf-config.xml" <<'PY'
import sys, xml.dom.minidom
for f in sys.argv[1:]:
    xml.dom.minidom.parse(f)
print("xml well-formed:", *[f.split('/')[-1] for f in sys.argv[1:]])
PY
grep -q '<TestCase>' "$tmp/plan.xml"
grep -q '<method>POST</method>' "$tmp/plan.xml"
grep -q 'loadTestingEnabled>true' "$tmp/plan.gatf-config.xml"

echo "ALL IMPORTER CHECKS PASSED"
