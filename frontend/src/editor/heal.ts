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
// H1 (Δ150): the healer's result as the editor shows it, and the store that hands a heal from the Trace Viewer to the
// editor. Nothing here builds seleasy: every line shown or written is the server's (Healer, validated there).
import { useSyncExternalStore } from 'react';

export interface HealProposal {
  rank: number;
  source: 'deterministic' | 'ai' | string;
  kind: string;
  locator?: string;
  count?: number;
  strict?: boolean;
  lines: string[];
  why: string;
  score: number;
  diff: string;
}

export interface HealResult {
  status: 'proposed' | 'none' | 'not-healable' | 'no-snapshot' | string;
  reason?: string;
  ai: string;
  aiStatus: string;
  step: string;
  locator?: { token: string; count: number };
  proposals: HealProposal[];
  usage?: Record<string, number>;
  file: string | null;
  line: number;
  stepSource?: string;
  trace?: string;
  failure?: string;
  /** set when a proposal can be accepted (the server keeps the proposals; Accept names this id + a rank) */
  healId?: string;
  error?: string;
  httpStatus?: number;
}

export interface HealApplyResult {
  status?: 'applied' | 'conflict' | 'invalid' | string;
  file?: string;
  line?: number;
  rank?: number;
  content?: string;
  lines?: string[];
  reason?: string;
  expected?: string;
  actual?: string | null;
  validation?: unknown;
  error?: string;
  httpStatus?: number;
}

export interface DiffRow {
  t: ' ' | '-' | '+' | '@';
  text: string;
}

/** A unified diff (as Healer builds it) as rows; the ---/+++ header is dropped. */
export function diffRows(diff: string): DiffRow[] {
  const out: DiffRow[] = [];
  for (const l of (diff || '').split('\n')) {
    if (l === '' || l.startsWith('--- ') || l.startsWith('+++ ')) continue;
    const c = l[0];
    if (c === '@') out.push({ t: '@', text: l });
    else if (c === '-' || c === '+' || c === ' ') out.push({ t: c, text: l.slice(1) });
  }
  return out;
}

/** "strict · 1 match" for a proposal whose locator resolved to exactly one element in the snapshot. */
export function strictText(p: HealProposal): string {
  if (!p.locator) return p.kind === 'goto' ? 'navigation' : '';
  const n = p.count ?? 0;
  return (p.strict ? 'strict' : 'not strict') + ' · ' + n + (n === 1 ? ' match' : ' matches');
}

/** The message for an Accept that wrote nothing (conflict / invalid / error), or null when it was applied. */
export function applyMessage(r: HealApplyResult): string | null {
  if (r.status === 'applied') return null;
  if (r.status === 'conflict') {
    return 'Conflict: ' + (r.reason || 'the line changed since the heal') + (r.actual != null ? ' (now: ' + r.actual + ')' : '') + '. Nothing was written; heal again.';
  }
  if (r.status === 'invalid') {
    const v = r.validation as { error?: unknown } | string | undefined;
    const why = typeof v === 'string' ? v : v && v.error != null ? String(v.error) : 'validation failed';
    return 'The fixed script did not validate: ' + why + '. Nothing was written.';
  }
  return 'Not applied: ' + (r.error || 'HTTP ' + (r.httpStatus ?? '?'));
}

let current: HealResult | null = null;
const subs = new Set<() => void>();

/** The heal the editor shows (one at a time). */
export const healStore = {
  get: (): HealResult | null => current,
  set(r: HealResult | null): void {
    current = r && typeof r.file === 'string' && Array.isArray(r.proposals) ? r : null;
    for (const f of Array.from(subs)) f();
  },
  subscribe(f: () => void): () => void {
    subs.add(f);
    return () => {
      subs.delete(f);
    };
  },
};

export function useHeal(): HealResult | null {
  return useSyncExternalStore(healStore.subscribe, healStore.get, healStore.get);
}

/** The trace's recorded failure, if any (a lifecycle "failure" event). */
export function hasFailure(events: Array<{ type: string; name: string | null }>): boolean {
  return events.some((e) => e.type === 'lifecycle' && e.name === 'failure');
}

/** Only traces served from the reports directory can be healed (the server reads the run's own trace). */
export function healableTraceUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  const u = url.replace(/^https?:\/\/[^/]+/, '');
  if (!/^\/reports\/.+/.test(u)) return null;
  const path = u.split(/[?#]/)[0];
  try {
    return decodeURIComponent(path);
  } catch {
    return path;
  }
}
