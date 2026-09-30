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
// M12: attachments of a run (gatf-results.json tests[].attachments) as a gallery: kinds + servable URLs.

export type AttachmentKind = 'screenshot' | 'diff' | 'trace' | 'checks' | 'a11y' | 'other';

export const KIND_LABEL: Record<AttachmentKind, string> = {
  screenshot: 'Screenshots',
  diff: 'Visual diffs',
  trace: 'Traces',
  checks: 'Checks',
  a11y: 'Accessibility',
  other: 'Other files',
};

export function kindOf(path: string): AttachmentKind {
  const p = path.toLowerCase().replace(/\\/g, '/');
  const base = p.slice(p.lastIndexOf('/') + 1);
  if (/-(diff|expected|actual)\.png$/.test(base)) return 'diff';
  if (/^a11y.*\.json$/.test(base) || /-a11y\.json$/.test(base)) return 'a11y';
  if (/checks\.json$/.test(base)) return 'checks';
  if (/\.(png|jpe?g|webp|gif)$/.test(base)) return 'screenshot';
  if (/\.zip$/.test(base)) return 'trace';
  return 'other';
}

/**
 * URL the console can load an attachment from, or null when it is outside the served report dir.
 * Relative paths (merged reports) resolve against the report's folder; absolute paths under the run's report
 * dir (`dir` in gatf-results.json) map to that folder too.
 */
// forward slashes, no "/./" segments, lower-case drive letter
function norm(p: string): string {
  let s = p.replace(/\\/g, '/').replace(/\/(\.\/)+/g, '/');
  if (/^[a-zA-Z]:\//.test(s)) s = s[0].toLowerCase() + s.slice(1);
  return s;
}

export function attachmentUrl(path: string, runDir: string | undefined, baseDir: string): string | null {
  const p = norm(path);
  const isAbs = p.startsWith('/') || /^[a-z]:\//.test(p);
  if (!isAbs) return baseDir + p.replace(/^\.\//, '');
  if (runDir) {
    const d = norm(runDir).replace(/\/+$/, '') + '/';
    if (p.startsWith(d)) return baseDir + p.slice(d.length);
  }
  return null;
}

/** M9: a visual comparison: the files `<p>-diff.png`, `<p>-expected.png`, `<p>-actual.png` (any may be missing). */
export interface DiffSet {
  key: string;
  test: string;
  diff?: string;
  expected?: string;
  actual?: string;
}

/** Groups the 'diff' attachments of a gallery into comparisons by their common prefix. */
export function diffSets(items: Attachment[]): DiffSet[] {
  const m = new Map<string, DiffSet>();
  for (const a of items) {
    if (!a.url) continue;
    const mm = /^(.*)-(diff|expected|actual)\.png$/i.exec(a.path.replace(/\\/g, '/'));
    if (!mm) continue;
    const s = m.get(mm[1]) ?? { key: mm[1], test: a.test };
    s[mm[2].toLowerCase() as 'diff' | 'expected' | 'actual'] = a.url;
    m.set(mm[1], s);
  }
  return [...m.values()];
}

export interface Attachment {
  path: string;
  url: string | null;
  kind: AttachmentKind;
  test: string;
}

export function gallery(
  tests: { title: string; attachments?: string[] }[],
  runDir: string | undefined,
  baseDir: string,
): Map<AttachmentKind, Attachment[]> {
  const out = new Map<AttachmentKind, Attachment[]>();
  for (const t of tests) {
    for (const a of t.attachments ?? []) {
      const k = kindOf(a);
      const list = out.get(k) ?? [];
      list.push({ path: a, url: attachmentUrl(a, runDir, baseDir), kind: k, test: t.title });
      out.set(k, list);
    }
  }
  return out;
}
