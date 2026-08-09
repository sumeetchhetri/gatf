import { useEffect, useRef, useState } from 'react';
import type { ViewKey } from '../Shell';
import { getTheme, toggleTheme, type Theme } from '../theme';
import ConfigPicker from './ConfigPicker';

// Primary workflow order (delta-064): Config → Test Files → Execute → Reports.
// Trace viewer is reached from Reports (not a tab); Compare is invoked from the test-file tree.
const TABS: { key: ViewKey; label: string }[] = [
  { key: 'config', label: 'Config' },
  { key: 'testfiles', label: 'Test Files' },
  { key: 'execute', label: 'Execute' },
  { key: 'reports', label: 'Reports' },
];
// Secondary tools live under a "More" menu so the primary bar stays the four-step workflow.
const MORE: { key: ViewKey; label: string }[] = [
  { key: 'import', label: 'Import' },
  { key: 'record', label: 'Record' },
  { key: 'datasources', label: 'Data Sources' },
  { key: 'integrations', label: 'Integrations' },
];

export default function Nav({
  active,
  onNavigate,
  configValue,
  onConfigChange,
}: {
  active: ViewKey;
  onNavigate: (v: ViewKey) => void;
  configValue: string;
  onConfigChange: (name: string) => void;
}) {
  const [theme, setThemeState] = useState<Theme>(getTheme());
  const [moreOpen, setMoreOpen] = useState(false);
  const moreRef = useRef<HTMLDivElement>(null);
  const moreActive = MORE.some((m) => m.key === active);

  useEffect(() => {
    if (!moreOpen) return;
    const onDoc = (e: MouseEvent) => {
      if (moreRef.current && !moreRef.current.contains(e.target as Node)) setMoreOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [moreOpen]);

  return (
    <nav className="nav">
      <div className="nav-brand">
        GATF <span className="muted small">console</span>
      </div>
      <div className="nav-tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            className={'nav-tab' + (active === t.key ? ' on' : '')}
            onClick={() => onNavigate(t.key)}
          >
            {t.label}
          </button>
        ))}
        <div className="nav-more" ref={moreRef}>
          <button
            className={'nav-tab' + (moreActive ? ' on' : '')}
            aria-haspopup="menu"
            aria-expanded={moreOpen}
            onClick={() => setMoreOpen((o) => !o)}
          >
            More {'\u25be'}
          </button>
          {moreOpen && (
            <div className="nav-more-menu" role="menu">
              {MORE.map((m) => (
                <button
                  key={m.key}
                  className={'nav-more-item' + (active === m.key ? ' on' : '')}
                  role="menuitem"
                  onClick={() => {
                    setMoreOpen(false);
                    onNavigate(m.key);
                  }}
                >
                  {m.label}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
      <div style={{ marginLeft: 'auto', marginRight: 8 }}>
        <ConfigPicker value={configValue} onChange={onConfigChange} />
      </div>
      <button
        className="nav-theme btn ghost small"
        title={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
        aria-label="Toggle theme"
        onClick={() => setThemeState(toggleTheme())}
      >
        {theme === 'dark' ? '☀︎' : '☾'}
      </button>
    </nav>
  );
}
