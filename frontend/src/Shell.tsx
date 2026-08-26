import { useEffect, useState } from 'react';
import App from './App';
import { api, setActiveConfig, getActiveConfig } from './api';
import Nav from './components/Nav';
import Login from './components/Login';
import ReportsView from './views/ReportsView';
import ReportViewer from './views/ReportViewer';
import ConfigView from './views/ConfigView';
import TestFilesView from './views/TestFilesView';
import ExecuteView from './views/ExecuteView';
import DataSourceStats from './views/DataSourceStats';
import ExtApiConfig from './views/ExtApiConfig';
import ImportView from './views/ImportView';
import RecordView from './views/RecordView';

// Compare is no longer a Shell view (it's invoked from the test-file tree). The Trace Viewer is reached
// from Reports rather than being a top-level tab, so it maps to 'reports' in the nav highlight.
export type ViewKey =
  | 'viewer'
  | 'reports'
  | 'config'
  | 'testfiles'
  | 'execute'
  | 'datasources'
  | 'integrations'
  | 'import'
  | 'record'
  | 'report';

const HASH: Record<string, ViewKey> = {
  '': 'reports',
  '#': 'reports',
  '#/': 'reports',
  '#/reports': 'reports',
  '#/viewer': 'viewer',
  '#/config': 'config',
  '#/testfiles': 'testfiles',
  '#/execute': 'execute',
  '#/datasources': 'datasources',
  '#/integrations': 'integrations',
  '#/import': 'import',
  '#/record': 'record',
};
const TO_HASH: Record<ViewKey, string> = {
  viewer: '#/viewer',
  reports: '#/reports',
  config: '#/config',
  testfiles: '#/testfiles',
  execute: '#/execute',
  datasources: '#/datasources',
  integrations: '#/integrations',
  import: '#/import',
  record: '#/record',
  report: '#/reports',
};

function currentView(): ViewKey {
  return HASH[window.location.hash] ?? 'reports';
}

function initialReport(): string | undefined {
  return new URLSearchParams(window.location.search).get('report') || undefined;
}
function hasTraceParam(): boolean {
  return !!new URLSearchParams(window.location.search).get('trace');
}

export default function Shell() {
  const [view, setView] = useState<ViewKey>(
    initialReport() ? 'report' : hasTraceParam() ? 'viewer' : currentView(),
  );
  const [pendingTrace, setPendingTrace] = useState<string | undefined>(undefined);
  const [pendingReport, setPendingReport] = useState<string | undefined>(initialReport());
  const [auth, setAuth] = useState<'checking' | 'ok' | 'required'>('checking');
  const [cfg, setCfg] = useState<string>(getActiveConfig() ?? '');
  const changeCfg = (name: string) => {
    setActiveConfig(name || null); // set module state synchronously so the remounted view refetches with it
    setCfg(name);
  };

  const checkAuth = () =>
    api.probeAuth().then((s) => setAuth(s === 'auth' ? 'required' : 'ok'));
  useEffect(() => {
    checkAuth();
  }, []);

  useEffect(() => {
    const onHash = () => setView(currentView());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  const [pendingTestFile, setPendingTestFile] = useState<string | undefined>(undefined);
  const navigate = (v: ViewKey) => {
    if (window.location.hash !== TO_HASH[v]) window.location.hash = TO_HASH[v];
    setView(v);
  };

  const openTrace = (url: string) => {
    setPendingTrace(url);
    navigate('viewer');
  };
  const openTestFile = (path: string) => {
    setPendingTestFile(path);
    navigate('testfiles');
  };
  // open the trace viewer with no preloaded bundle (drag-and-drop a trace.zip); framed under Reports
  const openViewer = () => navigate('viewer');

  const openReport = (dataUrl: string) => {
    setPendingReport(dataUrl);
    setView('report'); // reached from Reports; not a top-level hash route
  };

  if (auth === 'checking') return <div className="view-note" style={{ padding: 24 }}>Loading…</div>;
  if (auth === 'required') return <Login onLogin={checkAuth} />;

  return (
    <div className="shell">
      <Nav
        active={view === 'report' || view === 'viewer' ? 'reports' : view}
        onNavigate={navigate}
        configValue={cfg}
        onConfigChange={changeCfg}
      />
      <div className="shell-body">
        {/* Viewer stays mounted so a loaded trace survives tab switches. */}
        <div className="view-viewer" style={{ display: view === 'viewer' ? 'flex' : 'none' }}>
          {view === 'viewer' && (
            <div className="viewer-topbar">
              <button className="btn ghost small" onClick={() => navigate('reports')}>
                {'\u2039'} Reports
              </button>
              <span className="muted small">Trace Viewer</span>
            </div>
          )}
          <App traceUrl={pendingTrace} />
        </div>
        {view === 'reports' && (
          <ReportsView key={cfg} onOpenTrace={openTrace} onOpenReport={openReport} onOpenViewer={openViewer} />
        )}
        {view === 'report' && pendingReport && (
          <ReportViewer
            dataUrl={pendingReport}
            onBack={() => navigate('reports')}
            onOpenTrace={openTrace}
            onOpenReport={openReport}
          />
        )}
        {view === 'config' && <ConfigView key={cfg} />}
        {view === 'testfiles' && <TestFilesView key={cfg} pendingOpen={pendingTestFile} onConsumePending={() => setPendingTestFile(undefined)} />}
        {view === 'execute' && <ExecuteView key={cfg} onOpenReports={() => navigate('reports')} />}
        {view === 'datasources' && <DataSourceStats key={cfg} />}
        {view === 'integrations' && <ExtApiConfig />}
        {view === 'import' && <ImportView key={cfg} onOpenTestFiles={() => navigate('testfiles')} />}
        {view === 'record' && <RecordView key={cfg} onOpenInEditor={openTestFile} />}
      </div>
    </div>
  );
}
