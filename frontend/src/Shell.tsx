import { useEffect, useState } from 'react';
import App from './App';
import Nav from './components/Nav';
import ReportsView from './views/ReportsView';
import ReportViewer from './views/ReportViewer';
import ConfigView from './views/ConfigView';
import TestFilesView from './views/TestFilesView';
import ExecuteView from './views/ExecuteView';

export type ViewKey = 'viewer' | 'reports' | 'config' | 'testfiles' | 'execute' | 'report';

const HASH: Record<string, ViewKey> = {
  '': 'viewer',
  '#': 'viewer',
  '#/': 'viewer',
  '#/reports': 'reports',
  '#/config': 'config',
  '#/testfiles': 'testfiles',
  '#/execute': 'execute',
};
const TO_HASH: Record<ViewKey, string> = {
  viewer: '#/',
  reports: '#/reports',
  config: '#/config',
  testfiles: '#/testfiles',
  execute: '#/execute',
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

  return (
    <div className="shell">
      <Nav active={view === 'report' ? 'reports' : view} onNavigate={navigate} />
      <div className="shell-body">
        {/* Viewer stays mounted so a loaded trace survives tab switches. */}
        <div className="view-viewer" style={{ display: view === 'viewer' ? 'flex' : 'none' }}>
          <App traceUrl={pendingTrace} />
        </div>
        {view === 'reports' && <ReportsView onOpenTrace={openTrace} onOpenReport={openReport} />}
        {view === 'report' && pendingReport && (
          <ReportViewer
            dataUrl={pendingReport}
            onBack={() => navigate('reports')}
            onOpenTrace={openTrace}
            onOpenReport={openReport}
          />
        )}
        {view === 'config' && <ConfigView />}
        {view === 'testfiles' && <TestFilesView />}
        {view === 'execute' && <ExecuteView onOpenReports={() => navigate('reports')} />}
      </div>
    </div>
  );
}
