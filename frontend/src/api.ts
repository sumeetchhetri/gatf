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
  | LoadReportData
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

async function getJson<T>(url: string): Promise<T> {
  const r = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!r.ok) throw new Error(`HTTP ${r.status} for ${url}`);
  return (await r.json()) as T;
}

export const api = {
  reports: () => getJson<ReportsIndex>('/api/reports'),
  config: () => getJson<ConfigResponse>('/api/config'),
  testfiles: () => getJson<TestFilesIndex>('/api/testfiles'),
  testfile: (path: string) => getJson<TestFileContent>('/api/testfile?path=' + encodeURIComponent(path)),
  reportData: (url: string) => getJson<ReportData>(url),
};
