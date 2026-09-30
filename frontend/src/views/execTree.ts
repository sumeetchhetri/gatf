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
// G6: folder rows for the Execute file list. Pure helpers (unit-tested) — a folder row selects every
// descendant file client-side, so /execute keeps receiving an explicit file list (no server change).

export type ExecRow =
  | { kind: 'dir'; path: string; name: string; depth: number; files: string[] }
  | { kind: 'file'; path: string; name: string; depth: number };

/** Sorted paths → interleaved folder/file rows (folders emitted once, before their first descendant). */
export function buildExecRows(paths: string[]): ExecRow[] {
  const sorted = [...paths].sort((a, b) => a.localeCompare(b));
  const rows: ExecRow[] = [];
  const dirRows = new Map<string, Extract<ExecRow, { kind: 'dir' }>>();
  for (const p of sorted) {
    const parts = p.split('/');
    for (let i = 1; i < parts.length; i++) {
      const dir = parts.slice(0, i).join('/');
      let row = dirRows.get(dir);
      if (!row) {
        row = { kind: 'dir', path: dir, name: parts[i - 1], depth: i - 1, files: [] };
        dirRows.set(dir, row);
        rows.push(row);
      }
      row.files.push(p);
    }
    rows.push({ kind: 'file', path: p, name: parts[parts.length - 1], depth: parts.length - 1 });
  }
  return rows;
}

/** Hide rows under a collapsed folder (the collapsed folder row itself stays visible). */
export function visibleRows(rows: ExecRow[], collapsed: Set<string>): ExecRow[] {
  if (collapsed.size === 0) return rows;
  return rows.filter((r) => {
    const parts = r.path.split('/');
    for (let i = 1; i < parts.length; i++) if (collapsed.has(parts.slice(0, i).join('/'))) return false;
    return true;
  });
}

/** Tri-state of a folder against the current selection. */
export function dirState(files: string[], selected: Set<string>): 'all' | 'some' | 'none' {
  let n = 0;
  for (const f of files) if (selected.has(f)) n++;
  return n === 0 ? 'none' : n === files.length ? 'all' : 'some';
}

// Execute view filter: the CLI's --grep / --grep-invert / --tag for one run, applied server-side to test titles
// (UI: script path + the @tags in its leading // comments; API: file, test name and description).
export interface ExecFilter {
  grep: string;
  grepInvert: string;
  tags: string;
}

/** '@smoke, checkout  @smoke' → ['@smoke', '@checkout'] (comma / space separated, '@' optional, de-duplicated). */
export function normTags(tags: string): string[] {
  const out: string[] = [];
  for (const t of tags.split(/[,\s]+/)) {
    if (!t || t === '@') continue;
    const tag = t.startsWith('@') ? t : '@' + t;
    if (!out.includes(tag)) out.push(tag);
  }
  return out;
}

/** First invalid regex as a message, else null (the server re-checks with Java's regex syntax). */
export function execFilterError(f: ExecFilter): string | null {
  for (const [k, v] of [
    ['grep', f.grep],
    ['grep-invert', f.grepInvert],
  ] as const) {
    if (!v.trim()) continue;
    try {
      new RegExp(v.trim());
    } catch (e) {
      return `invalid ${k} regex: ${(e as Error).message}`;
    }
  }
  return null;
}

/** Query suffix for PUT /execute ('' when the filter is empty). */
export function execFilterQuery(f: ExecFilter): string {
  let q = '';
  if (f.grep.trim()) q += '&grep=' + encodeURIComponent(f.grep.trim());
  if (f.grepInvert.trim()) q += '&grepInvert=' + encodeURIComponent(f.grepInvert.trim());
  const tags = normTags(f.tags);
  if (tags.length) q += '&tag=' + encodeURIComponent(tags.join(','));
  return q;
}

/** The same filter as `gatf test` options, for display. */
export function describeExecFilter(f: ExecFilter): string {
  const d: string[] = [];
  if (f.grep.trim()) d.push('--grep ' + f.grep.trim());
  for (const t of normTags(f.tags)) d.push('--tag ' + t);
  if (f.grepInvert.trim()) d.push('--grep-invert ' + f.grepInvert.trim());
  return d.join(' ');
}
