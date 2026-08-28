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
