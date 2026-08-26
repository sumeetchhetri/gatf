import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import type { TestcaseFileEntry, SelParseError, ApiTestCase } from '../api';
import CodeEditor, { type EditorMarker } from '../components/CodeEditor';
import TestCaseEditor from '../components/TestCaseEditor';
import SelDebugger from '../components/SelDebugger';
import SelRunner from '../components/SelRunner';
import CompareFiles from './CompareFiles';
// CodeMirror 6 is heavy (~120KB gzip); load it only when a .sel file is opened.
const SelCodeMirror = lazy(() => import('../components/SelCodeMirror'));

type Kind = 'sel' | 'xml' | 'other';
const kindOf = (p: string): Kind =>
  p.toLowerCase().endsWith('.sel') ? 'sel' : p.toLowerCase().endsWith('.xml') ? 'xml' : 'other';
const baseName = (p: string) => {
  const i = p.lastIndexOf('/');
  return i === -1 ? p : p.slice(i + 1);
};

// One open editor tab. Each tab keeps its own working copy (draft) so switching tabs never
// discards edits (delta-045: opening a file adds a tab instead of replacing the open one).
interface Tab {
  path: string;
  kind: Kind;
  loading: boolean;
  err: string | null;
  text: string; // last-saved text (.sel/other)
  draft: string; // editor working copy
  cases: ApiTestCase[] | null; // .xml structured
}

// tree node: a directory (has children) or a file (has kind)
interface TreeNode {
  name: string;
  path: string;
  kind?: Kind;
  children?: TreeNode[];
}

export default function TestFilesView({ pendingOpen, onConsumePending }: { pendingOpen?: string; onConsumePending?: () => void } = {}) {
  const [files, setFiles] = useState<TestcaseFileEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null); // CRUD in flight (message)

  // open tabs + which is active + browser-style back/forward history of file paths
  const [tabs, setTabs] = useState<Tab[]>([]);
  const [activePath, setActivePath] = useState<string | null>(null);
  const [back, setBack] = useState<string[]>([]);
  const [fwd, setFwd] = useState<string[]>([]);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set()); // collapsed dir paths

  // transient state for the ACTIVE tab (reset when the active tab changes)
  const [saving, setSaving] = useState(false);
  const [saveMsg, setSaveMsg] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [parseErrors, setParseErrors] = useState<SelParseError[]>([]);
  const [gotoLine, setGotoLine] = useState<number | null>(null);
  const [gotoSeq, setGotoSeq] = useState(0);
  const [debugging, setDebugging] = useState(false);
  const [running, setRunning] = useState(false);
  const [navMsg, setNavMsg] = useState<string | null>(null);

  // dialogs
  const [newOpen, setNewOpen] = useState(false);
  const [newName, setNewName] = useState('');
  const [newType, setNewType] = useState<'sel' | 'xml'>('sel');
  const [renaming, setRenaming] = useState(false);
  const [renameTo, setRenameTo] = useState('');
  const [confirmDel, setConfirmDel] = useState<string | null>(null);
  // compare-from-tree (delta-064): tick two files → a Compare button opens an inline diff over the pair.
  const [compareSel, setCompareSel] = useState<Set<string>>(new Set());
  const [comparePair, setComparePair] = useState<[string, string] | null>(null);
  const toggleCompare = (path: string) =>
    setCompareSel((prev) => {
      const next = new Set(prev);
      if (next.has(path)) next.delete(path);
      else {
        if (next.size >= 2) next.delete([...next][0]); // keep at most the two most-recent picks
        next.add(path);
      }
      return next;
    });

  const active = tabs.find((t) => t.path === activePath) ?? null;
  const dirty = active != null && active.kind !== 'xml' && active.draft !== active.text;

  const jumpTo = (n: number) => {
    setGotoLine(n);
    setGotoSeq((s) => s + 1);
  };
  const flashNav = (m: string) => {
    setNavMsg(m);
    window.setTimeout(() => setNavMsg((c) => (c === m ? null : c)), 4000);
  };
  const resetTransient = () => {
    setParseErrors([]);
    setSaveMsg(null);
    setSaved(false);
    setDebugging(false);
    setRunning(false);
    setNavMsg(null);
    setRenaming(false);
  };
  const patchTab = (path: string, patch: Partial<Tab>) =>
    setTabs((ts) => ts.map((t) => (t.path === path ? { ...t, ...patch } : t)));

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

  // ---- directory tree ----
  const tree = useMemo(() => {
    const root: TreeNode = { name: '', path: '', children: [] };
    for (const f of [...files].sort((a, b) => a.path.localeCompare(b.path))) {
      const parts = f.path.split('/');
      let cur = root;
      let acc = '';
      for (let i = 0; i < parts.length; i++) {
        const part = parts[i];
        acc = acc ? acc + '/' + part : part;
        if (i === parts.length - 1) {
          cur.children!.push({ name: part, path: f.path, kind: kindOf(f.path) });
        } else {
          let dir = cur.children!.find((c) => c.children && c.name === part);
          if (!dir) {
            dir = { name: part, path: acc, children: [] };
            cur.children!.push(dir);
          }
          cur = dir;
        }
      }
    }
    const sortRec = (n: TreeNode) => {
      if (!n.children) return;
      n.children.sort((a, b) => {
        const ad = !!a.children,
          bd = !!b.children;
        if (ad !== bd) return ad ? -1 : 1; // directories first
        return a.name.localeCompare(b.name);
      });
      n.children.forEach(sortRec);
    };
    sortRec(root);
    return root;
  }, [files]);

  const toggleDir = (p: string) =>
    setCollapsed((s) => {
      const n = new Set(s);
      if (n.has(p)) n.delete(p);
      else n.add(p);
      return n;
    });

  // ---- tab / history navigation ----
  const openPaths = useMemo(() => new Set(tabs.map((t) => t.path)), [tabs]);

  const activate = (path: string, jumpLine?: number, record = true) => {
    if (record && activePath && activePath !== path) {
      setBack((b) => [...b, activePath]);
      setFwd([]);
    }
    setActivePath(path);
    resetTransient();
    if (jumpLine) jumpTo(jumpLine);
  };

  const openFile = (path: string, jumpLine?: number) => {
    if (openPaths.has(path)) {
      activate(path, jumpLine);
      return;
    }
    const kind = kindOf(path);
    setTabs((ts) => [...ts, { path, kind, loading: true, err: null, text: '', draft: '', cases: null }]);
    if (activePath && activePath !== path) {
      setBack((b) => [...b, activePath]);
      setFwd([]);
    }
    setActivePath(path);
    resetTransient();
    if (kind === 'xml') {
      api
        .readXmlCases(path)
        .then((cases) => patchTab(path, { loading: false, cases }))
        .catch((e) =>
          /no testcases/i.test(e.message)
            ? patchTab(path, { loading: false, cases: [] })
            : patchTab(path, { loading: false, err: e.message }),
        );
    } else {
      api
        .readSel(path)
        .then((t) => {
          patchTab(path, { loading: false, text: t, draft: t });
          if (jumpLine) jumpTo(jumpLine);
        })
        .catch((e) => patchTab(path, { loading: false, err: e.message }));
    }
  };

  useEffect(() => {
    if (!pendingOpen) return;
    loadFiles();
    openFile(pendingOpen);
    onConsumePending?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingOpen]);

  const closeTab = (path: string) => {
    const t = tabs.find((x) => x.path === path);
    if (t && t.kind !== 'xml' && t.draft !== t.text && !window.confirm(`Discard unsaved changes in ${baseName(path)}?`))
      return;
    const idx = tabs.findIndex((x) => x.path === path);
    const next = tabs.filter((x) => x.path !== path);
    setTabs(next);
    setBack((b) => b.filter((p) => p !== path));
    setFwd((f) => f.filter((p) => p !== path));
    if (activePath === path) {
      const nextActive = next.length ? next[Math.min(idx, next.length - 1)].path : null;
      setActivePath(nextActive);
      resetTransient();
    }
  };

  // back/forward: pop the nearest still-open path, pushing the current one onto the other stack
  const navHistory = (dir: 'back' | 'fwd') => {
    const src = dir === 'back' ? back : fwd;
    const setSrc = dir === 'back' ? setBack : setFwd;
    const setDst = dir === 'back' ? setFwd : setBack;
    let i = src.length - 1;
    while (i >= 0 && !openPaths.has(src[i])) i--;
    if (i < 0) {
      setSrc([]);
      return;
    }
    const target = src[i];
    setSrc(src.slice(0, i));
    if (activePath) setDst((d) => [...d.filter((p) => p !== activePath), activePath]);
    setActivePath(target);
    resetTransient();
  };
  const canBack = back.some((p) => openPaths.has(p));
  const canFwd = fwd.some((p) => openPaths.has(p));

  // ctrl/⌘-click or double-click on a reference line → jump within this file or open the target file
  const onNavigate = (p: string, l?: number) => {
    if (active && p === active.path) {
      if (l) jumpTo(l);
    } else {
      openFile(p, l);
    }
  };

  const setDraft = (v: string) => {
    if (active) patchTab(active.path, { draft: v });
  };

  const save = () => {
    if (!active || active.kind === 'xml') return;
    const path = active.path;
    const draft = active.draft;
    setSaving(true);
    setSaveMsg(null);
    setSaved(false);
    setParseErrors([]);
    api
      .saveSel(path, draft)
      .then((r) => {
        if (r.ok) {
          patchTab(path, { text: draft });
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
    if (!active) return;
    const from = active.path;
    const to = renameTo.trim();
    if (!to || to === from) {
      setRenaming(false);
      return;
    }
    setBusy('Renaming…');
    api
      .renameTestcaseFile(from, to)
      .then(() => {
        setRenaming(false);
        setTabs((ts) => ts.map((t) => (t.path === from ? { ...t, path: to, kind: kindOf(to) } : t)));
        setBack((b) => b.map((p) => (p === from ? to : p)));
        setFwd((f) => f.map((p) => (p === from ? to : p)));
        if (activePath === from) setActivePath(to);
        loadFiles();
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
        const idx = tabs.findIndex((x) => x.path === path);
        const next = tabs.filter((x) => x.path !== path);
        setTabs(next);
        setBack((b) => b.filter((p) => p !== path));
        setFwd((f) => f.filter((p) => p !== path));
        if (activePath === path) setActivePath(next.length ? next[Math.min(idx, next.length - 1)].path : null);
        loadFiles();
      })
      .catch((e) => setError(e.message))
      .finally(() => setBusy(null));
  };

  const markers: EditorMarker[] = parseErrors
    .map((e) => ({ line: Number(e[1]), message: String(e[3] ?? e[2] ?? 'Parse error') }))
    .filter((m) => Number.isFinite(m.line) && m.line > 0);

  const renderNode = (n: TreeNode, depth: number): JSX.Element => {
    const pad = { paddingLeft: 8 + depth * 14 };
    if (n.children) {
      const isCollapsed = collapsed.has(n.path);
      return (
        <div key={'d:' + n.path}>
          <div className="tf-node tf-dir mono small" style={pad} onClick={() => toggleDir(n.path)}>
            <span className="tf-caret">{isCollapsed ? '▸' : '▾'}</span>
            <span className="tf-dir-name">{n.name || '(root)'}</span>
          </div>
          {!isCollapsed && n.children.map((c) => renderNode(c, depth + 1))}
        </div>
      );
    }
    return (
      <div
        key={'f:' + n.path}
        className={'tf-node tf-file' + (activePath === n.path ? ' sel' : '') + (openPaths.has(n.path) ? ' open' : '')}
        style={pad}
        onClick={() => openFile(n.path)}
        title={n.path}
      >
        <input
          type="checkbox"
          className="tf-cmp-cb"
          title="Select for compare (pick two)"
          checked={compareSel.has(n.path)}
          onClick={(e) => e.stopPropagation()}
          onChange={() => toggleCompare(n.path)}
        />
        <span className={'kind kind-' + n.kind}>{n.kind}</span>
        <span className="tf-item-name mono ellipsis">{n.name}</span>
        <button
          className="tf-del"
          title="Delete"
          onClick={(e) => {
            e.stopPropagation();
            setConfirmDel(n.path);
          }}
        >
          ✕
        </button>
      </div>
    );
  };

  return (
    <div className="view">
      <div className="view-head">
        <h2>Test files</h2>
        <div className="view-head-right">
          {compareSel.size > 0 && (
            <button
              className="btn ghost small"
              disabled={compareSel.size !== 2}
              title={compareSel.size === 2 ? 'Compare the two selected files' : 'Select exactly two files'}
              onClick={() => {
                const [a, b] = [...compareSel];
                setComparePair([a, b]);
              }}
            >
              {'\u21c4'} Compare ({compareSel.size})
            </button>
          )}
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
              <div className="tf-tree">{tree.children!.map((c) => renderNode(c, 0))}</div>
            )}
          </div>

          <div className="tf-content">
            {comparePair ? (
              <CompareFiles
                embedded
                left={comparePair[0]}
                right={comparePair[1]}
                onClose={() => setComparePair(null)}
              />
            ) : (
              <>
                {tabs.length === 0 && <div className="view-note muted">Select a file to edit, or create a new one.</div>}

            {tabs.length > 0 && (
              <div className="tf-tabs">
                <button className="tf-histbtn" title="Back" disabled={!canBack} onClick={() => navHistory('back')}>
                  ‹
                </button>
                <button className="tf-histbtn" title="Forward" disabled={!canFwd} onClick={() => navHistory('fwd')}>
                  ›
                </button>
                <div className="tf-tabstrip">
                  {tabs.map((t) => (
                    <div
                      key={t.path}
                      className={'tf-tab' + (activePath === t.path ? ' on' : '')}
                      onClick={() => activate(t.path)}
                      title={t.path}
                    >
                      <span className={'kind kind-' + t.kind}>{t.kind}</span>
                      <span className="tf-tab-name mono">{baseName(t.path)}</span>
                      {t.kind !== 'xml' && t.draft !== t.text && <span className="tf-tab-dot">•</span>}
                      <button
                        className="tf-tab-x"
                        title="Close"
                        onClick={(e) => {
                          e.stopPropagation();
                          closeTab(t.path);
                        }}
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {active && (
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
                      <span className="mono small ellipsis" title={active.path}>
                        {active.path}
                      </span>
                      <span className={'kind kind-' + active.kind}>{active.kind}</span>
                      {!debugging && !running && (
                        <div className="tf-head-actions">
                          {active.kind !== 'xml' && (
                            <>
                              <button className="btn primary small" disabled={!dirty || saving} onClick={save}>
                                {saving ? 'Saving…' : 'Save'}
                              </button>
                              <button
                                className="btn ghost small"
                                disabled={!dirty || saving}
                                onClick={() => patchTab(active.path, { draft: active.text })}
                              >
                                Reset
                              </button>
                            </>
                          )}
                          {active.kind === 'sel' && (
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
                              setRenameTo(active.path);
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

                {active.loading && <div className="view-note">Loading…</div>}
                {active.err && <div className="view-error">Could not read file ({active.err}).</div>}

                {!active.loading && !active.err && active.kind === 'xml' && (
                  <TestCaseEditor
                    filePath={active.path}
                    cases={active.cases ?? []}
                    onReload={() => {
                      const p = active.path;
                      api
                        .readXmlCases(p)
                        .then((cases) => patchTab(p, { cases }))
                        .catch(() => patchTab(p, { cases: [] }));
                    }}
                  />
                )}

                {!active.loading && !active.err && active.kind !== 'xml' &&
                  (debugging && active.kind === 'sel' ? (
                    <SelDebugger filePath={active.path} onExit={() => setDebugging(false)} />
                  ) : running && active.kind === 'sel' ? (
                    <SelRunner filePath={active.path} code={active.draft} onExit={() => setRunning(false)} />
                  ) : (
                    <>
                      {active.kind === 'sel' ? (
                        <Suspense fallback={<div className="view-note">Loading editor…</div>}>
                          <SelCodeMirror
                            key={active.path}
                            value={active.draft}
                            onChange={setDraft}
                            filePath={active.path}
                            diagnostics={markers}
                            onSave={save}
                            gotoLine={gotoLine}
                            gotoSeq={gotoSeq}
                            onNavigate={onNavigate}
                            onNavError={flashNav}
                          />
                        </Suspense>
                      ) : (
                        <CodeEditor key={active.path} value={active.draft} onChange={setDraft} markers={markers} gotoLine={gotoLine} />
                      )}
                      {navMsg && <div className="view-note muted small tf-navmsg">{navMsg}</div>}
                      {saveMsg && <div className="view-error">{saveMsg}</div>}
                      {parseErrors.length > 0 && (
                        <div className="tf-problems">
                          <div className="tf-problems-h small">{parseErrors.length} parse error(s) — file not saved</div>
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
                        {active.kind === 'sel'
                          ? 'Ctrl/Cmd-S saves; Ctrl-Space autocompletes. Saving parse-validates server-side; a backup .bk is kept.'
                          : 'Saving parse-validates the script server-side; a backup .bk is kept.'}
                      </div>
                    </>
                  ))}
              </>
            )}
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
          <button className="btn small" style={{ color: 'var(--red)' }} onClick={() => doDelete(confirmDel)}>
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
