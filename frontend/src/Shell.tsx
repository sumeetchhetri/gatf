import { useEffect, useState } from 'react';
import App from './App';
import Nav from './components/Nav';
import ReportsView from './views/ReportsView';
import ConfigView from './views/ConfigView';
import TestFilesView from './views/TestFilesView';

export type ViewKey = 'viewer' | 'reports' | 'config' | 'testfiles';

const HASH: Record<string, ViewKey> = {
  '': 'viewer',
  '#': 'viewer',
  '#/': 'viewer',
  '#/reports': 'reports',
  '#/config': 'config',
  '#/testfiles': 'testfiles',
};
const TO_HASH: Record<ViewKey, string> = {
  viewer: '#/',
  reports: '#/reports',
  config: '#/config',
  testfiles: '#/testfiles',
};

function currentView(): ViewKey {
  return HASH[window.location.hash] ?? 'viewer';
}

export default function Shell() {
  const [view, setView] = useState<ViewKey>(currentView());
  const [pendingTrace, setPendingTrace] = useState<string | undefined>(undefined);

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

  return (
    <div className="shell">
      <Nav active={view} onNavigate={navigate} />
      <div className="shell-body">
        {/* Viewer stays mounted so a loaded trace survives tab switches. */}
        <div className="view-viewer" style={{ display: view === 'viewer' ? 'flex' : 'none' }}>
          <App traceUrl={pendingTrace} />
        </div>
        {view === 'reports' && <ReportsView onOpenTrace={openTrace} />}
        {view === 'config' && <ConfigView />}
        {view === 'testfiles' && <TestFilesView />}
      </div>
    </div>
  );
}
