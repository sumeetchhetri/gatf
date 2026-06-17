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
export type ReportData =
  | SeleniumSummaryData
  | SeleniumTestData
  | SeleniumFinalData
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
