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
import { useMemo, useState } from 'react';
import type { Trace } from '../trace/types';
import { fmtOffset } from '../util';

function levelClass(l: string): string {
  const s = (l || '').toLowerCase();
  if (s.includes('err') || s === 'severe') return 'lv-err';
  if (s.includes('warn')) return 'lv-warn';
  if (s.includes('debug') || s.includes('verbose')) return 'lv-dbg';
  return 'lv-info';
}

export function ConsoleTab({ trace, currentTime }: { trace: Trace; currentTime: number }) {
  const rows = trace.console;
  const [q, setQ] = useState('');
  const [lvl, setLvl] = useState('all');

  const filtered = useMemo(() => {
    const ql = q.toLowerCase();
    return rows.filter((r) => {
      if (lvl !== 'all' && levelClass(r.level) !== lvl) return false;
      if (ql && !(r.text ?? '').toLowerCase().includes(ql)) return false;
      return true;
    });
  }, [rows, q, lvl]);

  if (rows.length === 0) return <div className="empty-tab">No console entries in this trace.</div>;

  const levels = [
    ['all', 'All'],
    ['lv-err', 'Errors'],
    ['lv-warn', 'Warnings'],
    ['lv-info', 'Info'],
    ['lv-dbg', 'Debug'],
  ];

  return (
    <div className="console">
      <div className="net-toolbar">
        <input className="inp" placeholder="Filter text…" value={q} onChange={(e) => setQ(e.target.value)} />
        <select className="inp" value={lvl} onChange={(e) => setLvl(e.target.value)}>
          {levels.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </select>
        <span className="muted small">
          {filtered.length}/{rows.length} entries
        </span>
      </div>
      <div className="console-list">
        {filtered.map((r, i) => (
          <div key={i} className={'console-row ' + levelClass(r.level) + (r.ts > currentTime ? ' future' : '')}>
            <span className="c-time mono">+{fmtOffset(r.ts - trace.t0)}</span>
            <span className="c-lvl">{r.level}</span>
            <span className="c-text">{r.text}</span>
            {r.url && (
              <span className="c-src mono" title={r.url}>
                {shortSrc(r.url)}
                {r.line != null ? ':' + r.line : ''}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function shortSrc(u: string): string {
  try {
    const url = new URL(u);
    return (url.pathname.split('/').pop() || url.host) as string;
  } catch {
    return u.split('/').pop() || u;
  }
}
