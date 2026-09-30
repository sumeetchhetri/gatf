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
import { api, type TestCaseReport } from '../api';

// The created issue's id / link from the issue API's response (Jira {key,self}, GitHub {number,html_url}, …).
export function issueRef(r: TestCaseReport): { id: string; link: string } {
  let j: Record<string, unknown> = {};
  try {
    const v = JSON.parse(String(r.responseContent ?? ''));
    if (v && typeof v === 'object') j = v as Record<string, unknown>;
  } catch {
    /* not JSON */
  }
  const s = (v: unknown) => (v == null ? '' : String(v));
  return { id: s(j.key ?? j.number ?? j.id ?? j.iid), link: s(j.html_url ?? j.web_url ?? j.self ?? j.url) };
}

// "Raise defect" on a failed result: runs an issue-tracker API from Integrations → Issue Tracking with the failure.
export default function RaiseDefect({ report }: { report: TestCaseReport }) {
  const [apis, setApis] = useState<string[] | null>(null);
  const [sel, setSel] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; link?: string } | null>(null);

  const send = (name: string) => {
    setBusy(true);
    setMsg(null);
    api
      .raiseDefect(name, report)
      .then((r) => {
        const ok = String(r.status).toLowerCase() === 'success';
        const ref = issueRef(r);
        setMsg(
          ok
            ? { ok, text: ref.id ? 'Created ' + ref.id : 'Issue created', link: /^https?:/.test(ref.link) ? ref.link : undefined }
            : { ok, text: 'Issue API failed: ' + (r.error || r.responseStatusCode || r.status) },
        );
      })
      .catch((e) => setMsg({ ok: false, text: e.message }))
      .finally(() => setBusy(false));
  };

  const start = () => {
    if (apis && sel) return send(sel);
    setBusy(true);
    setMsg(null);
    api
      .issueApis()
      .then((names) => {
        setApis(names);
        if (names.length === 0) {
          setMsg({ ok: false, text: 'No issue-tracker API configured (Integrations → Issue Tracking)' });
          setBusy(false);
        } else if (names.length === 1) {
          setSel(names[0]);
          send(names[0]);
        } else {
          setSel(names[0]);
          setBusy(false);
        }
      })
      .catch((e) => {
        setMsg({ ok: false, text: /no testcases/i.test(e.message) ? 'No issue-tracker API configured (Integrations → Issue Tracking)' : e.message });
        setBusy(false);
      });
  };

  return (
    <span className="raise-defect">
      {apis && apis.length > 1 && (
        <select className="small" value={sel} onChange={(e) => setSel(e.target.value)} aria-label="Issue tracker API">
          {apis.map((n) => (
            <option key={n}>{n}</option>
          ))}
        </select>
      )}
      <button className="btn small" disabled={busy} onClick={start} title="Raise a defect in your issue tracker for this failure">
        {busy ? 'Raising…' : 'Raise defect'}
      </button>
      {msg && (
        <span className={'st-badge ' + (msg.ok ? 'st-pass' : 'st-fail')} style={{ marginLeft: 8 }}>
          {msg.link ? (
            <a href={msg.link} target="_blank" rel="noreferrer">
              {msg.text}
            </a>
          ) : (
            msg.text
          )}
        </span>
      )}
    </span>
  );
}
