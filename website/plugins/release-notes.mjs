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
// Lists docs/release-notes/*.md (titles and a one-line summary) as global data for the /release-notes page. The notes
// themselves stay ordinary docs pages; the page links to each in the newest docs version that has it.
import fs from 'node:fs';
import path from 'node:path';

/** Pure: { title, summary, position } from a Markdown source (front matter title, else the first H1; first prose line). */
export function describe(src, fallback) {
  let body = src.replace(/^\uFEFF/, '');
  let title = null;
  let position = null;
  const fm = /^---\r?\n([\s\S]*?)\r?\n---\r?\n/.exec(body);
  if (fm) {
    const t = /^title:\s*(.+?)\s*$/m.exec(fm[1]);
    if (t) title = t[1].replace(/^(['"])(.*)\1$/, '$2');
    const p = /^sidebar_position:\s*(-?\d+(?:\.\d+)?)\s*$/m.exec(fm[1]);
    if (p) position = Number(p[1]);
    body = body.slice(fm[0].length);
  }
  const lines = body.split(/\r?\n/);
  const h1 = lines.findIndex((l) => /^#\s+/.test(l));
  if (!title && h1 >= 0) title = lines[h1].replace(/^#\s+/, '').trim();
  const rest = lines.slice(h1 + 1);
  const para = rest.find((l) => l.trim() && !/^(#|[-*>|]|```|<)/.test(l.trim()));
  const summary = para ? para.trim().replace(/[*_`]/g, '').replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') : '';
  return { title: title || fallback, summary, position };
}

/** Sidebar order (sidebar_position ascending) first, then file name, newest (highest) first. */
export function byOrder(a, b) {
  const pa = a.position ?? Infinity;
  const pb = b.position ?? Infinity;
  if (pa !== pb) return pa - pb;
  return b.file.localeCompare(a.file, 'en', { numeric: true });
}

export default function releaseNotesPlugin(context, options) {
  const dir = options.dir;
  return {
    name: 'gatf-release-notes',
    getPathsToWatch() {
      return [path.join(dir, '*.md')];
    },
    async loadContent() {
      if (!fs.existsSync(dir)) return [];
      return fs
        .readdirSync(dir)
        .filter((f) => /\.mdx?$/i.test(f) && !f.startsWith('_'))
        .map((f) => {
          const id = 'release-notes/' + f.replace(/\.mdx?$/i, '');
          return { id, file: f, ...describe(fs.readFileSync(path.join(dir, f), 'utf8'), id) };
        })
        .sort(byOrder);
    },
    async contentLoaded({ content, actions }) {
      actions.setGlobalData({ notes: content });
    },
  };
}
