#!/usr/bin/env node
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
// webkit-caps-shim.mjs -- CI glue for the experimental WebKitGTK lane (Δ159).
//
// GATF has no "webkitgtk" browser key, so the lane runs scripts as `open firefox-dkr` with
// SELENIUM_REMOTE_URL pointing here. This proxy forwards every WebDriver call to WebKitWebDriver
// unchanged, except POST /session, where it rewrites the new-session capabilities:
//   - drops browserName/browserVersion (WebKitWebDriver would refuse "firefox") and every moz:* key;
//   - adds webkitgtk:browserOptions {binary: MiniBrowser, args: ["--automation"]};
//   - if the driver refuses webSocketUrl (no BiDi; GATF asks for it when tracing or for `route`),
//     retries once without it and logs "[webkit-shim] this WebKitWebDriver refused webSocketUrl ...".
// Pure function rewriteSession()/withoutWebSocketUrl() are exported for the node self-test (--self-test).
//
// Usage: node webkit-caps-shim.mjs <listenPort> <driverPort> <MiniBrowser path>
//        node webkit-caps-shim.mjs --self-test
import http from 'node:http';
import { pathToFileURL } from 'node:url';

export function rewriteSession(body, binary) {
  const req = JSON.parse(body || '{}');
  const strip = (o) => {
    if (!o || typeof o !== 'object') return o;
    const r = {};
    for (const [k, v] of Object.entries(o)) {
      if (k === 'browserName' || k === 'browserVersion' || k.startsWith('moz:')) continue;
      r[k] = v;
    }
    return r;
  };
  const caps = req.capabilities || {};
  const always = strip(caps.alwaysMatch || {});
  always['webkitgtk:browserOptions'] = { binary, args: ['--automation'] };
  const out = { capabilities: { alwaysMatch: always } };
  if (Array.isArray(caps.firstMatch)) {
    const fm = caps.firstMatch.map(strip).filter((o) => o && Object.keys(o).length);
    if (fm.length) out.capabilities.firstMatch = fm;
  }
  return JSON.stringify(out);
}

/** The same session request minus webSocketUrl (retry when the driver has no BiDi). */
export function withoutWebSocketUrl(body) {
  const req = JSON.parse(body);
  const c = req.capabilities || {};
  if (c.alwaysMatch) delete c.alwaysMatch.webSocketUrl;
  for (const f of c.firstMatch || []) delete f.webSocketUrl;
  return JSON.stringify(req);
}

function serve(listenPort, driverPort, binary) {
  http.createServer((req, res) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      let body = Buffer.concat(chunks);
      const isNew = req.method === 'POST' && /^\/session\/?$/.test(req.url.split('?')[0]);
      if (isNew) {
        try {
          body = Buffer.from(rewriteSession(body.toString('utf8'), binary), 'utf8');
          console.error(`[webkit-shim] new session -> ${body.toString('utf8')}`);
        } catch (e) {
          console.error(`[webkit-shim] could not rewrite new-session body (${e.message}); forwarding as-is`);
        }
      }
      const send = (b, retried) => {
        const headers = { ...req.headers, host: `127.0.0.1:${driverPort}`, 'content-length': String(b.length) };
        delete headers['transfer-encoding'];
        delete headers.connection;
        const up = http.request({ host: '127.0.0.1', port: driverPort, method: req.method, path: req.url, headers }, (ur) => {
          if (!isNew) { res.writeHead(ur.statusCode, ur.headers); ur.pipe(res); return; }
          const rc = [];
          ur.on('data', (c) => rc.push(c));
          ur.on('end', () => {
            const rb = Buffer.concat(rc);
            console.error(`[webkit-shim] new session <- ${ur.statusCode} ${rb.toString('utf8').slice(0, 2000)}`);
            const noBidi = !retried && ur.statusCode >= 400 && rb.toString('utf8').includes('session not created')
              && b.toString('utf8').includes('"webSocketUrl"');
            if (noBidi) {
              console.error('[webkit-shim] this WebKitWebDriver refused webSocketUrl (no WebDriver BiDi): retrying without it; BiDi-only features are unavailable');
              send(Buffer.from(withoutWebSocketUrl(b.toString('utf8')), 'utf8'), true);
              return;
            }
            res.writeHead(ur.statusCode, ur.headers);
            res.end(rb);
          });
        });
        up.on('error', (e) => {
          res.writeHead(502, { 'content-type': 'application/json' });
          res.end(JSON.stringify({ value: { error: 'unknown error', message: `webkit-shim: WebKitWebDriver unreachable: ${e.message}` } }));
        });
        up.end(b);
      };
      send(body, false);
    });
  }).listen(listenPort, '127.0.0.1', () => console.error(`[webkit-shim] :${listenPort} -> WebKitWebDriver :${driverPort} (binary ${binary})`));
}

function selfTest() {
  const sel = JSON.stringify({ capabilities: {
    alwaysMatch: { browserName: 'firefox', acceptInsecureCerts: true, 'moz:firefoxOptions': { args: [] }, webSocketUrl: true },
    firstMatch: [{ 'moz:debuggerAddress': true }, { pageLoadStrategy: 'normal' }] } });
  const r = JSON.parse(rewriteSession(sel, '/x/MiniBrowser')).capabilities;
  const ok = (c, m) => { if (!c) { console.log('FAIL ' + m); process.exitCode = 1; } else console.log('ok   ' + m); };
  ok(!('browserName' in r.alwaysMatch), 'browserName dropped');
  ok(!Object.keys(r.alwaysMatch).some((k) => k.startsWith('moz:')), 'moz:* dropped');
  ok(r.alwaysMatch.acceptInsecureCerts === true && r.alwaysMatch.webSocketUrl === true, 'W3C caps kept');
  ok(r.alwaysMatch['webkitgtk:browserOptions'].binary === '/x/MiniBrowser', 'browserOptions.binary set');
  ok(r.alwaysMatch['webkitgtk:browserOptions'].args[0] === '--automation', '--automation passed');
  ok(r.firstMatch.length === 1 && r.firstMatch[0].pageLoadStrategy === 'normal', 'empty firstMatch entries removed');
  const nw = JSON.parse(withoutWebSocketUrl(rewriteSession(sel, '/b'))).capabilities;
  ok(!('webSocketUrl' in nw.alwaysMatch) && nw.alwaysMatch.acceptInsecureCerts === true, 'withoutWebSocketUrl drops only webSocketUrl');
  ok(JSON.parse(rewriteSession('', '/b')).capabilities.alwaysMatch['webkitgtk:browserOptions'].binary === '/b', 'empty body tolerated');
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv[2] === '--self-test') selfTest();
  else {
    const [lp, dp, bin] = process.argv.slice(2);
    if (!lp || !dp || !bin) { console.error('usage: webkit-caps-shim.mjs <listenPort> <driverPort> <MiniBrowser path>'); process.exit(2); }
    serve(Number(lp), Number(dp), bin);
  }
}
