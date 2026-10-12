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
// H2 (Δ148): pick locator over a trace snapshot. Everything runs in the viewer's own realm against the sandboxed
// frame's document (allow-same-origin, no allow-scripts): hit-testing with elementFromPoint, the semantic candidates
// and the strict legacy fallback from the generated copy of the page scripts (SemanticLocators.java), ranking on the
// server (LocatorRanking via /record?action=rank), which also builds the line to paste. The viewer never formats
// seleasy itself. Nothing here throws: a failure is an empty pick or a server-less result.
import { makeSemanticResolver, type SemanticCandidate, type SemanticFallback } from './semanticResolver.generated';

export type { SemanticCandidate, SemanticFallback };

/** What the snapshot DOM says about one element: candidates and fallback, each with its match count there. */
export interface PickRaw {
  cand: SemanticCandidate[];
  sel: SemanticFallback | null;
  /** a short human description, e.g. <button#save.primary> */
  tag: string;
}

/** The server's ranking (PickLocator.rank). */
export interface RankReply {
  locator: string | null;
  line: string | null;
  strict: boolean;
  /** the chosen candidate's index, -1 for the fallback, null when nothing was chosen */
  source: number | null;
  candidates: Array<{ kind: string; locator: string | null; n: number }>;
  fallback: { locator: string; n: number } | null;
  error?: string;
}

export interface PickResult {
  raw: PickRaw;
  /** null when the server could not be reached (or refused the request) */
  ranked: RankReply | null;
  /** the server-built line, only when its source resolves strictly in this snapshot's DOM */
  line: string | null;
  error?: string;
}

/** The element under (x, y) in frame coordinates, descending into open shadow roots. */
export function deepElementFromPoint(doc: Document, x: number, y: number): Element | null {
  try {
    let e = doc.elementFromPoint(x, y);
    for (let g = 0; e && e.shadowRoot && g < 32; g++) {
      const inner = e.shadowRoot.elementFromPoint(x, y);
      if (!inner || inner === e) break;
      e = inner;
    }
    return e;
  } catch {
    return null;
  }
}

/** Elements the rebuild itself adds; never offered as a pick. */
export function isViewerNode(el: Element): boolean {
  return el.hasAttribute('data-gatf-rect');
}

function describe(el: Element): string {
  let d = '<' + el.localName;
  const id = el.getAttribute('id');
  if (id) d += '#' + id;
  const cl = (el.getAttribute('class') || '').trim().split(/\s+/).filter(Boolean).slice(0, 2);
  if (cl.length) d += '.' + cl.join('.');
  return d + '>';
}

/** Candidates + fallback for `el`, counted in its own document. Never throws. */
export function collectPick(doc: Document, el: Element, tidAttr = 'data-testid'): PickRaw {
  const raw: PickRaw = { cand: [], sel: null, tag: '' };
  try {
    raw.tag = describe(el);
    const win = (doc.defaultView ?? window) as Window;
    const r = makeSemanticResolver(doc, win);
    try { raw.cand = r.candidates(el, tidAttr) ?? []; } catch { raw.cand = []; }
    try { raw.sel = r.fallback(el); } catch { raw.sel = null; }
  } catch {
    /* empty pick */
  }
  return raw;
}

/**
 * The paste line, only when the server's choice resolves strictly here: the chosen candidate (or the fallback) must
 * have matched exactly one element in this snapshot's DOM (the strict rule of SemanticLocators.strictViolation).
 */
export function strictLine(raw: PickRaw, ranked: RankReply | null): string | null {
  if (!ranked || !ranked.line || !ranked.locator || ranked.source == null) return null;
  const n = ranked.source === -1 ? raw.sel?.n : raw.cand[ranked.source]?.n;
  return n === 1 ? ranked.line : null;
}

/** The same check for a live pick, against the counts the page itself reported (they travel with the reply). */
export function strictLineFromReply(r: RankReply | null): string | null {
  if (!r || !r.line || !r.locator || r.source == null) return null;
  const n = r.source === -1 ? r.fallback?.n : r.candidates?.[r.source]?.n;
  return n === 1 ? r.line : null;
}

/** A candidate as plain text for display without the server (not seleasy, not pasteable). */
export function candidateText(c: SemanticCandidate | SemanticFallback): string {
  if (c.s === 'role') return `role ${(c as SemanticCandidate).role} "${(c as SemanticCandidate).name ?? ''}"`;
  return `${c.s} "${(c as { v?: string }).v ?? ''}"`;
}

export function matchText(n: number): string {
  return n === 1 ? '1 match' : `${n < 0 ? '?' : n} matches`;
}

export type RankFn = (raw: PickRaw) => Promise<RankReply>;

/**
 * Debounced, cached ranking: hovering calls request() on every move; only the last element after `delayMs` of quiet
 * is ranked, and each element is ranked at most once (WeakMap: a new snapshot's elements are new keys). A failed call
 * is not cached, so the next hover retries. The callback gets the result for that element only.
 */
export class PickRanker {
  private readonly cache = new WeakMap<Element, PickResult>();
  private timer: ReturnType<typeof setTimeout> | null = null;
  private seq = 0;
  constructor(private readonly rank: RankFn, private readonly delayMs = 150) {}

  cached(el: Element): PickResult | undefined {
    return this.cache.get(el);
  }

  /** `raw` may be a thunk: collecting candidates walks the whole document, so it runs only once the pointer rests. */
  request(el: Element, rawIn: PickRaw | (() => PickRaw), cb: (r: PickResult) => void, immediate = false): void {
    const hit = this.cache.get(el);
    this.cancel();
    if (hit) { cb(hit); return; }
    const my = ++this.seq;
    const run = async () => {
      this.timer = null;
      let res: PickResult;
      const raw = typeof rawIn === 'function' ? rawIn() : rawIn;
      try {
        const ranked = await this.rank(raw);
        if (ranked && !ranked.error && Array.isArray(ranked.candidates)) {
          res = { raw, ranked, line: strictLine(raw, ranked) };
          this.cache.set(el, res);
        } else {
          res = { raw, ranked: null, line: null, error: ranked?.error || 'ranking unavailable' };
        }
      } catch (e) {
        res = { raw, ranked: null, line: null, error: 'ranking unavailable: ' + String((e as Error)?.message ?? e) };
      }
      if (my === this.seq) cb(res);
    };
    if (immediate) void run();
    else this.timer = setTimeout(() => void run(), this.delayMs);
  }

  cancel(): void {
    if (this.timer != null) clearTimeout(this.timer);
    this.timer = null;
    this.seq++;
  }
}

/** The outline box for `el`, in the coordinates of the frame's own (position: relative) parent; null when off-frame. */
export function outlineBox(iframe: HTMLIFrameElement, el: Element): { left: number; top: number; width: number; height: number } | null {
  try {
    const r = el.getBoundingClientRect();
    const w = iframe.clientWidth, h = iframe.clientHeight;
    const x0 = Math.max(0, r.left), y0 = Math.max(0, r.top);
    const x1 = Math.min(w || r.right, r.right), y1 = Math.min(h || r.bottom, r.bottom);
    if (x1 <= x0 || y1 <= y0) return null;
    return { left: iframe.offsetLeft + iframe.clientLeft + x0, top: iframe.offsetTop + iframe.clientTop + y0, width: x1 - x0, height: y1 - y0 };
  } catch {
    return null;
  }
}
