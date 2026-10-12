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
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { render, screen, fireEvent } from '@testing-library/react';
import { strToU8 } from 'fflate';
import { loadTraceZip, parseFiles } from './loadTrace';
import { rebuildSnapshot, resourceMap, BLANK_GIF, SNAPSHOT_CSP } from './rebuild';
import { DomView } from '../components/Stage';
import type { Trace, SnapshotRef } from './types';

// dom-v2.trace.zip: written by the real Δ146 recorders (TraceRecorder, SnapshotRecorder, NetworkRecorder) from the
// Chromium 141 DOMSnapshot fixture: actions 1 (type #name) and 2 (click #go); HAR bodies for index.html, s.css,
// i.css and a.png; bg.png answered 404 (no body).
const zip = readFileSync(resolve(process.cwd(), 'src/trace/fixtures/dom-v2.trace.zip'));
const load = () => loadTraceZip(new Uint8Array(zip));
// data: URLs instead of blob: so the output is inspectable (and renders anywhere)
const toUrl = (_p: string, mime: string, b: Uint8Array) => `data:${mime};base64,${Buffer.from(b).toString('base64')}`;
const snap = (t: Trace, action: number, phase: string) => t.snapshots.find((s) => s.action === action && s.phase === phase)!;
const parse = (html: string) => new DOMParser().parseFromString(html, 'text/html');

describe('rebuildSnapshot (styled, format 2)', () => {
  it('renders the snapshot styled from the bundle and inert', () => {
    const t = load();
    expect(t.meta.format).toBe(2);
    const r = rebuildSnapshot(t, snap(t, 2, 'before'), { toUrl })!;
    const d = parse(r.html);
    expect(r.stats.styled).toBe(true);
    // stylesheets: the linked sheet inlined (with its @import), CSS-in-JS rules restored, the adopted sheet added
    const linked = d.querySelector('style[data-gatf-href="/s.css"]')!;
    expect(linked.textContent).toContain('.btn');
    expect(linked.textContent).toContain('.imported');
    expect(d.querySelector('link')).toBeNull();
    expect(d.getElementById('cssinjs')!.textContent).toContain('.injected');
    expect(d.querySelector('style[data-gatf-adopted]')!.textContent).toContain('rgb(1, 2, 3)');
    expect(r.stats.sheets).toBe(3);
    // resources: the captured image comes from the bundle; the 404 background becomes none
    expect(d.getElementById('im')!.getAttribute('src')).toMatch(/^data:image\/png;base64,/);
    expect(linked.textContent).not.toContain('bg.png');
    expect(linked.textContent).toContain('background: none');
    // state + structure kept
    expect((d.getElementById('name') as HTMLInputElement).getAttribute('value')).toBe('typed <&> "q"');
    expect(d.querySelector('#host > template[shadowrootmode="open"]')).not.toBeNull();
    const frame = d.getElementById('fr')!;
    expect(frame.getAttribute('srcdoc')).toContain('frame body');
    expect(frame.getAttribute('srcdoc')).toContain('Content-Security-Policy');
    // target highlight for action 2 only
    expect(d.getElementById('go')!.getAttribute('data-gatf-target')).toContain('2');
    expect(d.querySelector('style[data-gatf]')!.textContent).toContain('[data-gatf-target~="2"]{outline');
    expect(r.stats.targetMarked).toBe(true);
    // inert: CSP first in head, nothing live, no script
    expect(d.head.firstElementChild!.getAttribute('content')).toBe(SNAPSHOT_CSP);
    expect(r.html).not.toMatch(/<script|onclick=|javascript:|http:\/\/127\.0\.0\.1/i);
    expect(r.urls).toEqual([]);   // custom toUrl: nothing to revoke
  });

  it('after(1) and before(2) are the same capture; each highlights its own action', () => {
    const t = load();
    const a1 = snap(t, 1, 'after');
    const b2 = snap(t, 2, 'before');
    expect(a1.html).toBe(b2.html);
    const h1 = rebuildSnapshot(t, a1, { toUrl })!.html;
    expect(h1).toContain('[data-gatf-target~="1"]{outline');
    expect(parse(h1).getElementById('name')!.getAttribute('data-gatf-target')).toBe('1');
  });

  it('degrades cleanly without HAR bodies (traceNetwork off): placeholders, no broken images, CSSOM text still styles', () => {
    const t = load();
    const files = { ...t.files };
    delete files['network.har'];
    const t2 = parseFiles(files);
    expect(resourceMap(t2).size).toBe(0);
    const r = rebuildSnapshot(t2, snap(t2, 2, 'before'), { toUrl })!;
    const d = parse(r.html);
    const img = d.getElementById('im')!;
    expect(img.getAttribute('src')).toBe(BLANK_GIF);
    expect(img.hasAttribute('data-gatf-missing')).toBe(true);
    expect(img.hasAttribute('srcset')).toBe(false);
    expect(r.stats.missingImages).toBe(1);
    expect(d.querySelector('style[data-gatf-href="/s.css"]')!.textContent).toContain('.btn');   // from the manifest
    expect(r.html).not.toMatch(/http:\/\/127\.0\.0\.1/);
  });

  it('creates one object URL per bundle file and reports them for revocation', () => {
    const made: string[] = [];
    const orig = URL.createObjectURL;
    URL.createObjectURL = (() => { const u = 'blob:x/' + made.length; made.push(u); return u; }) as typeof URL.createObjectURL;
    try {
      const t = load();
      const r = rebuildSnapshot(t, snap(t, 2, 'before'))!;
      expect(r.urls).toEqual(made);
      expect(new Set(r.urls).size).toBe(r.urls.length);
      expect(r.html).toContain('src="blob:x/');
    } finally {
      URL.createObjectURL = orig;
    }
  });
});

describe('rebuildSnapshot (lite and hostile input)', () => {
  const enc = (s: string) => strToU8(s);
  function bundle(html: string, extra: Record<string, Uint8Array> = {}, snapOver: Partial<SnapshotRef> = {}) {
    const har = { log: { version: '1.2', creator: { name: 'gatf', version: 'trace' }, entries: [
      { startedDateTime: new Date(1000).toISOString(), time: 1, request: { method: 'GET', url: 'https://app.test/p/index.html', httpVersion: '', headers: [] },
        response: { status: 200, statusText: 'OK', httpVersion: '', headers: [], content: { size: 1, mimeType: 'text/html', _file: 'resources/h' }, bodySize: 1 } },
      { startedDateTime: new Date(1001).toISOString(), time: 1, request: { method: 'GET', url: 'https://app.test/p/site.css', httpVersion: '', headers: [] },
        response: { status: 200, statusText: 'OK', httpVersion: '', headers: [], content: { size: 1, mimeType: 'text/css; charset=utf-8', _file: 'resources/c' }, bodySize: 1 } },
    ] } };
    const files: Record<string, Uint8Array> = {
      'snapshots/snapshot-1.html': enc(html),
      'snapshots.ndjson': enc(JSON.stringify({ n: 1, ts: 2000, label: 'x', html: 'snapshots/snapshot-1.html', screenshot: null, ...snapOver }) + '\n'),
      'network.har': enc(JSON.stringify(har)),
      'resources/h': enc('<html></html>'),
      'resources/c': enc('.site{color:red;background:url(img/bg.png)} @font-face{src:url(https://cdn.evil/f.woff)}'),
      ...extra,
    };
    return parseFiles(files);
  }

  it('lite outerHTML: stylesheet from the HAR body, scripts and handlers stripped, page URL from the HAR', () => {
    const t = bundle('<html><head><link rel="stylesheet" href="site.css"><script>steal()</script></head><body onload="x()"><p class="site" onclick="y()">hi</p></body></html>');
    const r = rebuildSnapshot(t, t.snapshots[0], { toUrl })!;
    expect(r.stats.styled).toBe(false);
    expect(r.stats.sheets).toBe(1);
    expect(r.html).toContain('.site{color:red;background:none}');
    expect(r.html).toContain('@font-face{src:none}');
    expect(r.html).not.toMatch(/<script|onload=|onclick=|cdn\.evil/);
  });

  it('neutralises hostile markup and never leaves a live URL', () => {
    const t = bundle(
      '<html><head><meta http-equiv="refresh" content="0;url=https://evil.test/"><base href="https://evil.test/">' +
        '<style>@import url(https://evil.test/x.css); .a{background:url(javascript:alert(1))}</style>' +
        '<link rel="preload" href="https://evil.test/p.js"><link rel="stylesheet" href="https://evil.test/s.css"></head><body>' +
        '<img src="https://evil.test/t.gif" width="40" height="30" srcset="https://evil.test/a.png 2x" onerror="alert(1)">' +
        '<a href="javascript:alert(1)">a</a><a href="https://evil.test/">b</a><a href="#top">c</a>' +
        '<iframe src="https://evil.test/f"></iframe><object data="https://evil.test/o"></object><embed src="https://evil.test/e">' +
        '<svg><script>alert(1)</script><image href="https://evil.test/i.png"/><use href="#g"/></svg>' +
        '<form action="https://evil.test/post"><input type="image" src="https://evil.test/btn.png"></form>' +
        '<div style="background-image:url(https://evil.test/d.png)">d</div>' +
        '<template shadowrootmode="open"><img src="https://evil.test/shadow.png"><style>p{background:url(https://evil.test/s.png)}</style></template>' +
        '</body></html>',
    );
    const r = rebuildSnapshot(t, t.snapshots[0], { toUrl })!;
    expect(r.html).not.toMatch(/evil\.test|javascript:|<script|onerror|<base|<object|<embed|http-equiv="refresh"/i);
    const d = parse(r.html);
    // sized placeholder keeps the layout; no broken-image icon
    const img = d.querySelector('img[width="40"]')!;
    expect(decodeURIComponent(img.getAttribute('src')!)).toContain('width="40" height="30"');
    expect(d.querySelector('a[href="#top"]')).not.toBeNull();
    expect(d.querySelector('use')!.getAttribute('href')).toBe('#g');
    expect(r.stats.missingSheets).toBe(1);
    expect(r.stats.missingImages).toBeGreaterThanOrEqual(3);
  });

  it('marks the target by rect when the element was not marked', () => {
    const t = bundle('<html><body><p>x</p></body></html>', {}, { action: 7, phase: 'before', v: 1, target: { rect: [10, 20, 30, 40] } });
    const r = rebuildSnapshot(t, t.snapshots[0], { toUrl })!;
    expect(r.stats.targetMarked).toBe(false);
    const box = parse(r.html).querySelector('[data-gatf-rect]')!;
    expect(box.getAttribute('style')).toContain('left:10px;top:20px;width:30px;height:40px');
  });
});

describe('rebuildSnapshot (edge cases)', () => {
  it('matches a sheet by order when hrefs differ, skips alternate sheets, ignores a stale style index', () => {
    const enc = (x: string) => strToU8(x);
    const files: Record<string, Uint8Array> = {
      'snapshots/s.html': enc('<html><head><link rel="alternate stylesheet" href="alt.css"><link rel="stylesheet" href="css/a.css"></head><body><p class="a">x</p></body></html>'),
      'snapshots/s.json': enc(JSON.stringify({ v: 2, source: 'script', url: 'https://x.test/p/', title: '', viewport: { w: 1, h: 1 }, scroll: { x: 0, y: 0 }, truncated: false,
        styles: [{ owner: 'link', href: 'https://x.test/static/css/a.css', media: '', index: -1, ref: 'resources/a' }, { owner: 'style', href: null, media: '', index: 9, ref: 'resources/a' }] })),
      'resources/a': enc('.a{color:red}'),
      'snapshots.ndjson': enc(JSON.stringify({ n: 1, ts: 1, label: null, html: 'snapshots/s.html', screenshot: null, v: 2, dom: 'snapshots/s.json' }) + '\n'),
    };
    const t = parseFiles(files);
    const r = rebuildSnapshot(t, t.snapshots[0], { toUrl })!;
    const d = parse(r.html);
    expect(d.querySelectorAll('link').length).toBe(0);
    expect(d.querySelectorAll('style[data-gatf-href]').length).toBe(1);
    expect(d.querySelector('style[data-gatf-href]')!.textContent).toBe('.a{color:red}');
    expect(r.stats.sheets).toBe(1);
  });
});

describe('DomView', () => {
  it('renders an old lite snapshot without the toggle', () => {
    const files: Record<string, Uint8Array> = {
      'snapshots/snapshot-1.html': strToU8('<html><body><h1>old</h1><script>x()</script></body></html>'),
      'snapshots.ndjson': strToU8(JSON.stringify({ n: 1, ts: 1, label: 'old', html: 'snapshots/snapshot-1.html', screenshot: null }) + '\n'),
    };
    const t = parseFiles(files);
    render(<DomView trace={t} snap={t.snapshots[0]} />);
    expect(screen.queryByText('Before')).toBeNull();
    expect(screen.getByText(/Lite snapshot \(outerHTML\)/)).toBeTruthy();
    const sd = (screen.getAllByTitle('dom-snapshot').pop() as HTMLIFrameElement).getAttribute('srcdoc')!;
    expect(sd).toContain('<h1>old</h1>');
    expect(sd).not.toContain('<script');
  });

  it('sandboxes without scripts and toggles before/after within the action', () => {
    const t = load();
    URL.createObjectURL = (() => 'blob:v') as typeof URL.createObjectURL;
    URL.revokeObjectURL = (() => {}) as typeof URL.revokeObjectURL;
    render(<DomView trace={t} snap={snap(t, 2, 'before')} />);
    const frame = screen.getByTitle('dom-snapshot') as HTMLIFrameElement;
    expect(frame.getAttribute('sandbox')).toBe('allow-same-origin');
    const before = frame.getAttribute('srcdoc')!;
    expect(before).toContain('[data-gatf-target~="2"]');
    expect(screen.getByText(/Styled snapshot \(DOMSnapshot\), 3 stylesheets/)).toBeTruthy();
    fireEvent.click(screen.getByText('After'));
    const after = (screen.getByTitle('dom-snapshot') as HTMLIFrameElement).getAttribute('srcdoc')!;
    expect(after).not.toBe(before);                                   // capture 3 (end), not capture 2
    expect(parse(after).getElementById('go')!.getAttribute('data-gatf-target')).toBe('2');
    fireEvent.click(screen.getByText('Before'));
    expect((screen.getByTitle('dom-snapshot') as HTMLIFrameElement).getAttribute('srcdoc')).toBe(before);
  });
});
