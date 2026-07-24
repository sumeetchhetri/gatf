import type { ViewKey } from '../Shell';

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

export default function Nav({ active, onNavigate }: { active: ViewKey; onNavigate: (v: ViewKey) => void }) {
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
    </nav>
  );
}
