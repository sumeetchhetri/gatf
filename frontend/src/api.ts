// Client for the read-only JSON API (Phase 3.x) exposed by GatfApiHandler at /api.
// These endpoints are additive over the legacy Grizzly handlers; writes still go through
// the classic config-tool at "/". Absolute paths so they resolve regardless of the /app base.

export interface ReportArtifact {
  name: string;
  url: string;
  size: number;
  modified: number;
  dataUrl?: string;
  // trace bundles produced by this run (attached to the run's single report row)
  traces?: ReportArtifact[];
}
export interface ReportsIndex {
  dir: string;
  reports: ReportArtifact[];
}

// ---- report data (from the <report>.data.json sidecars ReportHandler writes) ----
// selenium summary tree: node -> runs[] -> test -> browser -> subtests[]
// each subtest is an Object[] = [name, href, status, error, time]
export type SeleniumSubtest = (string | number | null)[];
export type SeleniumRun = Record<string, Record<string, SeleniumSubtest[]>>;
export interface SeleniumSummaryData {
  type: 'selenium-summary';
  sessionId: number;
  attempt: number;
  testsMap: Record<string, SeleniumRun[]>;
  sinfoMap?: Record<string, unknown>;
}
export interface SeleniumTestData {
  type: 'selenium-test';
  selFileName: string;
  selCode: string;
  javaFileName: string;
  javaCode: string;
  logs: unknown;
  status: boolean;
  tracePath: string | null;
}
export interface SeleniumFinalData {
  type: 'selenium-final';
  sessionId: number;
  attempt: number;
  indexes: Record<string, Record<string, string>>;
  dt: string;
}

// ---- API-test report ----
export interface TestGroupStats {
  sourceFile: string;
  baseUrl?: string;
  executionTime: number;
  totalTestCount: number;
  failedTestCount: number;
  skippedTestCount: number;
  totalRuns: number;
  failedRuns: number;
}
export interface TestSuiteStats {
  totalTestCount: number;
  failedTestCount: number;
  skippedTestCount: number;
  totalRuns: number;
  failedRuns: number;
  totalSuiteRuns: number;
  totalUserSuiteRuns: number;
  executionTime: number;
  actualExecutionTime: number;
  groupStats: TestGroupStats[];
}
export interface TestCaseStat {
  identifier: string;
  sourceFileName: string;
  workflowName: string;
  testCaseName: string;
  executionTime: number;
  status: string;
}
export interface TestCaseReport {
  workflowName?: string;
  testIdentifier?: string;
  url?: string;
  actualUrl?: string;
  method?: string;
  status: string;
  responseStatusCode?: string | number;
  executionTime?: number;
  averageExecutionTime?: number;
  numberOfRuns?: number;
  error?: string;
  errorText?: string;
  failureReason?: string;
  requestContent?: string;
  requestContentType?: string;
  requestHeaders?: string;
  responseContent?: string;
  responseContentType?: string;
  responseHeaders?: string;
  resHeaders?: string;
  curlCmd?: string;
  serverLogs?: string;
  [k: string]: unknown;
}
export interface ApiReportData {
  type: 'api-report';
  suiteStats: TestSuiteStats;
  testcaseStats: TestCaseStat[];
  testcaseReports: Record<string, TestCaseReport[]>;
  compareStats?: Record<string, unknown>;
}
export interface ApiIndexData {
  type: 'api-index';
  suiteStats: TestSuiteStats;
  testcaseStats: TestCaseStat[];
}

// ---- load-test report ----
export interface LoadTestResource {
  title: string;
  url: string;
}
export interface LoadReportData {
  type: 'load-report';
  suiteStats: TestSuiteStats;
  loadTestResources?: LoadTestResource[];
  nodes?: string[];
  nodeurls?: string[];
}
export interface DistributedLoadData {
  type: 'load-distributed';
  suiteStats: TestSuiteStats[];
  nodes: string[];
  loadTestResources?: LoadTestResource[][];
}

// ---- test-analysis (percentile) report ----
// each entry is [90th, 50th] ms
export interface TAReportData {
  type: 'ta-report';
  testcaseTAReports: Record<string, number[]>;
  runTAReports: Record<string, number[]>;
}
export type ReportData =
  | SeleniumSummaryData
  | SeleniumTestData
  | SeleniumFinalData
  | ApiReportData
  | ApiIndexData
  | LoadReportData
  | DistributedLoadData
  | TAReportData
  | ({ type: string } & Record<string, unknown>);

export interface TestFile {
  name: string;
  path: string;
  size: number;
  modified: number;
  kind: 'sel' | 'xml' | 'other';
}
export interface TestFilesIndex {
  dir: string;
  files: TestFile[];
}

export interface TestFileContent {
  path: string;
  kind: string;
  size: number;
  truncated: boolean;
  content: string;
}

export interface ConfigResponse {
  testDir: string | null;
  outDir: string | null;
  config: Record<string, unknown>;
}

// ---- test files (Slice C) ----
// GET /testcasefiles → [[relativePath, attrBase64], ...]. relativePath IS the testcaseFileName the
// /testcases + /testcasefiles CRUD endpoints expect (relative to the configured test-case dir).
export type TestcaseFileEntry = { path: string; attr: string };
// A .sel parse error detail from a failed save: [_, lineNo, lineText, message].
export type SelParseError = (string | number | null)[];
export type SaveSelResult = { ok: true } | { ok: false; errors: SelParseError[]; message?: string };
// Structured API test case (subset we render; the full TestCase has many more fields).
export interface ApiTestCase {
  name?: string;
  url?: string;
  method?: string;
  secure?: boolean;
  [k: string]: unknown;
}

function authToken(): string {
  try {
    return sessionStorage.getItem('token') || '';
  } catch {
    return '';
  }
}
// Append ?token= like the classic config-tool's $.ajaxPrefilter, so the shell works when auth is on.
function withTok(url: string): string {
  const t = authToken();
  if (!t) return url;
  return url + (url.indexOf('?') === -1 ? '?' : '&') + 'token=' + encodeURIComponent(t);
}

async function getJson<T>(url: string): Promise<T> {
  const r = await fetch(withTok(url), { headers: { Accept: 'application/json' } });
  if (!r.ok) throw new Error(`HTTP ${r.status} for ${url}`);
  return (await r.json()) as T;
}

// ---- auth / login (Slice E) ----
// The classic gates on GET /misc: a 401 there means auth is enabled and the token is missing/stale.
// Login is POST /login (form-encoded) → {token}, stored in sessionStorage and appended as ?token= by withTok.
async function probeAuth(): Promise<'ok' | 'auth' | 'error'> {
  try {
    const r = await fetch(withTok('/misc'), { headers: { Accept: 'application/json' } });
    if (r.status === 401) return 'auth';
    return r.ok ? 'ok' : 'error'; // other failures don't force a login wall
  } catch {
    return 'error';
  }
}
async function loginReq(username: string, password: string): Promise<{ ok: boolean; error?: string }> {
  try {
    const r = await fetch('/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
      body: 'username=' + encodeURIComponent(username) + '&password=' + encodeURIComponent(password),
    });
    if (!r.ok) return { ok: false, error: r.status === 401 ? 'Invalid username or password' : `HTTP ${r.status}` };
    const d = (await r.json()) as { token?: string };
    if (d && d.token) {
      sessionStorage.setItem('token', String(d.token));
      return { ok: true };
    }
    return { ok: false, error: 'No token returned' };
  } catch (e) {
    return { ok: false, error: (e as Error).message };
  }
}

async function getText(url: string): Promise<string> {
  const r = await fetch(withTok(url), { headers: { Accept: 'text/plain, application/json' } });
  if (!r.ok) throw new Error(`HTTP ${r.status} for ${url}`);
  return r.text();
}

// POST JSON to a legacy handler (e.g. /configure). On failure, surface the server's error text
// (the config-tool returns a plain-text/JSON error body with a 400).
async function postJson<T>(url: string, body: unknown): Promise<T> {
  const r = await fetch(withTok(url), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(body),
  });
  const text = await r.text();
  if (!r.ok) {
    let msg = text;
    try {
      const j = JSON.parse(text);
      msg = (j && (j.message || j.error)) || text;
    } catch {
      /* plain text */
    }
    throw new Error(msg || `HTTP ${r.status}`);
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    return text as unknown as T;
  }
}

// Generic write to a legacy handler (create/rename/delete). Surfaces the server error text.
async function sendReq(
  method: string,
  url: string,
  body?: string,
  contentType?: string,
  extraHeaders?: Record<string, string>,
): Promise<string> {
  const headers: Record<string, string> = { Accept: 'application/json', ...(extraHeaders || {}) };
  if (contentType) headers['Content-Type'] = contentType;
  const r = await fetch(withTok(url), { method, headers, body });
  const text = await r.text();
  if (!r.ok) {
    let msg = text;
    try {
      const j = JSON.parse(text);
      msg = (j && (j.message || j.error)) || text;
    } catch {
      /* plain text */
    }
    throw new Error(msg || `HTTP ${r.status}`);
  }
  return text;
}

// Grizzly's HTTP codec rejects an encoded slash (%2F) anywhere in the request URI with a 404, before
// any handler runs — so a query-param value that is a hierarchical path (e.g. api/restful-api-dev.xml)
// must carry a real '/', not %2F. A literal slash is legal in a query string; encode everything else.
const encPath = (p: string) => encodeURIComponent(p).replace(/%2F/gi, '/');
const tcf = (p: string) => 'testcaseFileName=' + encPath(p);

// ---- execution (Slice D1) ----
// Both success (200) and the "in progress"/failure (400) responses carry a JSON body we need, so
// execFetch never throws on status — it returns { ok, data }. `error` is a status string, or an
// array [lineText, line, relFile, msg] (+ `others`) when the run failed with a parse/runtime error.
export interface ExecStatus {
  error: string | (string | number | null)[];
  is_paused?: boolean;
  others?: (string | number | null)[][];
  loadTestingEnabled?: boolean;
  [k: string]: unknown;
}
async function execFetch(
  method: string,
  pluginType: string,
  files?: string[],
): Promise<{ ok: boolean; data: ExecStatus }> {
  const r = await fetch(withTok('/execute?pluginType=' + encodeURIComponent(pluginType)), {
    method,
    headers: files ? { 'Content-Type': 'application/json', Accept: 'application/json' } : { Accept: 'application/json' },
    body: files ? JSON.stringify(files) : undefined,
  });
  const text = await r.text();
  let data: ExecStatus;
  try {
    data = JSON.parse(text) as ExecStatus;
  } catch {
    data = { error: text || `HTTP ${r.status}` };
  }
  return { ok: r.ok, data };
}

// ---- single-test run + pause-on-element (Slice D2b) ----
// PUT /reports?action=playTest&testcaseFileName&testCaseName runs one test; poll pausedLineNo for the
// live line + pause state; pauseElSearch/unPauseElSearch toggle the pause, restartSubtest / stop control it.
export interface RunPaused {
  is_paused?: boolean;
  line?: number; // 1-based; -1 when not at a line
  file?: string;
  running?: boolean;
  error?: string | unknown[];
  m?: string;
  [k: string]: unknown;
}
async function runPut(action: string, extra = ''): Promise<RunPaused> {
  const r = await fetch(withTok('/reports?action=' + action + extra), {
    method: 'PUT',
    headers: { Accept: 'application/json' },
  });
  const text = await r.text();
  try {
    const j = JSON.parse(text) as RunPaused;
    return typeof j === 'object' && j ? j : { m: String(text) };
  } catch {
    return { m: text || `HTTP ${r.status}` };
  }
}

// ---- online debugger (Slice D2) ----
// -5 suspend; -6 terminate; -7 heartbeat {s,r,p,n,v,t}; b<n>/r<n> add/remove breakpoint (server uses n+1).
export interface DbgResponse {
  s?: boolean; // command accepted / session ok
  c?: string; // source code (init)
  l?: unknown; // debuggable lines map (init)
  i?: number; // initial current line (init, 1-based)
  p?: number; // prevLine (current paused line)
  n?: number; // nextLine
  r?: boolean; // running (heartbeat)
  v?: number; // debugger state (heartbeat = last action code)
  t?: string; // current file (heartbeat)
  m?: string; // error message
  [k: string]: unknown;
}
async function dbgFetch(
  lineCode: string,
  tcf: string,
  tc: string,
  sessionId: string,
  sline?: string,
): Promise<DbgResponse> {
  let url =
    '/reports?action=debug&line=' +
    encodeURIComponent(lineCode) +
    '&testcaseFileName=' +
    encPath(tcf) +
    '&testCaseName=' +
    encodeURIComponent(tc) +
    '&sessionId=' +
    encodeURIComponent(sessionId);
  if (sline != null) url += '&sline=' + encodeURIComponent(sline);
  const r = await fetch(withTok(url), { method: 'PUT', headers: { Accept: 'application/json' } });
  const text = await r.text();
  // note: the handler also returns plain-text "Fail: …" for bad command/session
  try {
    const j = JSON.parse(text) as DbgResponse;
    return typeof j === 'object' && j ? j : { s: false, m: String(text) };
  } catch {
    return { s: false, m: text || `HTTP ${r.status}` };
  }
}

// Flatten the debuggableLines map (l) for a file into a set of 1-based line numbers.
export function debugLinesFor(l: unknown, file: string): Set<number> {
  const out = new Set<number>();
  const root = l && typeof l === 'object' ? (l as Record<string, unknown>)[file] : undefined;
  const walk = (x: unknown) => {
    if (Array.isArray(x)) {
      if (typeof x[0] === 'number') out.add(x[0]);
      x.forEach(walk);
    }
  };
  walk(root);
  return out;
}

export const api = {
  reports: () => getJson<ReportsIndex>('/api/reports'),
  config: () => getJson<ConfigResponse>('/api/config'),
  testfiles: () => getJson<TestFilesIndex>('/api/testfiles'),
  testfile: (path: string) => getJson<TestFileContent>('/api/testfile?path=' + encodeURIComponent(path)),
  reportData: (url: string) => getJson<ReportData>(url),
  // ---- config editor (Slice B) — authoritative round-trip via the legacy /configure handler ----
  misc: () => getJson<Record<string, unknown>>('/misc'),
  // ---- auth (Slice E) ----
  probeAuth: () => probeAuth(),
  login: (username: string, password: string) => loginReq(username, password),
  logout: () => sessionStorage.removeItem('token'),
  // ---- data source statistics (Slice E) ----
  dsStats: () => getJson<Record<string, string[][]>>('/profile?dsnames=all'),
  configExecutor: () => getJson<Record<string, unknown>>('/configure?configType=executor'),
  saveConfigExecutor: (cfg: Record<string, unknown>) =>
    postJson<Record<string, unknown>>('/configure?configType=executor', cfg),
  // generic config by type (executor | generator | …)
  configByType: (configType: string) =>
    getJson<Record<string, unknown>>('/configure?configType=' + encodeURIComponent(configType)),
  saveConfig: (configType: string, cfg: Record<string, unknown>) =>
    postJson<Record<string, unknown>>('/configure?configType=' + encodeURIComponent(configType), cfg),
  // ---- test files (Slice C) ----
  testcaseFiles: async (): Promise<TestcaseFileEntry[]> => {
    const raw = await getJson<[string, string | null][]>('/testcasefiles');
    return (raw || []).map(([path, attr]) => ({ path, attr: attr || '' }));
  },
  readSel: (path: string) => getText('/testcases?' + tcf(path)),
  // declared subtests in a .sel file (for @call autocomplete)
  subtests: (path: string) => getJson<string[]>('/testcasefiles?' + tcf(path) + '&allsubtests=true'),
  // cross-file navigation (C2.1): resolve an include/import/config/dynprops target → [targetFile]
  resolveRef: (path: string, ref: string) =>
    getJson<string[]>('/testcasefiles?' + tcf(path) + '&referencedFile=' + encPath(ref)),
  // resolve an @call subtest reference → [targetFile, lineNo]
  resolveSubtest: (path: string, call: string) =>
    getJson<[string, number]>('/testcasefiles?' + tcf(path) + '&possibleSubtestFuncCall=' + encodeURIComponent(call)),
  readXmlCases: (path: string, configType?: string) =>
    getJson<ApiTestCase[]>('/testcases?' + tcf(path) + (configType ? '&configType=' + encodeURIComponent(configType) : '')),
  // Save raw .sel content; the server writes it and parse-validates — returns parse errors on failure.
  saveSel: async (path: string, text: string): Promise<SaveSelResult> => {
    const r = await fetch(withTok('/testcases?' + tcf(path)), {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      body: text,
    });
    if (r.ok) return { ok: true };
    const body = await r.text();
    try {
      const j = JSON.parse(body) as { error?: SelParseError; others?: SelParseError[] };
      const errors: SelParseError[] = [];
      if (j.error) errors.push(j.error);
      if (Array.isArray(j.others)) errors.push(...j.others);
      return errors.length ? { ok: false, errors } : { ok: false, errors: [], message: body };
    } catch {
      return { ok: false, errors: [], message: body || `HTTP ${r.status}` };
    }
  },
  createTestcaseFile: (path: string) => sendReq('POST', '/testcasefiles?' + tcf(path)),
  renameTestcaseFile: (from: string, to: string) =>
    sendReq('PUT', '/testcasefiles?' + tcf(from) + '&testcaseFileNameTo=' + encPath(to)),
  deleteTestcaseFile: (path: string) => sendReq('DELETE', '/testcasefiles?' + tcf(path)),
  // structured .xml API test-case CRUD (TestCase JSON; server validates)
  addCase: (path: string, tc: ApiTestCase, configType?: string) =>
    sendReq(
      'POST',
      '/testcases?' + tcf(path) + (configType ? '&configType=' + encodeURIComponent(configType) : ''),
      JSON.stringify(tc),
      'application/json',
    ),
  updateCase: (path: string, origName: string, tc: ApiTestCase, configType?: string) =>
    sendReq(
      'PUT',
      '/testcases?' +
        tcf(path) +
        '&tcName=' +
        encodeURIComponent(origName) +
        (configType ? '&configType=' + encodeURIComponent(configType) : ''),
      JSON.stringify(tc),
      'application/json',
    ),
  deleteCase: (path: string, name: string, configType?: string) =>
    sendReq(
      'DELETE',
      '/testcases?' + tcf(path) + (configType ? '&configType=' + encodeURIComponent(configType) : ''),
      undefined,
      undefined,
      { testcasename: name },
    ),
  // ---- execution (Slice D1) — async start-then-poll over /execute ----
  execStart: (pluginType: string, files: string[]) => execFetch('PUT', pluginType, files),
  execPoll: (pluginType: string) => execFetch('GET', pluginType),
  execStop: (pluginType: string) => execFetch('DELETE', pluginType),
  // ---- online debugger (Slice D2) — /reports?action=debug protocol ----
  dbg: (lineCode: string, tcf: string, tc: string, sessionId: string, sline?: string) =>
    dbgFetch(lineCode, tcf, tc, sessionId, sline),
  // Server-push channel (delta-040): SSE stream of {s,r,p,n,v,t}. Consumed via EventSource; replaces the
  // ~900ms heartbeat poll. Commands still go over dbg() (PUT). Token is appended like every other call.
  debugStreamUrl: (sessionId: string) =>
    withTok('/reports?action=debugStream&sessionId=' + encodeURIComponent(sessionId)),
  // WebSocket push channel (delta-041): ws(s)://<host>/gatfjdb. The client sends the sessionId as its first
  // message; the server pushes the same {s,r,p,n,v,t} frames. Preferred over SSE, which stays as fallback.
  debugWsUrl: () =>
    (location.protocol === 'https:' ? 'wss:' : 'ws:') + '//' + location.host + '/gatfjdb',
  // ---- single-test run + pause-on-element (Slice D2b) ----
  playTest: (tcf: string, tc: string) =>
    runPut(
      'playTest',
      '&testcaseFileName=' + encPath(tcf) + '&testCaseName=' + encodeURIComponent(tc),
    ),
  pausedLine: () => runPut('pausedLineNo'),
  pauseEl: (pause: boolean) => runPut(pause ? 'pauseElSearch' : 'unPauseElSearch'),
  restartSubtest: () => runPut('restartSubtest'),
  stopRun: () => runPut('stop'),
};
