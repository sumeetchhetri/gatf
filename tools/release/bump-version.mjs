#!/usr/bin/env node
/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
// Bumps the GATF release version everywhere it is written down, so the release workflow's "pom version matches the
// tag" check passes. Dependency-free; byte-preserving (CRLF poms stay CRLF).
//
//   node tools/release/bump-version.mjs 5.0.1            bump, then list any other mentions of the old version
//   node tools/release/bump-version.mjs 5.0.1 --dry-run  show what would change, write nothing
//   node tools/release/bump-version.mjs 5.0.1 --tag      bump, commit "Release 5.0.1", tag 5.0.1 (push it yourself)
//
// What it changes (tracked files only; release notes, delta mds and lockfiles are history and never touched):
//   - the version of every GATF Maven coordinate (groupId com.github.sumeetchhetri.gatf, artifactId gatf-*) in poms
//     and in Markdown examples: the parent pom, each module's <parent>, the plugin's gatf-alldep-jar dependency, and
//     the gatf-plugin snippets in the docs;
//   - release artifact names and links in Markdown: gatf-alldep-jar-<v>.jar (source build), gatf-alldep-<v>.jar,
//     gatf-<v>.zip, seleasy-<v>.vsix, releases/tag/<v>, releases/download/<v>/, ~/.gatf/versions/<v>, a "gatf <v>"
//     version-output line, and the link texts "[<v> release]" / "<v> release asset|page" (Δ205f);
//   - the gatf-bin*:<old> image tags in run_gatf.sh.
// Then, when tools/gen-docs/GenDocs.java exists (Δ191), it regenerates the generated docs (docs/reference/**) and
// runs their check, so the release commit passes DocsDriftTest; those files are never text-edited by this script.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const SEMVER = /^\d+\.\d+\.\d+$/;
const HISTORY = /^(docs\/release-notes\/|docs\/deltas\/|website\/blog\/)|(^|\/)release-notes\.md$|package-lock\.json$/;
const GENERATED = /^docs\/reference\//; // GenDocs output: regenerated below, never text-edited
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** The parent pom's own version (the one ci-release.yml compares with the tag). */
export function currentVersion(pom) {
  const m = /<artifactId>gatf-parent<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(pom);
  if (!m) throw new Error('pom.xml: no gatf-parent <version>');
  return m[1].trim();
}

/** Pure: the rewrite rules for one file, old -> new. Returns [newText, count]. */
export function bump(path, text, from, to) {
  const rules = [];
  if (/(^|\/)pom\.xml$/.test(path) || /\.md$/.test(path)) {
    rules.push([
      new RegExp(
        '(<groupId>\\s*com\\.github\\.sumeetchhetri\\.gatf\\s*</groupId>\\s*<artifactId>\\s*gatf-[\\w-]+\\s*</artifactId>\\s*<version>\\s*)' +
          esc(from) + '(\\s*</version>)', 'g'),
      '$1' + to + '$2',
    ]);
  }
  if (/\.md$/.test(path)) {
    const v = esc(from);
    // Artifact names and release links only. Prose such as "in GATF <v> …" describes that release's behaviour and is
    // left for a human (it is listed under "still mentions").
    for (const [pre, post] of [
      ['(gatf-alldep-jar-)', '(\\.jar)'],
      ['(gatf-alldep-)', '(\\.jar)'],
      ['(gatf-)', '(\\.zip)'],
      ['(gatf-)', '([\\/\\\\]install\\.(?:sh|ps1))'],
      ['(seleasy-)', '(\\.vsix)'],
      ['(releases\\/tag\\/)', '(?![\\w.-])()'],
      ['(releases\\/download\\/)', '(\\/)'],
      ['(\\.gatf\\/versions\\/)', '(?![\\w.-])()'],
      ['(^gatf )', '(\\r?$)'],
      ['(\\[)', '( release\\])'],
      ['(\\b)', '( release (?:asset|page)\\b)'],
    ]) rules.push([new RegExp(pre + v + post, 'gm'), '$1' + to + '$2']);
  }
  if (path === 'run_gatf.sh') rules.push([new RegExp('(gatf-bin(?:-ff|-vnc)?:)' + esc(from) + '(?![\\w.])', 'g'), '$1' + to]);
  let n = 0;
  for (const [re, rep] of rules) text = text.replace(re, (...a) => (n++, rep.replace(/\$(\d)/g, (_, i) => a[i])));
  return [text, n];
}

function git(args) {
  return execFileSync('git', ['-C', ROOT, ...args], { stdio: ['ignore', 'pipe', 'inherit'] }).toString();
}

function main(argv) {
  const to = argv.find((a) => !a.startsWith('--'));
  const dry = argv.includes('--dry-run');
  const tag = argv.includes('--tag');
  if (!to || !SEMVER.test(to)) {
    console.error('usage: node tools/release/bump-version.mjs <X.Y.Z> [--dry-run | --tag]');
    process.exit(2);
  }
  const from = currentVersion(readFileSync(join(ROOT, 'pom.xml'), 'latin1'));
  if (from === to) {
    console.log(`already ${to}: nothing to bump`);
    return;
  }
  if (tag && git(['status', '--porcelain', '--untracked-files=no']).trim()) {
    console.error('--tag needs a clean work tree (commit or stash your changes first)');
    process.exit(1);
  }
  const changed = [];
  for (const path of git(['ls-files']).split('\n').filter(Boolean)) {
    if (HISTORY.test(path) || GENERATED.test(path) || !(/(^|\/)pom\.xml$|\.md$/.test(path) || path === 'run_gatf.sh')) continue;
    const before = readFileSync(join(ROOT, path), 'latin1'); // byte-preserving round trip
    const [after, n] = bump(path, before, from, to);
    if (n) {
      changed.push(path);
      console.log(`${dry ? 'would bump' : 'bumped'}  ${path}  (${n})`);
      if (!dry) writeFileSync(join(ROOT, path), after, 'latin1');
    }
  }
  if (!changed.includes('pom.xml')) throw new Error('pom.xml was not bumped');
  // Generated docs are rebuilt from the bumped sources, never text-edited.
  const gen = join(ROOT, 'tools', 'gen-docs', 'GenDocs.java');
  if (!dry && existsSync(gen)) {
    for (const mode of ['generate', 'check']) {
      try {
        execFileSync('java', [gen, mode], { cwd: ROOT, stdio: ['ignore', 'inherit', 'inherit'] });
      } catch {
        console.error(`java tools/gen-docs/GenDocs.java ${mode} failed (Java 21 on PATH?): files are bumped but nothing is committed or tagged`);
        process.exit(1);
      }
    }
    for (const p of git(['diff', '--name-only']).split('\n').filter(Boolean)) {
      if (!changed.includes(p)) {
        changed.push(p);
        console.log(`regenerated  ${p}`);
      }
    }
  }
  // Anything else that still says the old version, for a human to judge (comments, history, other tools).
  let left = '';
  try {
    left = git(['grep', '-n', '-I', '-F', from, '--', '.', ':!*package-lock.json', ':!docs/release-notes/**', ':!docs/deltas/**', ':!website/blog/**']);
  } catch {
    left = '';
  }
  // After a real run the files hold the new text, so every line still naming the old version is listed, changed files
  // included (prose such as "in GATF <v> ..." is deliberately left). A dry run writes nothing, so it lists none.
  const rest = dry ? [] : left.split('\n').filter(Boolean);
  if (rest.length) {
    console.log(`\nstill mentions ${from} (left as is; check these are history/comments):`);
    for (const l of rest) console.log('  ' + l.slice(0, 160));
  }
  if (dry) return;
  console.log(`\n${from} -> ${to}: ${changed.length} file(s)`);
  if (tag) {
    git(['add', '--', ...changed]);
    git(['commit', '-q', '-m', `Release ${to}`]);
    git(['tag', to]);
    console.log(`committed "Release ${to}" and tagged ${to}. Push both:\n  git push origin HEAD ${to}`);
  } else {
    console.log(`next: git commit -am "Release ${to}" && git tag ${to} && git push origin HEAD ${to}`);
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main(process.argv.slice(2));
