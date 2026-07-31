#!/usr/bin/env node
/*
 * GATF sample REST server — a tiny, dependency-free HTTP/JSON target for
 * exercising GATF's load / performance / concurrent engines (and the API
 * executor in general). Node core `http` only, so it runs on any Node 16+
 * with no `npm install`.
 *
 *   node server.js                 # :8880, no artificial latency
 *   node server.js --port 9000 --latency 25 --jitter 15
 *   PORT=9000 LATENCY_MS=25 JITTER_MS=15 node server.js
 *
 * Flags (all optional; CLI overrides env overrides default):
 *   --port <n>       listen port                 (env PORT,      default 8880)
 *   --latency <ms>   base delay per response     (env LATENCY_MS, default 0)
 *   --jitter <ms>    +/- random delay on top     (env JITTER_MS,  default 0)
 *   --fail-rate <f>  0..1 fraction of /api/*      (env FAIL_RATE,  default 0)
 *                    calls that return HTTP 500  (to exercise RAG / failures)
 *   --quiet          suppress the per-second RPS line
 *
 * Designed to make percentile reports interesting: /api/slow?ms=N delays N ms,
 * base latency+jitter apply to every route, and --fail-rate injects errors.
 *
 * Routes (see README.md for the matching GATF test files):
 *   GET    /health                 -> 200 {status,uptimeMs,requests}
 *   POST   /auth/login             -> 200 {token,username}          (any creds)
 *   GET    /api/users              -> 200 [ {id,name,role}, ... ]
 *   POST   /api/users              -> 201 {id,name,role}            (body echoed)
 *   GET    /api/users/:id          -> 200 {id,...} | 404
 *   PUT    /api/users/:id          -> 200 {id,...}   | 404
 *   DELETE /api/users/:id          -> 200 {message}  | 404
 *   GET    /api/slow?ms=N          -> 200 after N ms extra delay
 *   ANY    /api/echo               -> 200 {method,headers,query,body}
 *   GET    /api/status/:code       -> <code> {code}                 (any status)
 *   GET    /metrics                -> 200 {requests,byRoute,startedAt}
 */
'use strict';

const http = require('http');
const { URL } = require('url');

// ---- config ---------------------------------------------------------------
function argOf(name) {
  const i = process.argv.indexOf('--' + name);
  return i !== -1 && i + 1 < process.argv.length ? process.argv[i + 1] : undefined;
}
function flag(name) { return process.argv.indexOf('--' + name) !== -1; }
function num(v, d) { const n = Number(v); return Number.isFinite(n) ? n : d; }

const PORT      = num(argOf('port')     ?? process.env.PORT,       8880);
const LATENCY   = num(argOf('latency')  ?? process.env.LATENCY_MS, 0);
const JITTER    = num(argOf('jitter')   ?? process.env.JITTER_MS,  0);
const FAIL_RATE = num(argOf('fail-rate')?? process.env.FAIL_RATE,  0);
const QUIET     = flag('quiet');

// ---- state ----------------------------------------------------------------
const startedAt = new Date();
let seq = 100;
const users = new Map(); // id -> user
[['alice', 'nurse'], ['bob', 'doctor'], ['carol', 'admin']].forEach(([name, role]) => {
  const id = ++seq; users.set(String(id), { id, name, role });
});
let totalRequests = 0;
let windowRequests = 0;
const byRoute = Object.create(null);

// ---- helpers --------------------------------------------------------------
function sleep(ms) { return ms > 0 ? new Promise((r) => setTimeout(r, ms)) : Promise.resolve(); }
function baseDelay() {
  let d = LATENCY;
  if (JITTER > 0) d += Math.floor((Math.random() * 2 - 1) * JITTER);
  return d > 0 ? d : 0;
}
function send(res, code, obj, extraHeaders) {
  const body = obj === undefined ? '' : JSON.stringify(obj);
  const headers = Object.assign(
    { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) },
    extraHeaders || {}
  );
  res.writeHead(code, headers);
  res.end(body);
}
function readBody(req) {
  return new Promise((resolve) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => { size += c.length; if (size <= 5 * 1024 * 1024) chunks.push(c); });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({ raw: '', json: undefined });
      try { resolve({ raw, json: JSON.parse(raw) }); } catch { resolve({ raw, json: undefined }); }
    });
    req.on('error', () => resolve({ raw: '', json: undefined }));
  });
}
function track(route) { totalRequests++; windowRequests++; byRoute[route] = (byRoute[route] || 0) + 1; }

// ---- router ---------------------------------------------------------------
async function route(req, res) {
  const u = new URL(req.url, 'http://localhost');
  const path = u.pathname.replace(/\/+$/, '') || '/';
  const parts = path.split('/').filter(Boolean); // e.g. ['api','users','101']
  const method = req.method.toUpperCase();

  await sleep(baseDelay());

  // GET /health -------------------------------------------------------------
  if (path === '/health' && method === 'GET') {
    track('GET /health');
    return send(res, 200, { status: 'ok', uptimeMs: Date.now() - startedAt.getTime(), requests: totalRequests });
  }

  // GET /metrics ------------------------------------------------------------
  if (path === '/metrics' && method === 'GET') {
    track('GET /metrics');
    return send(res, 200, { requests: totalRequests, byRoute, startedAt: startedAt.toISOString() });
  }

  // POST /auth/login  -> issues a token (accepts anything) ------------------
  if (path === '/auth/login' && method === 'POST') {
    track('POST /auth/login');
    const { json } = await readBody(req);
    const username = (json && (json.username || json.user)) || 'anonymous';
    const token = 'tok_' + Buffer.from(username + ':' + Date.now()).toString('base64').replace(/=+$/, '');
    return send(res, 200, { token, username });
  }

  // /api/* fault injection --------------------------------------------------
  if (parts[0] === 'api' && FAIL_RATE > 0 && Math.random() < FAIL_RATE) {
    track(method + ' ' + path + ' [injected-500]');
    return send(res, 500, { error: 'injected failure', route: path });
  }

  // GET /api/slow?ms=N ------------------------------------------------------
  if (path === '/api/slow' && method === 'GET') {
    const extra = num(u.searchParams.get('ms'), 250);
    await sleep(extra);
    track('GET /api/slow');
    return send(res, 200, { sleptMs: extra, at: new Date().toISOString() });
  }

  // ANY /api/echo -----------------------------------------------------------
  if (path === '/api/echo') {
    const { json, raw } = await readBody(req);
    track(method + ' /api/echo');
    return send(res, 200, {
      method,
      query: Object.fromEntries(u.searchParams),
      headers: req.headers,
      body: json !== undefined ? json : raw,
    });
  }

  // GET /api/status/:code ---------------------------------------------------
  if (parts[0] === 'api' && parts[1] === 'status' && method === 'GET') {
    const code = Math.min(599, Math.max(100, num(parts[2], 200)));
    track('GET /api/status/:code');
    return send(res, code, { code });
  }

  // /api/users collection ---------------------------------------------------
  if (parts[0] === 'api' && parts[1] === 'users' && parts.length === 2) {
    if (method === 'GET') {
      track('GET /api/users');
      return send(res, 200, Array.from(users.values()));
    }
    if (method === 'POST') {
      const { json } = await readBody(req);
      const id = ++seq;
      const user = { id, name: (json && json.name) || ('user-' + id), role: (json && json.role) || 'member' };
      users.set(String(id), user);
      track('POST /api/users');
      return send(res, 201, user, { Location: '/api/users/' + id });
    }
  }

  // /api/users/:id item -----------------------------------------------------
  if (parts[0] === 'api' && parts[1] === 'users' && parts.length === 3) {
    const id = parts[2];
    const existing = users.get(id);
    if (method === 'GET') {
      track('GET /api/users/:id');
      return existing ? send(res, 200, existing) : send(res, 404, { error: 'not found', id });
    }
    if (method === 'PUT') {
      if (!existing) return send(res, 404, { error: 'not found', id });
      const { json } = await readBody(req);
      const updated = Object.assign({}, existing, json || {}, { id: existing.id });
      users.set(id, updated);
      track('PUT /api/users/:id');
      return send(res, 200, updated);
    }
    if (method === 'DELETE') {
      if (!existing) return send(res, 404, { error: 'not found', id });
      users.delete(id);
      track('DELETE /api/users/:id');
      return send(res, 200, { message: 'deleted', id });
    }
  }

  track(method + ' ' + path + ' [404]');
  return send(res, 404, { error: 'no route', method, path });
}

// ---- server ---------------------------------------------------------------
const server = http.createServer((req, res) => {
  route(req, res).catch((e) => {
    try { send(res, 500, { error: String(e && e.message || e) }); } catch { /* ignore */ }
  });
});
server.keepAliveTimeout = 60_000;

server.listen(PORT, () => {
  console.log(`[gatf-sample-server] listening on http://localhost:${PORT}` +
    `  (latency=${LATENCY}ms jitter=${JITTER}ms failRate=${FAIL_RATE})`);
});

let rpsTimer;
if (!QUIET) {
  rpsTimer = setInterval(() => {
    if (windowRequests > 0) {
      console.log(`[gatf-sample-server] ${windowRequests} req/s  (total ${totalRequests})`);
      windowRequests = 0;
    }
  }, 1000);
  rpsTimer.unref();
}

function shutdown() {
  console.log('\n[gatf-sample-server] shutting down…');
  if (rpsTimer) clearInterval(rpsTimer);
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1500).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
