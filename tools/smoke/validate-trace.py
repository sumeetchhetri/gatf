#!/usr/bin/env python3
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
"""Validate a GATF trace.zip produced by an end-to-end run.

Finds the newest ``*-trace.zip`` under the given output dir, reads it, and asserts
the bundle is well-formed: metadata, network HAR (parses as HAR 1.2), the console
and event streams -- including at least one *per-action* timeline event (the
codegen-free action hook) -- snapshots, and, when metadata says they ran, the
performance/memory, cpu, coverage and video artifacts.

This is the check that turns "compiles / looks right" into "the recorder pipeline
actually works against a real browser". It needs a real run to have happened; the
dev container has no browser, so run it after ``trace-e2e.sh`` on a box with a Grid.

Usage:  validate-trace.py <output-dir>          # defaults to '.'
Exit non-zero on any failed check or if no trace bundle is found.
"""
import sys
import os
import json
import zipfile
import glob


def find_trace(outdir):
    hits = glob.glob(os.path.join(outdir, "**", "*-trace.zip"), recursive=True)
    return max(hits, key=os.path.getmtime) if hits else None


def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else "."
    tz = find_trace(outdir)
    if not tz:
        print("FAIL: no *-trace.zip found under %s" % outdir)
        print("  - was enableTrace=true, on a functional run (not load / multi-user)?")
        print("  - did the browser session actually start against the Grid?")
        return 1

    print("trace bundle: %s (%d bytes)" % (tz, os.path.getsize(tz)))
    fails = 0

    def check(cond, msg):
        nonlocal fails
        print(("  ok  " if cond else " FAIL ") + msg)
        if not cond:
            fails += 1

    with zipfile.ZipFile(tz) as z:
        names = set(z.namelist())

        def read(n):
            return z.read(n).decode("utf-8", "replace") if n in names else None

        # --- metadata ---
        check("metadata.json" in names, "metadata.json present")
        meta = {}
        if "metadata.json" in names:
            try:
                meta = json.loads(read("metadata.json"))
                check(bool(meta.get("browser")), "metadata names the browser")
            except Exception as e:
                check(False, "metadata.json parses (%s)" % e)

        # --- network -> HAR ---
        check("network.har" in names, "network.har present")
        if "network.har" in names:
            try:
                har = json.loads(read("network.har"))
                log = har.get("log", {})
                check(log.get("version") == "1.2", "network.har is HAR 1.2")
                entries = log.get("entries", [])
                check(len(entries) >= 1, "HAR has >= 1 request (%d)" % len(entries))
            except Exception as e:
                check(False, "network.har parses (%s)" % e)

        # --- console + event streams ---
        check("console.ndjson" in names, "console.ndjson present")
        check("events.ndjson" in names, "events.ndjson present")
        ev = read("events.ndjson") or ""
        check('"type":"lifecycle"' in ev, "events.ndjson has lifecycle markers")
        # The payoff of the action hook: real per-action events, not just page loads.
        check('"type":"action"' in ev,
              "events.ndjson has >= 1 per-action event (click/hover/... hook)")

        # --- snapshots ---
        check(any(n.startswith("snapshots/") for n in names) or "snapshots.ndjson" in names,
              "snapshots present")

        # --- optional recorders: only assert when metadata says they ran ---
        if meta.get("recMetrics"):
            check("performance.json" in names and "memory.json" in names,
                  "performance.json + memory.json present (metrics)")
        if meta.get("recCpu"):
            check("cpu.cpuprofile" in names, "cpu.cpuprofile present")
        if meta.get("recCoverage"):
            check("coverage.json" in names, "coverage.json present")
        if meta.get("recVideo"):
            check("video.mp4" in names or any(n.startswith("frames/") for n in names),
                  "video.mp4 (or frames/ fallback) present")

        check("truncatedBodies" in meta, "metadata carries body-cap counters")

        print()
        print("bundle entries: %s" % ", ".join(sorted(names)))

    print()
    if fails == 0:
        print("TRACE E2E VALIDATION PASSED - the recorder pipeline works against a real browser.")
        return 0
    print("%d CHECK(S) FAILED" % fails)
    return 1


if __name__ == "__main__":
    sys.exit(main())
