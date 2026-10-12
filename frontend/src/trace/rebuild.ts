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
// H2 (Δ147): rebuild a trace DOM snapshot as a self-contained, inert HTML document for a sandboxed iframe.
//
// Inputs are the bundle's own files only: the snapshot HTML, its v2 manifest (stylesheet text in resources/<sha1>),
// and the HAR's captured bodies (url -> resources/<sha1>). Every URL in the document is resolved against the page
// URL and rewritten to a bundle URL (blob: by default), kept when it is a data: URL, or neutralised:
//   - images / media not in the bundle become a sized placeholder (no broken-image icon);
//   - stylesheets not in the bundle are dropped; url()s inside CSS go through the same map, else `none`;
//   - nothing may reach the network: a CSP meta allows only blob:/data:, and every live http(s) URL is removed.
// Scripts, event handlers, javascript: URLs, <base>, refresh/CSP metas, plugins and preload links are removed again
// here (defence in depth: lite snapshots are raw outerHTML), and the frame is sandboxed without allow-scripts.
import type { DomManifest, HarEntry, SnapshotRef, Trace } from './types';
import { domManifest } from './loadTrace';

export interface Resource {
  path: string;
  mime: string;
}

export interface RebuildStats {
  /** true when the v2 manifest was used (styled capture); false for a lite outerHTML snapshot */
  styled: boolean;
  /** stylesheets inlined from the bundle */
  sheets: number;
  /** linked stylesheets whose text is not in the bundle (dropped) */
  missingSheets: number;
  /** images / media sources not in the bundle (placeholders) */
  missingImages: number;
  /** URLs rewritten to bundle resources */
  rewritten: number;
  /** whether the action target was found in the document (else a rect overlay may stand in) */
  targetMarked: boolean;
}

export interface Rebuilt {
  html: string;
  /** object URLs this rebuild created; revoke them when the view goes away */
  urls: string[];
  manifest: DomManifest | null;
  stats: RebuildStats;
}

export interface RebuildOptions {
  /** bundle path -> URL usable inside the frame. Default: a blob: object URL (created once per path). */
  toUrl?: (path: string, mime: string, bytes: Uint8Array) => string;
  /** the action id whose target to highlight (default: snap.action) */
  highlight?: number | null;
}

/** 1x1 transparent GIF: an image slot with no broken-image icon when the size is unknown. */
export const BLANK_GIF = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';

export const SNAPSHOT_CSP =
  "default-src 'none'; img-src blob: data:; style-src blob: data: 'unsafe-inline'; font-src blob: data:; media-src blob: data:; frame-src 'self' about: blob: data:";

const dec = new TextDecoder('utf-8');
const MAX_FRAME_DEPTH = 3;

/** url (without fragment) -> captured body, from the HAR. Later entries win (the page saw the latest body). */
export function resourceMap(trace: Pick<Trace, 'har' | 'files'>): Map<string, Resource> {
  const m = new Map<string, Resource>();
  const entries = (trace.har?.log?.entries ?? []) as HarEntry[];
  for (const e of entries) {
    const f = e?.response?.content?._file;
    const url = e?.request?.url;
    if (!f || !url || !trace.files[f]) continue;
    const status = e.response.status;
    if (status && (status < 200 || status >= 300)) continue;
    m.set(stripHash(url), { path: f, mime: (e.response.content.mimeType || '').split(';')[0].trim() || guessMime(url) });
  }
  return m;
}

function stripHash(u: string): string {
  const i = u.indexOf('#');
  return i >= 0 ? u.slice(0, i) : u;
}

function guessMime(url: string): string {
  const ext = stripHash(url).split('?')[0].split('.').pop()?.toLowerCase() ?? '';
  return (
    {
      css: 'text/css', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp',
      svg: 'image/svg+xml', avif: 'image/avif', ico: 'image/x-icon', woff: 'font/woff', woff2: 'font/woff2', ttf: 'font/ttf',
      otf: 'font/otf', mp4: 'video/mp4', webm: 'video/webm',
    } as Record<string, string>
  )[ext] ?? 'application/octet-stream';
}

/** The page URL a lite snapshot was taken on: the latest document response at or before it. */
export function liteBase(trace: Pick<Trace, 'har'>, snap: SnapshotRef): string | null {
  let best: string | null = null;
  for (const e of (trace.har?.log?.entries ?? []) as HarEntry[]) {
    const mime = e?.response?.content?.mimeType ?? '';
    if (e.startMs <= snap.ts && /text\/html/i.test(mime) && e.request?.url) best = e.request.url;
  }
  return best;
}

function resolve(raw: string, base: string | null): string | null {
  const s = raw.trim();
  if (!s) return null;
  try {
    return base ? new URL(s, base).href : new URL(s).href;
  } catch {
    return null;
  }
}

function isData(u: string): boolean {
  return /^\s*data:/i.test(u);
}

function isDangerous(u: string): boolean {
  return /^\s*(javascript|vbscript):/i.test(u);
}

class Rewriter {
  readonly urls: string[] = [];
  private readonly made = new Map<string, string>();
  readonly stats: RebuildStats = { styled: false, sheets: 0, missingSheets: 0, missingImages: 0, rewritten: 0, targetMarked: false };

  constructor(
    private readonly files: Record<string, Uint8Array>,
    readonly map: Map<string, Resource>,
    private readonly toUrl?: RebuildOptions['toUrl'],
  ) {}

  bundleUrl(path: string, mime: string): string | null {
    const b = this.files[path];
    if (!b) return null;
    let u = this.made.get(path);
    if (!u) {
      if (this.toUrl) u = this.toUrl(path, mime, b);
      else {
        u = URL.createObjectURL(new Blob([b.slice()], { type: mime }));
        this.urls.push(u);
      }
      this.made.set(path, u);
    }
    return u;
  }

  /** A raw URL from the document -> a bundle URL, the same data: URL, or null (not available offline). */
  url(raw: string, base: string | null): string | null {
    if (isData(raw)) return raw.trim();
    if (isDangerous(raw) || /^\s*blob:/i.test(raw)) return null;
    const abs = resolve(raw, base);
    if (!abs) return null;
    const r = this.map.get(stripHash(abs));
    if (!r) return null;
    const u = this.bundleUrl(r.path, r.mime);
    if (u) this.stats.rewritten++;
    return u;
  }

  text(path: string): string | null {
    const b = this.files[path];
    return b ? dec.decode(b) : null;
  }

  /** CSS text with every url() / @import resolved against `base` and rewritten (missing -> none / dropped). */
  css(text: string, base: string | null): string {
    let out = text.replace(/@import\s+(?:url\(\s*)?(['"]?)([^'")\s;]+)\1\s*\)?([^;]*);/gi, (_m, _q, u: string, media: string) => {
      const abs = resolve(u, base);
      const r = abs ? this.map.get(stripHash(abs)) : undefined;
      const t = r ? this.text(r.path) : null;
      if (t == null) return '';
      const inner = this.css(t, abs);
      const m = media.trim();
      return m ? `@media ${m}{${inner}}` : inner;
    });
    out = out.replace(/url\(\s*(['"]?)(.*?)\1\s*\)/gi, (_m, _q, u: string) => {
      const v = this.url(u, base);
      return v ? `url("${v.replace(/"/g, '%22')}")` : 'none';
    });
    return out;
  }
}

function attrUrls(el: Element, rw: Rewriter, base: string | null): void {
  const tag = el.localName;
  // srcset: rewrite each candidate, drop the unavailable ones
  for (const a of ['srcset', 'imagesrcset']) {
    const v = el.getAttribute(a);
    if (v == null) continue;
    const kept: string[] = [];
    for (const cand of v.split(/,(?=\s*\S)/)) {
      const [u, ...d] = cand.trim().split(/\s+/);
      const r = u ? rw.url(u, base) : null;
      if (r) kept.push([r, ...d].join(' '));
    }
    if (kept.length) el.setAttribute(a, kept.join(', '));
    else el.removeAttribute(a);
  }
  for (const a of ['src', 'href', 'xlink:href', 'poster', 'data', 'background', 'action', 'formaction', 'ping', 'cite', 'longdesc', 'manifest', 'lowsrc']) {
    const v = el.getAttribute(a);
    if (v == null) continue;
    if (a === 'href' && (tag === 'a' || tag === 'area')) {
      // links are not followed from a snapshot: keep same-page anchors, drop the rest
      if (!v.trim().startsWith('#')) el.removeAttribute('href');
      continue;
    }
    if (a === 'href' && tag === 'use' && v.trim().startsWith('#')) continue;   // SVG same-document reference
    if (a === 'action' || a === 'formaction' || a === 'ping' || a === 'cite' || a === 'longdesc' || a === 'manifest') {
      el.removeAttribute(a);
      continue;
    }
    const r = rw.url(v, base);
    if (r) {
      el.setAttribute(a, r);
      continue;
    }
    el.removeAttribute(a);
    if ((tag === 'img' || (tag === 'input' && (el.getAttribute('type') || '').toLowerCase() === 'image')) && a === 'src') {
      placeholder(el);
      rw.stats.missingImages++;
    } else if ((tag === 'video' || tag === 'audio' || tag === 'source' || tag === 'image') && (a === 'src' || a === 'href' || a === 'xlink:href')) {
      rw.stats.missingImages++;
    }
  }
  const st = el.getAttribute('style');
  if (st && /url\(/i.test(st)) el.setAttribute('style', rw.css(st, base));
}

/** A sized slot instead of a broken image: an SVG of the element's width/height, else a transparent pixel. */
function placeholder(el: Element): void {
  const w = parseInt(el.getAttribute('width') || '', 10);
  const h = parseInt(el.getAttribute('height') || '', 10);
  if (w > 0 && h > 0) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="100%" height="100%" fill="#eef0f3"/><path d="M0 0L${w} ${h}M${w} 0L0 ${h}" stroke="#c9ced6" stroke-width="1"/></svg>`;
    el.setAttribute('src', 'data:image/svg+xml,' + encodeURIComponent(svg));
  } else {
    el.setAttribute('src', BLANK_GIF);
  }
  el.setAttribute('data-gatf-missing', '');
  if (!el.getAttribute('alt')) el.setAttribute('alt', '');
}

const DROP = 'script,noscript,base,object,embed,applet,portal,frame:not([srcdoc])';
const DROP_LINK = /^(preload|prefetch|modulepreload|preconnect|dns-prefetch|prerender|manifest|import|serviceworker)$/i;

/** Every element of the document, including those inside <template> contents (declarative shadow roots). */
function allElements(root: ParentNode): Element[] {
  const out: Element[] = [];
  const walk = (r: ParentNode) => {
    for (const el of Array.from(r.querySelectorAll('*'))) {
      out.push(el);
      if (el.localName === 'template') walk((el as HTMLTemplateElement).content);
    }
  };
  walk(root);
  return out;
}

function sanitize(doc: Document): void {
  for (const el of allElements(doc)) {
    const tag = el.localName;
    if (el.matches(DROP)) { el.remove(); continue; }
    if (tag === 'meta') {
      const he = (el.getAttribute('http-equiv') || '').trim().toLowerCase();
      if (he === 'refresh' || he.startsWith('content-security-policy') || he === 'set-cookie') { el.remove(); continue; }
    }
    if (tag === 'link') {
      const rel = (el.getAttribute('rel') || '').trim();
      if (rel.split(/\s+/).some((r) => DROP_LINK.test(r))) { el.remove(); continue; }
    }
    for (const a of Array.from(el.attributes)) {
      const n = a.name.toLowerCase();
      if (n.startsWith('on')) el.removeAttribute(a.name);
      else if (isDangerous(a.value) && /(^|:)(href|src|action|formaction|data|xlink:href|poster)$/.test(n)) el.removeAttribute(a.name);
    }
  }
}

function rebuildDoc(
  html: string,
  base: string | null,
  rw: Rewriter,
  manifest: DomManifest | null,
  highlight: number | null,
  rect: [number, number, number, number] | null,
  depth: number,
): string {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  sanitize(doc);

  // stylesheets: the manifest's CSSOM text first (styled capture), then the HAR body for any other linked sheet
  const styleEls = Array.from(doc.querySelectorAll('style'));
  const used = new Set<Element>();
  const head = doc.head ?? doc.documentElement;
  if (manifest && depth === 0) {
    for (const s of manifest.styles) {
      const text = s.ref ? rw.text(s.ref) : null;
      if (s.owner === 'style') {
        const el = s.index >= 0 ? styleEls[s.index] : undefined;
        if (el && text != null) { el.textContent = rw.css(text, s.href ?? base); used.add(el); rw.stats.sheets++; }
        continue;
      }
      if (s.owner === 'adopted') {
        if (text == null) continue;
        const el = doc.createElement('style');
        el.setAttribute('data-gatf-adopted', '');
        el.textContent = rw.css(text, s.href ?? base);
        head.appendChild(el);
        used.add(el);
        rw.stats.sheets++;
        continue;
      }
      // link: match the <link> by resolved href, else the next unused stylesheet link (CSSOM order is document
      // order; a <base> the capture removed can make the hrefs resolve differently)
      const links = Array.from(doc.querySelectorAll('link[href]')).filter((l) => !used.has(l) && /(^|\s)stylesheet(\s|$)/i.test(l.getAttribute('rel') || ''));
      const link =
        links.find((l) => resolve(l.getAttribute('href') || '', base) === s.href) ??
        links.find((l) => !/(^|\s)alternate(\s|$)/i.test(l.getAttribute('rel') || ''));
      if (!link) continue;
      used.add(link);
      if (/(^|\s)alternate(\s|$)/i.test(link.getAttribute('rel') || '')) { link.remove(); continue; }   // not applied live
      // cross-origin sheet (no CSSOM text): the HAR body for its URL, if the network recorder kept it
      const css = text ?? (s.href ? resourceText(rw, s.href) : null);
      if (css == null) { rw.stats.missingSheets++; link.remove(); continue; }
      inlineSheet(doc, link, rw.css(css, s.href), s.media);
      rw.stats.sheets++;
    }
  }
  for (const link of Array.from(doc.querySelectorAll('link'))) {
    if (used.has(link)) continue;
    const rel = (link.getAttribute('rel') || '').toLowerCase();
    const href = link.getAttribute('href') || '';
    if (/(^|\s)stylesheet(\s|$)/.test(rel)) {
      if (/(^|\s)alternate(\s|$)/.test(rel)) { link.remove(); continue; }
      const abs = resolve(href, base);
      const css = abs ? resourceText(rw, abs) : null;
      if (css == null) { rw.stats.missingSheets++; link.remove(); continue; }
      inlineSheet(doc, link, rw.css(css, abs), link.getAttribute('media'));
      rw.stats.sheets++;
    } else if (/icon/.test(rel)) {
      const r = rw.url(href, base);
      if (r) link.setAttribute('href', r); else link.remove();
    } else {
      link.remove();   // anything else a link could load (alternate, canonical, …) has no use offline
    }
  }
  // inline <style> (incl. shadow-root styles) keep their text; only their url()s are rewritten
  for (const el of allElements(doc)) {
    if (el.localName === 'style' && !used.has(el) && el.textContent && /url\(|@import/i.test(el.textContent)) {
      el.textContent = rw.css(el.textContent, base);
    }
  }
  for (const el of allElements(doc)) {
    if (el.localName === 'style' || el.localName === 'link') continue;
    if (el.localName === 'iframe') {
      const sd = el.getAttribute('srcdoc');
      el.removeAttribute('src');
      if (sd != null && depth < MAX_FRAME_DEPTH) {
        el.setAttribute('srcdoc', rebuildDoc(sd, base, rw, null, null, null, depth + 1));
      } else el.removeAttribute('srcdoc');
      continue;
    }
    attrUrls(el, rw, base);
  }

  // inertness + highlight, added last so nothing above rewrites them
  const meta = doc.createElement('meta');
  meta.setAttribute('http-equiv', 'Content-Security-Policy');
  meta.setAttribute('content', SNAPSHOT_CSP);
  head.insertBefore(meta, head.firstChild);
  const gatf = doc.createElement('style');
  gatf.setAttribute('data-gatf', '');
  let css = 'img[data-gatf-missing]{background:repeating-linear-gradient(45deg,#eef0f3 0 6px,#f7f8fa 6px 12px)}';
  if (highlight != null && depth === 0) {
    const sel = `[data-gatf-target~="${String(highlight).replace(/[^0-9]/g, '')}"]`;
    const hit = doc.querySelector(sel);
    rw.stats.targetMarked = !!hit;
    css += `${sel}{outline:2px solid #ef4444!important;outline-offset:1px!important;box-shadow:0 0 0 4px rgba(239,68,68,.25)!important}`;
    if (!hit && rect && doc.body) {
      const [x, y, w, h] = rect;
      const sx = manifest?.scroll?.x ?? 0;
      const sy = manifest?.scroll?.y ?? 0;
      const box = doc.createElement('div');
      box.setAttribute('data-gatf-rect', '');
      box.setAttribute(
        'style',
        `position:absolute;left:${x + sx}px;top:${y + sy}px;width:${w}px;height:${h}px;outline:2px solid #ef4444;` +
          'background:rgba(239,68,68,.12);pointer-events:none;z-index:2147483647',
      );
      doc.body.appendChild(box);
    }
  }
  gatf.textContent = css;
  head.appendChild(gatf);
  return '<!DOCTYPE html>' + doc.documentElement.outerHTML;
}

function resourceText(rw: Rewriter, absUrl: string): string | null {
  const r = rw.map.get(stripHash(absUrl));
  return r ? rw.text(r.path) : null;
}

function inlineSheet(doc: Document, link: Element, css: string, media: string | null | undefined): void {
  const st = doc.createElement('style');
  st.setAttribute('data-gatf-href', link.getAttribute('href') || '');
  if (media && media.trim() && media.trim() !== 'all') st.setAttribute('media', media);
  st.textContent = css;
  link.replaceWith(st);
}

/** Rebuild `snap` for display; null when the bundle has no HTML for it. */
export function rebuildSnapshot(trace: Pick<Trace, 'files' | 'har'>, snap: SnapshotRef, opts: RebuildOptions = {}): Rebuilt | null {
  if (!snap.html) return null;
  const raw = trace.files[snap.html];
  if (!raw) return null;
  const manifest = domManifest(trace.files, snap);
  const rw = new Rewriter(trace.files, resourceMap(trace), opts.toUrl);
  rw.stats.styled = !!manifest;
  const base = manifest?.url ?? liteBase(trace, snap);
  const highlight = opts.highlight !== undefined ? opts.highlight : snap.action ?? null;
  const html = rebuildDoc(dec.decode(raw), base, rw, manifest, highlight, snap.target?.rect ?? null, 0);
  return { html, urls: rw.urls, manifest, stats: rw.stats };
}
