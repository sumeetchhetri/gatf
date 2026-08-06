import { useEffect, useState } from 'react';
import { api } from '../api';
import type { ConfigInfo } from '../api';

// Global gatf-config selector. Lists /api/configs (root gatf-config*.{json,xml} + configs/*.{json,xml});
// selecting one scopes reports and runs to that config (the value is sent as ?config= on every API call).
// Hides itself quietly against an older JAR that has no /api/configs endpoint.
export default function ConfigPicker({
  value,
  onChange,
}: {
  value: string;
  onChange: (name: string) => void;
}) {
  const [configs, setConfigs] = useState<ConfigInfo[]>([]);
  const [err, setErr] = useState(false);

  useEffect(() => {
    api
      .configs()
      .then((d) => setConfigs(d.configs ?? []))
      .catch(() => setErr(true));
  }, []);

  if (err) return null;

  return (
    <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <span className="muted small">Config</span>
      <select
        className="btn ghost small"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        title="Active gatf-config for reports and runs"
      >
        <option value="">Default (gatf-config)</option>
        {configs.map((c) => (
          <option key={c.name} value={c.name}>
            {c.name}
          </option>
        ))}
      </select>
    </label>
  );
}
