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
// Runs before every start/build (npm prestart/prebuild). It writes only git-ignored files:
//   1. versioned_docs/version-<v>/ + versioned_sidebars/: each released docs version (versions.json), materialised from
//      a git ref: by default ("auto" in version-refs.json) the newest release tag of that line (5.0 -> the highest
//      5.0.x tag, e.g. 5.0.1), so a patch release refreshes its docs with no extra tag. version-refs.json may instead
//      name any ref, and GATF_DOCS_REF_<v with . as _> (e.g. GATF_DOCS_REF_5_0) overrides both. When there is no such
//      ref (or no git), the working-tree ../docs is used and a warning says so. "next" is
//      ../docs itself, read in place. No docs are copied into the repository.
//   2. .site-meta.json: what each version was built from (read by docusaurus.config.mjs for source links).
//   3. static/CNAME: only when GATF_SITE_CNAME is set; otherwise any stale one is removed.
//   4. src/prism/seleasy.generated.js: the Prism grammar converted from ../vscode-extension/syntaxes.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmToPrism, emitModule } from './tm2prism.mjs';

const SITE = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const REPO = path.resolve(SITE, '..');
const DOCS = path.join(REPO, 'docs');
const EXCLUDE_TOP = new Set(['deltas']); // git-ignored DELTA mds, never published

export function refEnvName(version) {
  return 'GATF_DOCS_REF_' + version.replace(/[^A-Za-z0-9]/g, '_');
}

export function validCname(v) {
  return /^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\.(?!-)[a-z0-9-]{1,63}(?<!-))+$/i.test(v);
}

function git(args, opts = {}) {
  return execFileSync('git', ['-C', REPO, ...args], { stdio: ['ignore', 'pipe', 'ignore'], ...opts });
}

/** Pure: the highest X.Y.Z tag of a version line ("5.0" -> "5.0.1" over "5.0.0"), or null. */
export function newestPatchTag(version, tags) {
  const re = new RegExp('^' + version.replace(/\./g, '\\.') + '\\.(\\d+)$');
  let best = null;
  for (const t of tags) {
    const m = re.exec(t.trim());
    if (m && (!best || Number(m[1]) > best.n)) best = { tag: t.trim(), n: Number(m[1]) };
  }
  return best ? best.tag : null;
}

function listTags() {
  try {
    return git(['tag', '--list']).toString().split('\n').filter(Boolean);
  } catch {
    return [];
  }
}

function resolveRef(ref) {
  try {
    return git(['rev-parse', '--verify', '--quiet', ref + '^{commit}']).toString().trim();
  } catch {
    return null;
  }
}

function copyDocs(from, to) {
  fs.cpSync(from, to, {
    recursive: true,
    filter: (src) => {
      const rel = path.relative(from, src);
      return !rel || !EXCLUDE_TOP.has(rel.split(path.sep)[0]);
    },
  });
}

function materialise(version, ref) {
  const dest = path.join(SITE, 'versioned_docs', 'version-' + version);
  fs.rmSync(dest, { recursive: true, force: true });
  fs.mkdirSync(dest, { recursive: true });
  const sha = ref ? resolveRef(ref) : null;
  if (sha) {
    const tmp = fs.mkdtempSync(path.join(SITE, '.snap-'));
    try {
      const tar = git(['archive', '--format=tar', sha, 'docs'], { maxBuffer: 1 << 30 });
      execFileSync('tar', ['-x', '-C', tmp], { input: tar });
      copyDocs(path.join(tmp, 'docs'), dest);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
    console.log(`[prepare] docs ${version}: git ${ref} (${sha.slice(0, 12)})`);
    return { source: 'git', ref, sha };
  }
  copyDocs(DOCS, dest);
  console.warn(`[prepare] WARNING docs ${version}: ref "${ref}" not found; using the working-tree docs/ instead`);
  return { source: 'worktree', ref: null, sha: null };
}

export function main(env = process.env) {
  const versions = JSON.parse(fs.readFileSync(path.join(SITE, 'versions.json'), 'utf8'));
  const refs = JSON.parse(fs.readFileSync(path.join(SITE, 'version-refs.json'), 'utf8'));
  const meta = { versions: {} };
  fs.mkdirSync(path.join(SITE, 'versioned_sidebars'), { recursive: true });
  for (const v of versions) {
    const configured = env[refEnvName(v)] || refs[v] || 'auto';
    const ref = configured === 'auto' ? newestPatchTag(v, listTags()) || `${v}.x (no release tag yet)` : configured;
    meta.versions[v] = materialise(v, ref);
    fs.writeFileSync(
      path.join(SITE, 'versioned_sidebars', `version-${v}-sidebars.json`),
      JSON.stringify({ docs: [{ type: 'autogenerated', dirName: '.' }] }, null, 2) + '\n',
    );
  }
  fs.writeFileSync(path.join(SITE, '.site-meta.json'), JSON.stringify(meta, null, 2) + '\n');

  const cnamePath = path.join(SITE, 'static', 'CNAME');
  const cname = (env.GATF_SITE_CNAME || '').trim();
  if (cname) {
    if (!validCname(cname)) throw new Error(`GATF_SITE_CNAME is not a host name: ${cname}`);
    fs.writeFileSync(cnamePath, cname + '\n');
    console.log(`[prepare] static/CNAME: ${cname}`);
  } else {
    fs.rmSync(cnamePath, { force: true });
  }

  const tmFile = path.join(REPO, 'vscode-extension', 'syntaxes', 'seleasy.tmLanguage.json');
  const prism = tmToPrism(JSON.parse(fs.readFileSync(tmFile, 'utf8')));
  const header = fs.readFileSync(fileURLToPath(import.meta.url), 'utf8').split('*/')[0] + '*/\n';
  fs.mkdirSync(path.join(SITE, 'src', 'prism'), { recursive: true });
  fs.writeFileSync(
    path.join(SITE, 'src', 'prism', 'seleasy.generated.js'),
    emitModule(prism, 'vscode-extension/syntaxes/seleasy.tmLanguage.json', header),
  );
  console.log(`[prepare] seleasy Prism grammar: ${Object.keys(prism).length} tokens`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
