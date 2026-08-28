/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
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
