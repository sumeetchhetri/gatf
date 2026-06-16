import { useMemo, useState } from 'react';
import type { Trace, HarEntry } from '../trace/types';
import { fileUrl } from '../trace/loadTrace';
import { fmtBytes, fmtMs, fmtOffset } from '../util';

const dec = new TextDecoder('utf-8');

function statusClass(s: number): string {
  if (s >= 500) return 'st-5';
  if (s >= 400) return 'st-4';
  if (s >= 300) return 'st-3';
  if (s >= 200) return 'st-2';
  return 'st-0';
}

function typeOf(mime: string): string {
  if (!mime) return 'other';
  const m = mime.split(';')[0];
  if (m.includes('html')) return 'doc';
  if (m.includes('javascript') || m.includes('ecmascript')) return 'js';
  if (m.includes('css')) return 'css';
  if (m.includes('json')) return 'json';
  if (m.startsWith('image/')) return 'img';
  if (m.startsWith('font/') || m.includes('font')) return 'font';
  if (m.startsWith('text/')) return 'text';
  return 'other';
}

function shortUrl(u: string): string {
  try {
    const url = new URL(u);
    const p = url.pathname === '/' ? '/' : url.pathname;
    return url.host + p + (url.search ? url.search : '');
  } catch {
    return u;
  }
}

export function NetworkTab({ trace, currentTime }: { trace: Trace; currentTime: number }) {
  const entries = trace.har?.log.entries ?? [];
  const [sel, setSel] = useState<number | null>(null);
  const [q, setQ] = useState('');
  const [ftype, setFtype] = useState('all');

  const filtered = useMemo(() => {
    const ql = q.toLowerCase();
    return entries
      .map((e, i) => ({ e, i }))
      .filter(({ e }) => {
        if (ftype !== 'all' && typeOf(e.response.content.mimeType) !== ftype) return false;
        if (ql && !e.request.url.toLowerCase().includes(ql)) return false;
        return true;
      });
  }, [entries, q, ftype]);

  if (entries.length === 0) return <div className="empty-tab">No network data in this trace.</div>;

  const selected = sel != null ? entries[sel] : null;
  const types = ['all', 'doc', 'js', 'css', 'json', 'img', 'font', 'text', 'other'];

  return (
    <div className="net">
      <div className="net-toolbar">
        <input
          className="inp"
          placeholder="Filter URL…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select className="inp" value={ftype} onChange={(e) => setFtype(e.target.value)}>
          {types.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <span className="muted small">
          {filtered.length}/{entries.length} requests
        </span>
      </div>
      <div className="net-split">
        <div className="net-list">
          <table className="grid-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Method</th>
                <th>Status</th>
                <th>Type</th>
                <th>URL</th>
                <th>Size</th>
                <th>Time</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(({ e, i }) => {
                const future = e.startMs > currentTime;
                return (
                  <tr
                    key={i}
                    className={
                      (sel === i ? 'sel ' : '') + (future ? 'future ' : '') + (e._failed ? 'failed' : '')
                    }
                    onClick={() => setSel(i)}
                  >
                    <td className="mono">{i + 1}</td>
                    <td className="mono">{e.request.method}</td>
                    <td className={'mono ' + statusClass(e.response.status)}>
                      {e._failed ? 'ERR' : e.response.status || '–'}
                    </td>
                    <td>{typeOf(e.response.content.mimeType)}</td>
                    <td className="url" title={e.request.url}>
                      {shortUrl(e.request.url)}
                    </td>
                    <td className="mono num">{fmtBytes(e.response.content.size)}</td>
                    <td className="mono num">{fmtMs(e.time)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {selected && <RequestDetail trace={trace} entry={selected} onClose={() => setSel(null)} />}
      </div>
    </div>
  );
}

function RequestDetail({
  trace,
  entry,
  onClose,
}: {
  trace: Trace;
  entry: HarEntry;
  onClose: () => void;
}) {
  const bodyPath = entry.response.content._file;
  const bytes = bodyPath ? trace.files[bodyPath] : undefined;
  const mime = entry.response.content.mimeType || '';
  const isImg = mime.startsWith('image/');
  const imgUrl = isImg && bodyPath ? fileUrl(trace.files, bodyPath) : null;

  let bodyText: string | null = null;
  if (bytes && !isImg) {
    const t = dec.decode(bytes.length > 200000 ? bytes.slice(0, 200000) : bytes);
    if (mime.includes('json')) {
      try {
        bodyText = JSON.stringify(JSON.parse(t), null, 2);
      } catch {
        bodyText = t;
      }
    } else {
      bodyText = t;
    }
  }

  return (
    <div className="net-detail">
      <div className="detail-head">
        <span className="mono ellipsis" title={entry.request.url}>
          {entry.request.method} {entry.request.url}
        </span>
        <button className="btn-x" onClick={onClose}>
          ✕
        </button>
      </div>
      <div className="detail-body">
        <div className="kv">
          <b>Started</b>
          <span>+{fmtOffset(entry.startMs - trace.t0)}</span>
          <b>Duration</b>
          <span>{fmtMs(entry.time)}</span>
          <b>Status</b>
          <span>
            {entry._failed ? `FAILED — ${entry._failed}` : `${entry.response.status} ${entry.response.statusText}`}
          </span>
          <b>MIME</b>
          <span>{mime || '–'}</span>
          <b>Size</b>
          <span>{fmtBytes(entry.response.content.size)}</span>
        </div>

        <Headers title="Request headers" headers={entry.request.headers} />
        <Headers title="Response headers" headers={entry.response.headers} />

        <div className="hdr-title">Response body</div>
        {imgUrl ? (
          <img className="body-img" src={imgUrl} alt="response" />
        ) : bodyText != null ? (
          <pre className="body-pre">{bodyText}</pre>
        ) : (
          <div className="muted small">
            {bodyPath ? 'body captured but not decodable as text' : 'body not captured (capped, streamed, or a non-body request)'}
          </div>
        )}
      </div>
    </div>
  );
}

function Headers({ title, headers }: { title: string; headers: { name: string; value: string }[] }) {
  if (!headers || headers.length === 0) return null;
  return (
    <>
      <div className="hdr-title">{title}</div>
      <table className="hdr-table">
        <tbody>
          {headers.map((h, i) => (
            <tr key={i}>
              <td className="hdr-name mono">{h.name}</td>
              <td className="hdr-val mono">{h.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
