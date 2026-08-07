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
import CompareFiles from './views/CompareFiles';
import ExtApiConfig from './views/ExtApiConfig';

export type ViewKey =
  | 'viewer'
  | 'reports'
  | 'config'
  | 'testfiles'
  | 'execute'
  | 'datasources'
  | 'compare'
  | 'integrations'
  | 'report';

const HASH: Record<string, ViewKey> = {
  '': 'viewer',
  '#': 'viewer',
  '#/': 'viewer',
  '#/reports': 'reports',
  '#/config': 'config',
  '#/testfiles': 'testfiles',
  '#/execute': 'execute',
  '#/datasources': 'datasources',
  '#/compare': 'compare',
  '#/integrations': 'integrations',
};
const TO_HASH: Record<ViewKey, string> = {
  viewer: '#/',
  reports: '#/reports',
  config: '#/config',
  testfiles: '#/testfiles',
  execute: '#/execute',
  datasources: '#/datasources',
  compare: '#/compare',
  integrations: '#/integrations',
  report: '#/reports',
};

function currentView(): ViewKey {
  return HASH[window.location.hash] ?? 'viewer';
}

function initialReport(): string | undefined {
  return new URLSearchParams(window.location.search).get('report') || undefined;
}

export default function Shell() {
  const [view, setView] = useState<ViewKey>(initialReport() ? 'report' : currentView());
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

  const navigate = (v: ViewKey) => {
    if (window.location.hash !== TO_HASH[v]) window.location.hash = TO_HASH[v];
    setView(v);
  };

  const openTrace = (url: string) => {
    setPendingTrace(url);
    navigate('viewer');
  };

  const openReport = (dataUrl: string) => {
    setPendingReport(dataUrl);
    setView('report'); // reached from Reports; not a top-level hash route
  };

  if (auth === 'checking') return <div className="view-note" style={{ padding: 24 }}>Loading…</div>;
  if (auth === 'required') return <Login onLogin={checkAuth} />;

  return (
    <div className="shell">
      <Nav
        active={view === 'report' ? 'reports' : view}
        onNavigate={navigate}
        configValue={cfg}
        onConfigChange={changeCfg}
      />
      <div className="shell-body">
        {/* Viewer stays mounted so a loaded trace survives tab switches. */}
        <div className="view-viewer" style={{ display: view === 'viewer' ? 'flex' : 'none' }}>
          <App traceUrl={pendingTrace} />
        </div>
        {view === 'reports' && <ReportsView key={cfg} onOpenTrace={openTrace} onOpenReport={openReport} />}
        {view === 'report' && pendingReport && (
          <ReportViewer
            dataUrl={pendingReport}
            onBack={() => navigate('reports')}
            onOpenTrace={openTrace}
            onOpenReport={openReport}
          />
        )}
        {view === 'config' && <ConfigView key={cfg} />}
        {view === 'testfiles' && <TestFilesView key={cfg} />}
        {view === 'execute' && <ExecuteView key={cfg} onOpenReports={() => navigate('reports')} />}
        {view === 'datasources' && <DataSourceStats key={cfg} />}
        {view === 'compare' && <CompareFiles key={cfg} />}
        {view === 'integrations' && <ExtApiConfig />}
      </div>
    </div>
  );
}
