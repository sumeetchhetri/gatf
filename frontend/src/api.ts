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
// Client for the read-only JSON API (Phase 3.x) exposed by GatfApiHandler at /api.
// These endpoints are additive over the legacy Grizzly handlers; writes still go through
// the classic config-tool at "/". Absolute paths so they resolve regardless of the /app base.

export interface ReportArtifact {
  name: string;
  url: string;
  size: number;
  modified: number;
  // 'API' | 'Selenium' | 'Load' — classified from the .data.json type by the backend
  type?: string;
  // display label: the run folder (raw index.html filename stripped)
  label?: string;
  dataUrl?: string;
  // trace bundles produced by this run (attached to the run's single report row)
  traces?: ReportArtifact[];
}
export interface ReportsIndex {
  dir: string;
  reports: ReportArtifact[];
}
export interface ConfigInfo {
  name: string; // relative path used as the ?config= value
  file: string; // basename
  source: string; // 'root' | 'configs'
  size: number;
  modified: number;
}
export interface ConfigsIndex {
  configs: ConfigInfo[];
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
  // Q3: report-relative failure screenshot (<report>-failure.png); absent on passing tests and older reports
  screenshot?: string | null;
  // Q9/Q10: performance budgets + passive security findings from the script's trace
  checks?: TraceChecks | null;
}
export interface BudgetResult {
  metric: string;
  budget: string;
  actual: number | null;
  display: string | null;
  outcome: 'pass' | 'breach' | 'not_measured';
  message: string;
}
export interface SecurityFinding {
  severity: 'high' | 'medium' | 'low' | string;
  rule: string;
  page: string;
  url: string | null;
  message: string;
}
export interface TraceChecks {
  budgets: BudgetResult[];
  security: SecurityFinding[] | null; // null = security checks off
  failure: string | null;
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
const ACTIVE_CONFIG_KEY = 'gatf.activeConfig';
let activeConfig: string | null = (() => {
  try {
    const v = localStorage.getItem(ACTIVE_CONFIG_KEY);
    return v && v.trim() ? v : null;
  } catch {
    return null;
  }
})();
export function setActiveConfig(c: string | null) {
  activeConfig = c && c.trim() ? c : null;
  try {
    if (activeConfig) localStorage.setItem(ACTIVE_CONFIG_KEY, activeConfig);
    else localStorage.removeItem(ACTIVE_CONFIG_KEY);
  } catch {
    /* localStorage unavailable — persistence is best-effort */
  }
}
export function getActiveConfig(): string | null {
  return activeConfig;
}

function withTok(url: string): string {
  let u = url;
  const t = authToken();
  if (t) u = u + (u.indexOf('?') === -1 ? '?' : '&') + 'token=' + encodeURIComponent(t);
  if (activeConfig) u = u + (u.indexOf('?') === -1 ? '?' : '&') + 'config=' + encodeURIComponent(activeConfig);
  return u;
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
// Live load/distributed reporting (delta-064): while a load run is in progress the /execute GET poll
// carries per-run samples the classic UI graphed live. Server format (RuntimeReportUtil.getEntry):
//   lstats[i] = [prefix, runNo, reportUrl, node, epochMs, [total,failed,skipped,execMs], selStats|null, fname|null]
//   sstats[i] = "node|runNo|1|0"  (pipe: node, run, pass-flag, …)   gstats = rolling TestSuiteStats
export type LoadStatEntry = [
  string, // 0 prefix ("Run"/"L")
  string, // 1 runNo
  string | null, // 2 report file (→ /reports/<file>)
  string | null, // 3 node
  number, // 4 epoch ms
  number[], // 5 [total, failed, skipped, execMs]
  unknown, // 6 selenium sub-stats (usually null for API load)
  string | null, // 7 optional label
];
export interface LiveGStats {
  totalSuiteRuns?: number;
  totalTestCount?: number;
  failedTestCount?: number;
  skippedTestCount?: number;
  executionTime?: number;
  actualExecutionTime?: number;
  [k: string]: unknown;
}
export interface ExecStatus {
  error: string | (string | number | null)[];
  is_paused?: boolean;
  others?: (string | number | null)[][];
  loadTestingEnabled?: boolean;
  lstats?: LoadStatEntry[];
  sstats?: string[];
  gstats?: LiveGStats;
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
// Debug protocol line codes for PUT /reports?action=debug (GatfReportsHandler dispatch). Breakpoint
// codes carry the 0-based gutter index — the server adds 1 — so pass the 1-based line here.
export const DBG = {
  INIT: '0',
  STEP_INTO: '-1',
  STEP_OUT: '-2',
  STEP_OVER: '-3',
  CONTINUE: '-4',
  SUSPEND: '-5',
  TERMINATE: '-6',
  HEARTBEAT: '-7',
  addBreakpoint: (line1: number) => 'b' + (line1 - 1),
  removeBreakpoint: (line1: number) => 'r' + (line1 - 1),
} as const;

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

// ---- test importers (Phase 11 Playwright/JMeter, Q2 cURL/Postman/HAR) — POST source to /import, preview or save ----
export type ImportType = 'playwright' | 'jmeter' | 'curl' | 'postman' | 'har' | 'openapi';
export interface ImportUnsupported {
  line: number;
  construct: string;
  reason: string;
}
export interface ImportResultDto {
  type: string;
  fileName: string;
  kind: 'sel' | 'xml';
  content: string;
  unsupported: ImportUnsupported[];
  stats: Record<string, number | string | Record<string, number>>;
  loadConfig?: Record<string, number | boolean>; // JMeter: first ThreadGroup as gatf-config load keys
  variables?: Record<string, string>; // Postman collection variables / OpenAPI auth placeholders for gatf-config globalVariables
  saved?: boolean;
  savedPath?: string;
  validation?: string;
  error?: string;
}
async function importSourceReq(
  type: ImportType,
  fileName: string,
  save: boolean,
  source: string,
  filter?: string,
): Promise<ImportResultDto> {
  const qs =
    '/import?type=' +
    encodeURIComponent(type) +
    (fileName ? '&fileName=' + encodeURIComponent(fileName) : '') +
    (save ? '&save=true' : '') +
    (filter ? '&filter=' + encodeURIComponent(filter) : '');
  const r = await fetch(withTok(qs), {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain; charset=utf-8', Accept: 'application/json' },
    body: source,
  });
  const d = (await r.json().catch(() => ({}))) as ImportResultDto;
  if (!r.ok) return { ...d, error: d?.error || 'HTTP ' + r.status } as ImportResultDto;
  return d;
}

// ---- record mode (Phase 11) — drive a live browser recording, poll steps, stop/save ----
export interface RecordStart {
  sessionId?: string;
  running?: boolean;
  error?: string;
}
export interface RecordSteps {
  running: boolean;
  lines: string[];
  count: number;
  error?: string;
}
export interface RecordStop {
  content?: string;
  lines?: string[];
  count?: number;
  error?: string;
}
export interface RecordSave {
  saved?: boolean;
  savedPath?: string;
  validation?: string;
  error?: string;
}
async function recordPost<T>(qs: string): Promise<T> {
  const r = await fetch(withTok(qs), { method: 'POST', headers: { Accept: 'application/json' } });
  const d = (await r.json().catch(() => ({}))) as T & { error?: string };
  if (!r.ok && !d.error) (d as { error?: string }).error = 'HTTP ' + r.status;
  return d as T;
}

// ---- SPA crawler (G3) — start a browser-driven crawl, poll progress, fetch result, save ----
export interface CrawlStartBody {
  seed: string;
  maxPages?: number;
  maxDepth?: number;
  loginUrl?: string;
  username?: string;
  password?: string;
  userSel?: string;
  passSel?: string;
  submitSel?: string;
  submitForms?: boolean;
  exclude?: string;
  headless?: boolean;
  browser?: 'chrome' | 'firefox' | 'edge';
  suiteName?: string;
}
export interface CrawlPage {
  url: string;
  title?: string;
  depth: number;
  forms: number;
  fields: number;
  buttons: number;
  error?: string;
}
export interface CrawlStatus {
  running: boolean;
  done: boolean;
  visited: number;
  maxPages: number;
  queued: number;
  current?: string | null;
  elapsedMs: number;
  pages: CrawlPage[];
  log: string[];
  loginWarning?: string;
  error?: string;
}
export interface CoverageItem {
  url: string;
  title?: string;
  depth: number;
  forms: number;
  fields: number;
  required: number;
  buttons: number;
  links: number;
  score: number;
  priority: 'high' | 'medium' | 'low' | 'error';
  reasons: string;
}
export interface CrawlResult {
  content?: string;
  coverage?: CoverageItem[];
  error?: string;
}
export interface CrawlSave {
  savedPath?: string;
  coveragePath?: string;
  validation?: string;
  error?: string;
}
async function crawlCall<T>(qs: string, method: 'GET' | 'POST', body?: unknown): Promise<T> {
  const r = await fetch(withTok(qs), {
    method,
    headers: body ? { 'Content-Type': 'application/json', Accept: 'application/json' } : { Accept: 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const d = (await r.json().catch(() => ({}))) as T & { error?: string };
  if (!r.ok && !d.error) (d as { error?: string }).error = 'HTTP ' + r.status;
  return d as T;
}

export const api = {
  reports: () => getJson<ReportsIndex>('/api/reports'),
  configs: () => getJson<ConfigsIndex>('/api/configs'),
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
  // ---- API test Play / Replay (delta-064) ----
  // Play  = run the test AS DEFINED in its file (no body; ignores the captured request).
  // Replay= re-run the CAPTURED request from the failed TestCaseReport (actual URL / body / headers as sent).
  // Both return the fresh TestCaseReport (status / response / headers refreshed).
  playApiTest: async (fileName: string, testCaseName: string): Promise<TestCaseReport> => {
    const t = await sendReq(
      'PUT',
      '/reports?action=playTest&' + tcf(fileName) + '&testCaseName=' + encodeURIComponent(testCaseName),
    );
    return JSON.parse(t) as TestCaseReport;
  },
  replayApiTest: async (
    fileName: string,
    testCaseName: string,
    report: TestCaseReport,
  ): Promise<TestCaseReport> => {
    const t = await sendReq(
      'PUT',
      '/reports?action=replayTest&' + tcf(fileName) + '&testCaseName=' + encodeURIComponent(testCaseName),
      JSON.stringify(report),
      'application/json',
    );
    return JSON.parse(t) as TestCaseReport;
  },
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
  // ---- test importers (Phase 11 / Q2) ----
  importSource: (type: ImportType, fileName: string, save: boolean, source: string, filter?: string) =>
    importSourceReq(type, fileName, save, source, filter),
  // ---- SPA crawler (G3) ----
  crawlStart: (b: CrawlStartBody) => crawlCall<{ sessionId?: string; error?: string }>('/crawl?action=start', 'POST', b),
  crawlStatus: (id: string) => crawlCall<CrawlStatus>('/crawl?action=status&sessionId=' + encodeURIComponent(id), 'GET'),
  crawlStop: (id: string) => crawlCall<CrawlStatus>('/crawl?action=stop&sessionId=' + encodeURIComponent(id), 'POST'),
  crawlResult: (id: string) => crawlCall<CrawlResult>('/crawl?action=result&sessionId=' + encodeURIComponent(id), 'GET'),
  crawlSave: (id: string, fileName: string) =>
    crawlCall<CrawlSave>('/crawl?action=save&sessionId=' + encodeURIComponent(id) + '&fileName=' + encodeURIComponent(fileName), 'POST'),
  // ---- record mode (Phase 11) ----
  recordStart: (url: string, browser = 'chrome') =>
    recordPost<RecordStart>('/record?action=start&url=' + encodeURIComponent(url) + '&browser=' + encodeURIComponent(browser)),
  recordSteps: (sessionId: string) =>
    getJson<RecordSteps>('/record?action=steps&sessionId=' + encodeURIComponent(sessionId)),
  recordStop: (sessionId: string) =>
    recordPost<RecordStop>('/record?action=stop&sessionId=' + encodeURIComponent(sessionId)),
  recordSave: (sessionId: string, fileName: string) =>
    recordPost<RecordSave>(
      '/record?action=save&sessionId=' + encodeURIComponent(sessionId) + '&fileName=' + encodeURIComponent(fileName),
    ),
};
