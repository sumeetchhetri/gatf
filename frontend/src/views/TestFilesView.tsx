import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import type { TestcaseFileEntry, SelParseError, ApiTestCase } from '../api';
import CodeEditor, { type EditorMarker } from '../components/CodeEditor';
import TestCaseEditor from '../components/TestCaseEditor';
import SelDebugger from '../components/SelDebugger';
import SelRunner from '../components/SelRunner';
// CodeMirror 6 is heavy (~120KB gzip); load it only when a .sel file is opened.
const SelCodeMirror = lazy(() => import('../components/SelCodeMirror'));

type Kind = 'sel' | 'xml' | 'other';
const kindOf = (p: string): Kind =>
  p.toLowerCase().endsWith('.sel') ? 'sel' : p.toLowerCase().endsWith('.xml') ? 'xml' : 'other';
const baseName = (p: string) => {
  const i = p.lastIndexOf('/');
  return i === -1 ? p : p.slice(i + 1);
};

interface OpenFile {
  path: string;
  kind: Kind;
  loading: boolean;
  err: string | null;
  text: string; // original loaded text (.sel/other)
  cases: ApiTestCase[] | null; // .xml structured
}

export default function TestFilesView() {
  const [files, setFiles] = useState<TestcaseFileEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null); // CRUD in flight (message)

  const [open, setOpen] = useState<OpenFile | null>(null);
  const [text, setText] = useState(''); // editor working copy
  const [saving, setSaving] = useState(false);
  const [saveMsg, setSaveMsg] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [parseErrors, setParseErrors] = useState<SelParseError[]>([]);
  const [gotoLine, setGotoLine] = useState<number | null>(null);
  const [gotoSeq, setGotoSeq] = useState(0);
  const [debugging, setDebugging] = useState(false);
  const [running, setRunning] = useState(false);
  const jumpTo = (n: number) => {
    setGotoLine(n);
    setGotoSeq((s) => s + 1);
  };

  // dialogs
  const [newOpen, setNewOpen] = useState(false);
  const [newName, setNewName] = useState('');
  const [newType, setNewType] = useState<'sel' | 'xml'>('sel');
  const [renaming, setRenaming] = useState(false);
  const [renameTo, setRenameTo] = useState('');
  const [confirmDel, setConfirmDel] = useState<string | null>(null);

  const loadFiles = () => {
    setLoading(true);
    setError(null);
    api
      .testcaseFiles()
      .then(setFiles)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };
  useEffect(loadFiles, []);

  const groups = useMemo(() => {
    const m = new Map<string, TestcaseFileEntry[]>();
    for (const f of [...files].sort((a, b) => a.path.localeCompare(b.path))) {
      const i = f.path.lastIndexOf('/');
      const dir = i === -1 ? '' : f.path.slice(0, i);
      if (!m.has(dir)) m.set(dir, []);
      m.get(dir)!.push(f);
    }
    return [...m.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [files]);

  const dirty = open != null && open.kind !== 'xml' && text !== open.text;

  const openFile = (path: string, jumpLine?: number) => {
    if (dirty && !window.confirm('Discard unsaved changes?')) return;
    const kind = kindOf(path);
    setOpen({ path, kind, loading: true, err: null, text: '', cases: null });
    setText('');
    setParseErrors([]);
    setSaveMsg(null);
    setSaved(false);
    setRenaming(false);
    setConfirmDel(null);
    setDebugging(false);
    setRunning(false);
    if (kind === 'xml') {
      api
        .readXmlCases(path)
        .then((cases) => setOpen({ path, kind, loading: false, err: null, text: '', cases }))
        .catch((e) =>
          // an empty/new .xml (seeded <TestCases></TestCases>) has no cases yet — open the editor empty
          /no testcases/i.test(e.message)
            ? setOpen({ path, kind, loading: false, err: null, text: '', cases: [] })
            : setOpen({ path, kind, loading: false, err: e.message, text: '', cases: null }),
        );
    } else {
      api
        .readSel(path)
        .then((t) => {
          setOpen({ path, kind, loading: false, err: null, text: t, cases: null });
          setText(t);
          if (jumpLine) jumpTo(jumpLine);
        })
        .catch((e) => setOpen({ path, kind, loading: false, err: e.message, text: '', cases: null }));
    }
  };

  // ctrl/⌘-click or double-click on a reference line → jump within this file or open the target file
  const onNavigate = (p: string, l?: number) => {
    if (open && p === open.path) {
      if (l) jumpTo(l);
    } else {
      openFile(p, l);
    }
  };

  const save = () => {
    if (!open || open.kind === 'xml') return;
    setSaving(true);
    setSaveMsg(null);
    setSaved(false);
    setParseErrors([]);
    api
      .saveSel(open.path, text)
      .then((r) => {
        if (r.ok) {
          setOpen({ ...open, text });
          setSaved(true);
          setTimeout(() => setSaved(false), 4000);
        } else {
          setParseErrors(r.errors);
          if (r.message) setSaveMsg(r.message);
          else if (r.errors.length === 0) setSaveMsg('Save failed.');
        }
      })
      .catch((e) => setSaveMsg(e.message))
      .finally(() => setSaving(false));
  };

  const doCreate = () => {
    let name = newName.trim();
    if (!name) return;
    if (!/\.(sel|xml)$/i.test(name)) name += '.' + newType;
    setBusy('Creating…');
    api
      .createTestcaseFile(name)
      .then(() => {
        setNewOpen(false);
        setNewName('');
        loadFiles();
        openFile(name);
      })
      .catch((e) => setError(e.message))
      .finally(() => setBusy(null));
  };

  const doRename = () => {
    if (!open) return;
    const to = renameTo.trim();
    if (!to || to === open.path) {
      setRenaming(false);
      return;
    }
    setBusy('Renaming…');
    api
      .renameTestcaseFile(open.path, to)
      .then(() => {
        setRenaming(false);
        loadFiles();
        openFile(to);
      })
      .catch((e) => setError(e.message))
      .finally(() => setBusy(null));
  };

  const doDelete = (path: string) => {
    setBusy('Deleting…');
    api
      .deleteTestcaseFile(path)
      .then(() => {
        setConfirmDel(null);
        if (open?.path === path) setOpen(null);
        loadFiles();
      })
      .catch((e) => setError(e.message))
      .finally(() => setBusy(null));
  };

  const markers: EditorMarker[] = parseErrors
    .map((e) => ({ line: Number(e[1]), message: String(e[3] ?? e[2] ?? 'Parse error') }))
    .filter((m) => Number.isFinite(m.line) && m.line > 0);

  return (
    <div className="view">
      <div className="view-head">
        <h2>Test files</h2>
        <div className="view-head-right">
          <button className="btn ghost small" onClick={() => setNewOpen((o) => !o)}>
            + New file
          </button>
          <button className="btn ghost small" onClick={loadFiles}>
            Refresh
          </button>
        </div>
      </div>

      {busy && <div className="view-note">{busy}</div>}
      {loading && <div className="view-note">Loading…</div>}
      {error && <div className="view-error">{error}</div>}

      {newOpen && (
        <div className="tf-new">
          <input
            className="sf-input"
            placeholder="path/name (e.g. login.sel or api/users.xml)"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && doCreate()}
            autoFocus
          />
          <select className="sf-input" value={newType} onChange={(e) => setNewType(e.target.value as 'sel' | 'xml')}>
            <option value="sel">.sel (selenium)</option>
            <option value="xml">.xml (API)</option>
          </select>
          <button className="btn primary small" onClick={doCreate}>
            Create
          </button>
          <button className="btn ghost small" onClick={() => setNewOpen(false)}>
            Cancel
          </button>
        </div>
      )}

      {!loading && !error && (
        <div className="tf-split">
          <div className="tf-list">
            {files.length === 0 ? (
              <div className="view-note">No test files. Create one with “+ New file”.</div>
            ) : (
              groups.map(([dir, entries]) => (
                <div className="tf-group" key={dir || '(root)'}>
                  <div className="tf-group-h mono small">{dir || '(root)'}</div>
                  {entries.map((f) => (
                    <div
                      key={f.path}
                      className={'tf-item' + (open?.path === f.path ? ' sel' : '')}
                      onClick={() => openFile(f.path)}
                    >
                      <span className={'kind kind-' + kindOf(f.path)}>{kindOf(f.path)}</span>
                      <span className="tf-item-name mono ellipsis" title={f.path}>
                        {baseName(f.path)}
                      </span>
                      <button
                        className="tf-del"
                        title="Delete"
                        onClick={(e) => {
                          e.stopPropagation();
                          setConfirmDel(f.path);
                        }}
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                </div>
              ))
            )}
          </div>

          <div className="tf-content">
            {!open && <div className="view-note muted">Select a file to edit, or create a new one.</div>}

            {open && (
              <>
                <div className="tf-content-head">
                  {renaming ? (
                    <div className="tf-rename">
                      <input
                        className="sf-input"
                        value={renameTo}
                        onChange={(e) => setRenameTo(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && doRename()}
                        autoFocus
                      />
                      <button className="btn primary small" onClick={doRename}>
                        Rename
                      </button>
                      <button className="btn ghost small" onClick={() => setRenaming(false)}>
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <>
                      <span className="mono small ellipsis" title={open.path}>
                        {open.path}
                      </span>
                      <span className={'kind kind-' + open.kind}>{open.kind}</span>
                      {!debugging && !running && (
                        <div className="tf-head-actions">
                          {open.kind !== 'xml' && (
                            <>
                              <button className="btn primary small" disabled={!dirty || saving} onClick={save}>
                                {saving ? 'Saving…' : 'Save'}
                              </button>
                              <button
                                className="btn ghost small"
                                disabled={!dirty || saving}
                                onClick={() => setText(open.text)}
                              >
                                Reset
                              </button>
                            </>
                          )}
                          {open.kind === 'sel' && (
                            <>
                              <button
                                className="btn small"
                                disabled={dirty}
                                title={dirty ? 'Save before running' : 'Run this script'}
                                onClick={() => setRunning(true)}
                              >
                                ▶ Run
                              </button>
                              <button
                                className="btn small"
                                disabled={dirty}
                                title={dirty ? 'Save before debugging' : 'Debug this script'}
                                onClick={() => setDebugging(true)}
                              >
                                🐞 Debug
                              </button>
                            </>
                          )}
                          <button
                            className="btn ghost small"
                            onClick={() => {
                              setRenameTo(open.path);
                              setRenaming(true);
                            }}
                          >
                            Rename
                          </button>
                          {dirty && <span className="muted small">unsaved</span>}
                          {saved && <span className="cfg-ok small">saved ✓</span>}
                        </div>
                      )}
                    </>
                  )}
                </div>

                {open.loading && <div className="view-note">Loading…</div>}
                {open.err && <div className="view-error">Could not read file ({open.err}).</div>}

                {!open.loading && !open.err && open.kind === 'xml' && (
                  <TestCaseEditor filePath={open.path} cases={open.cases ?? []} onReload={() => openFile(open.path)} />
                )}

                {!open.loading && !open.err && open.kind !== 'xml' &&
                  (debugging && open.kind === 'sel' ? (
                    <SelDebugger filePath={open.path} onExit={() => setDebugging(false)} />
                  ) : running && open.kind === 'sel' ? (
                    <SelRunner filePath={open.path} code={text} onExit={() => setRunning(false)} />
                  ) : (
                    <>
                      {open.kind === 'sel' ? (
                      <Suspense fallback={<div className="view-note">Loading editor…</div>}>
                        <SelCodeMirror
                          value={text}
                          onChange={setText}
                          filePath={open.path}
                          diagnostics={markers}
                          onSave={save}
                          gotoLine={gotoLine}
                          gotoSeq={gotoSeq}
                          onNavigate={onNavigate}
                        />
                      </Suspense>
                    ) : (
                      <CodeEditor value={text} onChange={setText} markers={markers} gotoLine={gotoLine} />
                    )}
                    {saveMsg && <div className="view-error">{saveMsg}</div>}
                    {parseErrors.length > 0 && (
                      <div className="tf-problems">
                        <div className="tf-problems-h small">
                          {parseErrors.length} parse error(s) — file not saved
                        </div>
                        {parseErrors.map((e, i) => {
                          const ln = Number(e[1]);
                          const msg = String(e[3] ?? e[2] ?? 'Parse error');
                          return (
                            <div
                              key={i}
                              className="tf-problem"
                              onClick={() => Number.isFinite(ln) && ln > 0 && jumpTo(ln)}
                            >
                              <span className="tf-problem-ln">{Number.isFinite(ln) && ln > 0 ? `L${ln}` : '—'}</span>
                              <span className="tf-problem-msg">{msg}</span>
                            </div>
                          );
                        })}
                      </div>
                    )}
                    <div className="view-note muted small">
                      {open.kind === 'sel'
                        ? 'Ctrl/Cmd-S saves; Ctrl-Space autocompletes. Saving parse-validates server-side; a backup .bk is kept.'
                        : 'Saving parse-validates the script server-side; a backup .bk is kept.'}
                    </div>
                    </>
                  ))}
              </>
            )}
          </div>
        </div>
      )}

      {confirmDel && (
        <div className="tf-confirm">
          <span className="small">
            Delete <span className="mono">{confirmDel}</span>?
          </span>
          <button className="btn small" style={{ color: '#e2557b' }} onClick={() => doDelete(confirmDel)}>
            Delete
          </button>
          <button className="btn ghost small" onClick={() => setConfirmDel(null)}>
            Cancel
          </button>
        </div>
      )}
    </div>
  );
}
