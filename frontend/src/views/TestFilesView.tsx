import { useEffect, useState } from 'react';
import { api } from '../api';
import type { TestFilesIndex, TestFile, TestFileContent } from '../api';
import { fmtBytes } from '../util';

export default function TestFilesView() {
  const [data, setData] = useState<TestFilesIndex | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [sel, setSel] = useState<string | null>(null);
  const [content, setContent] = useState<TestFileContent | null>(null);
  const [cErr, setCErr] = useState<string | null>(null);
  const [cLoading, setCLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    api
      .testfiles()
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const openFile = (f: TestFile) => {
    setSel(f.path);
    setContent(null);
    setCErr(null);
    setCLoading(true);
    api
      .testfile(f.path)
      .then(setContent)
      .catch((e) => setCErr(e.message))
      .finally(() => setCLoading(false));
  };

  return (
    <div className="view">
      <div className="view-head">
        <h2>Test files</h2>
        <div className="view-head-right">
          {data && (
            <span className="muted small mono ellipsis" title={data.dir}>
              {data.dir}
            </span>
          )}
          <span className="muted small">read-only</span>
        </div>
      </div>

      {loading && <div className="view-note">Loading…</div>}
      {error && (
        <div className="view-error">
          Could not reach the test-files API ({error}). Rebuild the JAR from this delta so the{' '}
          <code>/api</code> handler is present.
        </div>
      )}

      {data && !loading && (
        <div className="tf-split">
          <div className="tf-list">
            {data.files.length === 0 ? (
              <div className="view-note">No .sel or .xml test files in this directory.</div>
            ) : (
              <table className="grid-table">
                <thead>
                  <tr>
                    <th>File</th>
                    <th>Type</th>
                    <th className="num">Size</th>
                  </tr>
                </thead>
                <tbody>
                  {data.files.map((f) => (
                    <tr
                      key={f.path}
                      className={sel === f.path ? 'sel' : ''}
                      onClick={() => openFile(f)}
                      style={{ cursor: 'pointer' }}
                    >
                      <td className="mono ellipsis" style={{ maxWidth: 260 }}>
                        {f.name}
                      </td>
                      <td>
                        <span className={'kind kind-' + f.kind}>{f.kind}</span>
                      </td>
                      <td className="num mono">{fmtBytes(f.size)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div className="tf-content">
            {!sel && <div className="view-note muted">Select a file to view its contents.</div>}
            {cLoading && <div className="view-note">Loading…</div>}
            {cErr && <div className="view-error">Could not read file ({cErr}).</div>}
            {content && (
              <>
                <div className="tf-content-head">
                  <span className="mono small ellipsis" title={content.path}>
                    {content.path}
                  </span>
                  {content.truncated && <span className="warn-badge small">truncated</span>}
                </div>
                <pre className="body-pre tf-pre">{content.content}</pre>
                <div className="view-note muted small">
                  Editing arrives with the Monaco seleasy editor in Phase 4; save is still via the{' '}
                  <a href="/">classic UI</a>.
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
