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
// Remark plugin for docs pages. The docs are written to read on GitHub, so a page may link a repository file outside
// docs/ (e.g. ../sample-project/) or a non-Markdown file inside it (e.g. commands.json). Neither is a site page, so
// such a link is rewritten to the file on GitHub (tree/ for a folder, blob/ for a file) at the ref the version was
// built from. Everything else is left alone, so Docusaurus still resolves .md links and fails the build
// (onBrokenLinks / onBrokenMarkdownLinks: 'throw') on a link that points nowhere, including a repository path that
// does not exist at that version's ref. An image must live under docs/ (the site serves no other repository files):
// an image path outside docs/ fails the build with that message, in every version alike.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const SCHEME = /^[a-z][a-z0-9+.-]*:/i;
const MD = /\.mdx?$/i;

/**
 * Pure rewrite decision.
 * @param url       the link as written
 * @param fromFile  absolute path of the Markdown file holding it
 * @param roots     [{ dir: absolute docs root as built, ref: git ref for links, stat?: (repoRel) => kind }]
 * @param repoRoot  absolute repository root (its docs/ corresponds to every root.dir)
 * @param repoUrl   e.g. https://github.com/owner/repo
 * @param stat      (absPath) => 'file' | 'dir' | null, in the repository working tree; a root's own stat
 *                  (repo-relative path at that root's ref) wins
 * @returns the new URL, or null to leave the link alone
 */
export function rewriteRepoLink(url, fromFile, roots, repoRoot, repoUrl, stat) {
  if (!url || SCHEME.test(url) || url.startsWith('#') || url.startsWith('/') || url.startsWith('@site/')) return null;
  const hash = url.indexOf('#');
  const target = decodeURI(hash >= 0 ? url.slice(0, hash) : url);
  const anchor = hash >= 0 ? url.slice(hash) : '';
  if (!target || target.includes('?')) return null;
  const root = roots.find((r) => fromFile === r.dir || fromFile.startsWith(r.dir + path.sep));
  if (!root) return null;
  const abs = path.resolve(path.dirname(fromFile), target);
  const inDocs = abs === root.dir || abs.startsWith(root.dir + path.sep);
  if (inDocs && MD.test(abs)) return null; // a docs page: Docusaurus resolves (or rejects) it
  const repoRel = path.join('docs', path.relative(root.dir, abs)).split(path.sep).join('/');
  if (repoRel === '..' || repoRel.startsWith('../')) return null; // outside the repository: let the build reject it
  const kind = root.stat ? root.stat(repoRel) : stat(path.join(repoRoot, repoRel));
  if (!kind) return null; // missing: the broken-link check reports it
  if (inDocs && kind === 'dir') return null;
  const clean = repoRel.replace(/\/$/, '');
  return `${repoUrl.replace(/\/$/, '')}/${kind === 'dir' ? 'tree' : 'blob'}/${root.ref}/${clean}${anchor}`;
}

/** Kind of a repo-relative path at a git commit ('file' | 'dir' | null), without touching the working tree. */
export function gitStat(repoRoot, sha) {
  const cache = new Map();
  return (repoRel) => {
    if (!cache.has(repoRel)) {
      let kind = null;
      try {
        const t = execFileSync('git', ['-C', repoRoot, 'cat-file', '-t', `${sha}:${repoRel}`], { stdio: ['ignore', 'pipe', 'ignore'] })
          .toString()
          .trim();
        kind = t === 'tree' ? 'dir' : t === 'blob' ? 'file' : null;
      } catch {
        kind = null;
      }
      cache.set(repoRel, kind);
    }
    return cache.get(repoRel);
  };
}

/** Pure: the error for an image outside the docs root, or null when it is fine (or not a local path). */
export function imageOutsideDocs(url, fromFile, roots) {
  if (!url || SCHEME.test(url) || url.startsWith('/') || url.startsWith('@site/') || url.startsWith('data:')) return null;
  const root = roots.find((r) => fromFile === r.dir || fromFile.startsWith(r.dir + path.sep));
  if (!root) return null;
  const abs = path.resolve(path.dirname(fromFile), decodeURI(url.split(/[?#]/)[0]));
  if (abs === root.dir || abs.startsWith(root.dir + path.sep)) return null;
  return `image "${url}" in ${fromFile} is outside docs/: keep images under docs/ (the site serves no other repository files)`;
}

export function fsStat(p) {
  try {
    const s = fs.statSync(p);
    return s.isDirectory() ? 'dir' : 'file';
  } catch {
    return null;
  }
}

function walk(node, fn) {
  fn(node);
  if (node.children) for (const c of node.children) walk(c, fn);
}

export default function remarkRepoLinks(options) {
  const { roots, repoRoot, repoUrl, stat = fsStat, onRewrite } = options;
  return (tree, file) => {
    const from = file.path || (file.history && file.history[0]);
    if (!from) return;
    walk(tree, (node) => {
      if (node.type === 'image' && typeof node.url === 'string') {
        const err = imageOutsideDocs(node.url, from, roots);
        if (err) throw new Error(err);
      }
      if ((node.type === 'link' || node.type === 'definition') && typeof node.url === 'string') {
        const next = rewriteRepoLink(node.url, from, roots, repoRoot, repoUrl, stat);
        if (next) {
          if (onRewrite) onRewrite(from, node.url, next);
          node.url = next;
        }
      }
    });
  };
}
