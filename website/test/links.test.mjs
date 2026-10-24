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
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { rewriteRepoLink, imageOutsideDocs, gitStat } from '../plugins/remark-repo-links.mjs';
import { describe, byOrder } from '../plugins/release-notes.mjs';
import { siteEnv, normaliseBase } from '../plugins/site-env.mjs';
import { validCname, refEnvName, newestPatchTag } from '../scripts/prepare.mjs';

const REPO = '/r';
const ROOTS = [
  { dir: '/r/docs', ref: 'master' },
  { dir: '/r/website/versioned_docs/version-5.0', ref: 'abc123' },
];
const FS = { '/r/sample-project': 'dir', '/r/docs/seleasy/commands.json': 'file', '/r/README.md': 'file', '/r/docs/guide': 'dir' };
const stat = (p) => FS[p.replace(/\/$/, '')] || null;
const rw = (url, from) => rewriteRepoLink(url, from, ROOTS, REPO, 'https://github.com/o/g/', stat);

test('repository paths outside docs/ go to GitHub at the version ref', () => {
  assert.equal(rw('../sample-project/', '/r/docs/configuration.md'), 'https://github.com/o/g/tree/master/sample-project');
  assert.equal(rw('../README.md#install', '/r/docs/a.md'), 'https://github.com/o/g/blob/master/README.md#install');
  assert.equal(
    rw('../sample-project/', '/r/website/versioned_docs/version-5.0/configuration.md'),
    'https://github.com/o/g/tree/abc123/sample-project',
  );
  assert.equal(rw('commands.json', '/r/docs/seleasy/REFERENCE.md'), 'https://github.com/o/g/blob/master/docs/seleasy/commands.json');
});

test('docs pages, anchors, absolute and external URLs, and missing paths are left for Docusaurus to check', () => {
  for (const u of ['REFERENCE.md', 'x.md#a', '#a', '/docs/x', 'https://e.org', 'mailto:a@b', '../missing/', '../../../etc', '']) {
    assert.equal(rw(u, '/r/docs/seleasy/README.md'), null, u);
  }
  assert.equal(rw('guide/', '/r/docs/a.md'), null, 'a folder inside docs is a docs route');
  assert.equal(rewriteRepoLink('../x', '/elsewhere/a.md', ROOTS, REPO, 'https://g', stat), null, 'not a docs file');
});

test('a snapshot checks repository paths at its own ref, not in the working tree', () => {
  const atRef = { 'sample-project': 'dir' };
  const roots = [{ dir: '/r/website/versioned_docs/version-5.0', ref: 'abc123', stat: (rel) => atRef[rel] || null }];
  const from = '/r/website/versioned_docs/version-5.0/a.md';
  const noTree = () => null; // the working tree no longer has it
  assert.equal(rewriteRepoLink('../sample-project/', from, roots, REPO, 'https://g', noTree), 'https://g/tree/abc123/sample-project');
  assert.equal(rewriteRepoLink('../README.md', from, roots, REPO, 'https://g', () => 'file'), null, 'absent at the ref');
});

const REPO_ROOT = path.resolve(new URL('../..', import.meta.url).pathname);
const IN_GIT = fs.existsSync(path.join(REPO_ROOT, '.git'));

test('gitStat reads the commit (this repository)', { skip: !IN_GIT && 'not a git checkout' }, () => {
  const root = REPO_ROOT;
  const st = gitStat(root, 'HEAD');
  assert.equal(st('docs'), 'dir');
  assert.equal(st('pom.xml'), 'file');
  assert.equal(st('no/such/path'), null);
  assert.equal(gitStat(root, 'no-such-ref-xyz')('pom.xml'), null);
});

test('images must live under docs/, in every version', () => {
  assert.equal(imageOutsideDocs('img/a.png', '/r/docs/guide/x.md', ROOTS), null);
  assert.equal(imageOutsideDocs('../../docs/a.png', '/r/website/versioned_docs/version-5.0/g/x.md', ROOTS) !== null, true);
  assert.match(imageOutsideDocs('../../alldep-jar/a.png', '/r/docs/g/x.md', ROOTS), /outside docs\//);
  for (const u of ['https://e.org/a.png', '/img/a.png', 'data:image/png;base64,AA']) assert.equal(imageOutsideDocs(u, '/r/docs/x.md', ROOTS), null, u);
});

test('release-note titles and order', () => {
  assert.deepEqual(describe('# Tier 3 notes\n\nAdds **x** and [y](z.md).\n', 'f'), { title: 'Tier 3 notes', summary: 'Adds x and y.', position: null });
  assert.deepEqual(describe('---\ntitle: "GATF 5.0.0"\nsidebar_position: 1\n---\n\n- list\n\nText.\n', 'f'), { title: 'GATF 5.0.0', summary: 'Text.', position: 1 });
  assert.equal(describe('no heading', 'fallback').title, 'fallback');
  const n = [
    { file: 'tier-3.md', position: null },
    { file: '5.0.0.md', position: 1 },
    { file: 'tier-10.md', position: null },
  ].sort(byOrder);
  assert.deepEqual(n.map((x) => x.file), ['5.0.0.md', 'tier-10.md', 'tier-3.md']);
});

test('site settings from env', () => {
  assert.deepEqual(siteEnv({}), {
    siteUrl: 'https://sumeetchhetri.github.io',
    baseUrl: '/gatf/',
    repoUrl: 'https://github.com/sumeetchhetri/gatf',
    release: '5.0.0',
  });
  const e = siteEnv({ GATF_SITE_URL: 'https://gatf.example.org/', GATF_SITE_BASE: '/' });
  assert.equal(e.siteUrl, 'https://gatf.example.org');
  assert.equal(e.baseUrl, '/');
  assert.equal(siteEnv({ GATF_SITE_BASE: '' }).baseUrl, '/', 'set but empty = root');
  assert.equal(normaliseBase('docs'), '/docs/');
  assert.equal(normaliseBase('//a/b//'), '/a/b/');
  assert.throws(() => siteEnv({ GATF_SITE_URL: 'https://h/path' }), /origin/);
  assert.throws(() => siteEnv({ GATF_SITE_URL: 'h.org' }), /origin/);
});

test('a released docs version follows the newest patch tag of its line', () => {
  assert.equal(newestPatchTag('5.0', ['5.0.0', '5.0.1', '4.9.9', '5.1.0']), '5.0.1');
  assert.equal(newestPatchTag('5.0', ['5.0.2', '5.0.10', '5.0.9']), '5.0.10', 'numeric, not string order');
  assert.equal(newestPatchTag('5.0', ['50.0.1', '5.0.1-rc1', 'v5.0.1', '5x0.1', 'docs-5.0']), null);
  assert.equal(newestPatchTag('5.0', []), null);
});

test('CNAME validation and ref env names', () => {
  assert.ok(validCname('gatf.example.org'));
  assert.ok(validCname('docs.gatf.dev'));
  for (const bad of ['bad host', 'localhost', '-a.org', 'a-.org', 'a.org/x', 'https://a.org', 'a..org']) assert.ok(!validCname(bad), bad);
  assert.equal(refEnvName('5.0'), 'GATF_DOCS_REF_5_0');
});
