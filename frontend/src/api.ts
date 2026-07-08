// Client for the read-only JSON API (Phase 3.x) exposed by GatfApiHandler at /api.
// These endpoints are additive over the legacy Grizzly handlers; writes still go through
// the classic config-tool at "/". Absolute paths so they resolve regardless of the /app base.

export interface ReportArtifact {
  name: string;
  url: string;
  size: number;
  modified: number;
  dataUrl?: string;
}
export interface ReportsIndex {
  dir: string;
  reports: ReportArtifact[];
  traces: ReportArtifact[];
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

const tcf = (p: string) => 'testcaseFileName=' + encodeURIComponent(p);

export const api = {
  reports: () => getJson<ReportsIndex>('/api/reports'),
  config: () => getJson<ConfigResponse>('/api/config'),
  testfiles: () => getJson<TestFilesIndex>('/api/testfiles'),
  testfile: (path: string) => getJson<TestFileContent>('/api/testfile?path=' + encodeURIComponent(path)),
  reportData: (url: string) => getJson<ReportData>(url),
  // ---- config editor (Slice B) — authoritative round-trip via the legacy /configure handler ----
  misc: () => getJson<Record<string, unknown>>('/misc'),
  configExecutor: () => getJson<Record<string, unknown>>('/configure?configType=executor'),
  saveConfigExecutor: (cfg: Record<string, unknown>) =>
    postJson<Record<string, unknown>>('/configure?configType=executor', cfg),
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
    getJson<string[]>('/testcasefiles?' + tcf(path) + '&referencedFile=' + encodeURIComponent(ref)),
  // resolve an @call subtest reference → [targetFile, lineNo]
  resolveSubtest: (path: string, call: string) =>
    getJson<[string, number]>('/testcasefiles?' + tcf(path) + '&possibleSubtestFuncCall=' + encodeURIComponent(call)),
  readXmlCases: (path: string) => getJson<ApiTestCase[]>('/testcases?' + tcf(path)),
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
    sendReq('PUT', '/testcasefiles?' + tcf(from) + '&testcaseFileNameTo=' + encodeURIComponent(to)),
  deleteTestcaseFile: (path: string) => sendReq('DELETE', '/testcasefiles?' + tcf(path)),
  // structured .xml API test-case CRUD (TestCase JSON; server validates)
  addCase: (path: string, tc: ApiTestCase) =>
    sendReq('POST', '/testcases?' + tcf(path), JSON.stringify(tc), 'application/json'),
  updateCase: (path: string, origName: string, tc: ApiTestCase) =>
    sendReq('PUT', '/testcases?' + tcf(path) + '&tcName=' + encodeURIComponent(origName), JSON.stringify(tc), 'application/json'),
  deleteCase: (path: string, name: string) =>
    sendReq('DELETE', '/testcases?' + tcf(path), undefined, undefined, { testcasename: name }),
};
