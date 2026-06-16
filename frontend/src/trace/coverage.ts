import type { Coverage } from './types';

export interface CovRow {
  url: string;
  usedBytes: number;
  totalBytes: number;
  pct: number; // 0..100
}
export interface CovSummary {
  js: CovRow[];
  css: CovRow[];
  jsTotals: { used: number; total: number; pct: number };
  cssTotals: { used: number; total: number; pct: number };
}

// merge [start,end) intervals and sum their covered length
function mergedLength(ivals: Array<[number, number]>): number {
  if (ivals.length === 0) return 0;
  ivals.sort((a, b) => a[0] - b[0]);
  let total = 0;
  let [cs, ce] = ivals[0];
  for (let i = 1; i < ivals.length; i++) {
    const [s, e] = ivals[i];
    if (s <= ce) {
      if (e > ce) ce = e;
    } else {
      total += ce - cs;
      cs = s;
      ce = e;
    }
  }
  total += ce - cs;
  return total;
}

function shortUrl(u: string): string {
  if (!u) return '(inline)';
  try {
    const url = new URL(u);
    const last = url.pathname.split('/').filter(Boolean).pop();
    return last ? last + (url.search ? url.search : '') : url.host;
  } catch {
    const parts = u.split('/');
    return parts[parts.length - 1] || u;
  }
}

export function summarizeCoverage(cov: Coverage | null): CovSummary | null {
  if (!cov || (!cov.js && !cov.css)) return null;

  const js: CovRow[] = [];
  for (const script of cov.js ?? []) {
    let total = 0;
    const covered: Array<[number, number]> = [];
    for (const fn of script.functions ?? []) {
      for (const r of fn.ranges ?? []) {
        if (r.endOffset > total) total = r.endOffset;
        if (r.count > 0) covered.push([r.startOffset, r.endOffset]);
      }
    }
    const used = Math.min(mergedLength(covered), total);
    js.push({
      url: shortUrl(script.url),
      usedBytes: used,
      totalBytes: total,
      pct: total > 0 ? (used / total) * 100 : 0,
    });
  }
  js.sort((a, b) => b.totalBytes - a.totalBytes);

  // CSS ruleUsage: per stylesheet, used vs total tracked-rule span
  const cssMap = new Map<string, { used: number; total: number }>();
  for (const r of cov.css ?? []) {
    const key = r.styleSheetId;
    const span = Math.max(0, r.endOffset - r.startOffset);
    const cur = cssMap.get(key) ?? { used: 0, total: 0 };
    cur.total += span;
    if (r.used) cur.used += span;
    cssMap.set(key, cur);
  }
  const css: CovRow[] = [...cssMap.entries()].map(([id, v]) => ({
    url: 'stylesheet ' + id,
    usedBytes: v.used,
    totalBytes: v.total,
    pct: v.total > 0 ? (v.used / v.total) * 100 : 0,
  }));
  css.sort((a, b) => b.totalBytes - a.totalBytes);

  const jsUsed = js.reduce((s, r) => s + r.usedBytes, 0);
  const jsTot = js.reduce((s, r) => s + r.totalBytes, 0);
  const cssUsed = css.reduce((s, r) => s + r.usedBytes, 0);
  const cssTot = css.reduce((s, r) => s + r.totalBytes, 0);

  return {
    js,
    css,
    jsTotals: { used: jsUsed, total: jsTot, pct: jsTot > 0 ? (jsUsed / jsTot) * 100 : 0 },
    cssTotals: { used: cssUsed, total: cssTot, pct: cssTot > 0 ? (cssUsed / cssTot) * 100 : 0 },
  };
}
