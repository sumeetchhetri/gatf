// Client for the read-only JSON API (Phase 3.x) exposed by GatfApiHandler at /api.
// These endpoints are additive over the legacy Grizzly handlers; writes still go through
// the classic config-tool at "/". Absolute paths so they resolve regardless of the /app base.

export interface ReportArtifact {
  name: string;
  url: string;
  size: number;
  modified: number;
}
export interface ReportsIndex {
  dir: string;
  reports: ReportArtifact[];
  traces: ReportArtifact[];
}

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
};
