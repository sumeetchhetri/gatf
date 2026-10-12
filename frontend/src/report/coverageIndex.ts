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
// H6 (Δ155): the coverage index served by GET /api/coverage (com.gatf.coverage.CoverageMerge, format
// "gatf-coverage-index" v2 since Δ155a; v1 is still read). Field names mirror the Java writer. Old, missing or
// foreign payloads → null ("no data").

export const COVERAGE_INDEX_FORMAT = 'gatf-coverage-index';
export const COVERAGE_INDEX_VERSION = 2;
const READABLE_VERSIONS = [1, 2];

/** Δ155a: a test's coverage state; anything but 'ok' means its script list may be incomplete. */
export type CovState = 'ok' | 'empty' | 'capped' | 'old' | 'malformed' | 'missing';

export interface CovTestEntry {
  /** v2 */
  coverage?: CovState;
  traces: string[];
  routes: string[];
  scripts: Record<string, { len: number; usedBytes: number | null; bodies?: string[]; mixedBodies?: boolean; truncated?: boolean }>;
  css: { rules: number; totalBytes: number; usedBytes: number };
}
export interface CovScriptAgg {
  len: number;
  usedBytes: number;
  functionsHit: number;
  functionsTotal: number;
  tests: string[];
  bodies: string[];
  /** v2: more than one body of this URL in the run (ranges not unioned across them) */
  mixedBodies?: boolean;
}
export interface CovUntestedRoute {
  route: string;
  priority: 'high' | 'medium' | 'low' | 'error' | null;
}
export interface CoverageIndexDto {
  format: string;
  version: number;
  detail: 'full' | 'summary';
  run: string;
  status: 'ok' | 'partial' | 'no-data';
  counts: {
    traces: number;
    tracesWithCoverage: number;
    tracesCoverageRead?: number;
    tests: number;
    testsCoverageNotOk?: number;
    scripts: number;
    routesVisited: number;
    crawlRoutes: number | null;
    untestedRoutes: number | null;
    untestedScripts: number;
    skipped: number;
  };
  tests: Record<string, CovTestEntry>;
  routes: Record<string, string[]>;
  scripts: Record<string, CovScriptAgg>;
  css: { unattributed: boolean; traces: number; sheets: number; rules: number; totalBytes: number; usedBytes: number };
  crawl: { source: string; routes: number } | null;
  untested: { routes: CovUntestedRoute[] | null; scripts: string[] };
  skipped: { file: string; test?: string | null; reason: string }[];
  notes: string[];
  rules: Record<string, string>;
  /** /api/coverage only: crawl maps in the test dir, newest first */
  crawls?: string[];
}

const isObj = (x: unknown): x is Record<string, unknown> => typeof x === 'object' && x !== null && !Array.isArray(x);

/** The index when the payload is one this page understands; null for anything else (old server, other version, junk). */
export function asCoverageIndex(x: unknown): CoverageIndexDto | null {
  if (!isObj(x) || x.format !== COVERAGE_INDEX_FORMAT || !READABLE_VERSIONS.includes(x.version as number)) return null;
  if (typeof x.status !== 'string' || !isObj(x.counts)) return null;
  const arr = <T,>(v: unknown): T[] => (Array.isArray(v) ? (v as T[]) : []);
  const obj = <T,>(v: unknown): Record<string, T> => (isObj(v) ? (v as Record<string, T>) : {});
  const untested = obj<unknown>(x.untested);
  const css = obj<number | boolean>(x.css);
  return {
    ...(x as unknown as CoverageIndexDto),
    tests: obj<CovTestEntry>(x.tests),
    routes: obj<string[]>(x.routes),
    scripts: obj<CovScriptAgg>(x.scripts),
    css: {
      unattributed: css.unattributed !== false,
      traces: Number(css.traces ?? 0),
      sheets: Number(css.sheets ?? 0),
      rules: Number(css.rules ?? 0),
      totalBytes: Number(css.totalBytes ?? 0),
      usedBytes: Number(css.usedBytes ?? 0),
    },
    crawl: isObj(x.crawl) ? (x.crawl as CoverageIndexDto['crawl']) : null,
    untested: {
      routes: Array.isArray(untested.routes) ? (untested.routes as CovUntestedRoute[]) : null,
      scripts: arr<string>(untested.scripts),
    },
    skipped: arr<{ file: string; test?: string | null; reason: string }>(x.skipped),
    notes: arr<string>(x.notes),
    rules: obj<string>(x.rules),
    crawls: arr<string>(x.crawls),
  };
}

export interface CovScriptRow {
  url: string;
  usedBytes: number;
  totalBytes: number;
  pct: number;
  functions: string;
  tests: number;
}

/** Scripts, largest first, in CoverageTab's row shape (used/total/pct). */
export function scriptRows(idx: CoverageIndexDto): CovScriptRow[] {
  return Object.entries(idx.scripts)
    .map(([url, s]) => ({
      url,
      usedBytes: s.usedBytes ?? 0,
      totalBytes: s.len ?? 0,
      pct: s.len > 0 ? ((s.usedBytes ?? 0) / s.len) * 100 : 0,
      functions: (s.functionsHit ?? 0) + '/' + (s.functionsTotal ?? 0),
      tests: (s.tests ?? []).length,
    }))
    .sort((a, b) => b.totalBytes - a.totalBytes || a.url.localeCompare(b.url));
}

const PRIORITY_RANK: Record<string, number> = { high: 0, medium: 1, low: 2, error: 3 };

/** Untested routes, highest crawl priority first; null when there is no crawl map to compare with. */
export function untestedRoutes(idx: CoverageIndexDto): CovUntestedRoute[] | null {
  const r = idx.untested.routes;
  if (!r) return null;
  return [...r].sort(
    (a, b) => (PRIORITY_RANK[a.priority ?? ''] ?? 4) - (PRIORITY_RANK[b.priority ?? ''] ?? 4) || a.route.localeCompare(b.route),
  );
}
