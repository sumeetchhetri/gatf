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
import { useState } from 'react';
import { api } from '../api';

// Shown when GET /misc returns 401 (auth enabled, no valid token). Posts to /login and, on success,
// stores the token and calls onLogin so the shell re-probes and renders the app.
export default function Login({ onLogin }: { onLogin: () => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = () => {
    if (!username || busy) return;
    setBusy(true);
    setErr(null);
    api
      .login(username, password)
      .then((r) => {
        if (r.ok) onLogin();
        else setErr(r.error || 'Login failed');
      })
      .finally(() => setBusy(false));
  };

  return (
    <div className="login-wrap">
      <div className="login-card">
        <h2 className="login-title">GATF</h2>
        <p className="muted small">Sign in to continue</p>
        <label className="login-field">
          <span className="small muted">Username</span>
          <input
            autoFocus
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && submit()}
          />
        </label>
        <label className="login-field">
          <span className="small muted">Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && submit()}
          />
        </label>
        {err && <div className="view-error small">{err}</div>}
        <button className="btn primary" disabled={busy || !username} onClick={submit}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </div>
    </div>
  );
}
