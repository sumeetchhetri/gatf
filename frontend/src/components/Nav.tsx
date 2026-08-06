import { useState } from 'react';
import type { ViewKey } from '../Shell';
import { getTheme, toggleTheme, type Theme } from '../theme';
import ConfigPicker from './ConfigPicker';

const TABS: { key: ViewKey; label: string }[] = [
  { key: 'viewer', label: 'Trace Viewer' },
  { key: 'reports', label: 'Reports' },
  { key: 'config', label: 'Config' },
  { key: 'testfiles', label: 'Test Files' },
  { key: 'execute', label: 'Execute' },
  { key: 'datasources', label: 'Data Sources' },
  { key: 'compare', label: 'Compare' },
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
