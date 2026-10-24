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
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { api, getActiveConfig } from '../api';
import type { TestcaseFileEntry, SelParseError, ApiTestCase } from '../api';
import {
  allDirs,
  ancestors,
  buildTree,
  filesHash,
  kindOf,
  loadCollapsed,
  loadListHidden,
  normPath,
  pathFromHash,
  saveCollapsed,
  saveListHidden,
  tfMemory,
  type Kind,
  type TreeNode,
} from './testFilesState';
import CodeEditor, { type EditorMarker } from '../components/CodeEditor';
import TestCaseEditor from '../components/TestCaseEditor';
import SelDebugger from '../components/SelDebugger';
import SelRunner from '../components/SelRunner';
import { initialWatch, onFinished as watchFinished, onSaved as watchSaved, watchOn, type WatchAction, type WatchState } from '../components/watchQueue';
import CompareFiles from './CompareFiles';
import { HealBar } from '../components/HealPanel';
import { useHeal } from '../editor/heal';
// CodeMirror 6 is heavy (~120KB gzip); load it only when a .sel file is opened.
const SelCodeMirror = lazy(() => import('../components/SelCodeMirror'));

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

export default function TestFilesView({
  pendingOpen,
  onConsumePending,
  onOpenTrace,
}: { pendingOpen?: string; onConsumePending?: () => void; onOpenTrace?: (url: string) => void } = {}) {
  const [files, setFiles] = useState<TestcaseFileEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null); // CRUD in flight (message)

  // open tabs + which is active + browser-style back/forward history of file paths
  const [tabs, setTabs] = useState<Tab[]>([]);
  const [activePath, setActivePath] = useState<string | null>(null);
  const [back, setBack] = useState<string[]>([]);
  const [fwd, setFwd] = useState<string[]>([]);
  const cfg = getActiveConfig() ?? '';
  // Δ163n (bug 2): collapsed folders are remembered per viewer + config; (bug 1) the file list can be hidden
  const [collapsed, setCollapsed] = useState<Set<string>>(() => loadCollapsed(cfg)); // collapsed dir paths
  const [listHidden, setListHidden] = useState<boolean>(loadListHidden);
  const listRef = useRef<HTMLDivElement>(null);
  // Δ163n (bug 8): upload test files / a project zip into a folder
  const [upOpen, setUpOpen] = useState(false);
  const [upDir, setUpDir] = useState('');
  const [upOverwrite, setUpOverwrite] = useState(false);
  const [upMsg, setUpMsg] = useState<string | null>(null);
  const upRef = useRef<HTMLInputElement>(null);

  // transient state for the ACTIVE tab (reset when the active tab changes)
  const [saving, setSaving] = useState(false);
  const [saveMsg, setSaveMsg] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [parseErrors, setParseErrors] = useState<SelParseError[]>([]);
  const [gotoLine, setGotoLine] = useState<number | null>(null);
  const [gotoSeq, setGotoSeq] = useState(0);
  const [debugging, setDebugging] = useState(false);
  const [running, setRunning] = useState(false);
  // M15: watch mode — the runner sits beside the editor and every successful save re-runs the script
  const [watching, setWatching] = useState(false);
  const [watch, setWatch] = useState<WatchState>(initialWatch);
  const watchRef = useRef(watch);
  watchRef.current = watch;
  const applyWatch = ([next, action]: [WatchState, WatchAction]) => {
    setWatch(next);
    if (action === 'stop') api.stopRun().catch(() => {});
  };
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
  // H1 (Δ150): the line a shown heal targets, marked in the editor
  const healNow = useHeal();
  const healLine = healNow && active && healNow.file === active.path ? healNow.line : null;

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
    setWatching(false);
    setWatch(initialWatch);
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
      .then((fs) => setFiles(fs.map((f) => ({ ...f, path: normPath(f.path) }))))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };
  useEffect(loadFiles, []);

  // ---- directory tree ----
  const tree = useMemo(() => buildTree(files.map((f) => f.path)), [files]);
  const dirs = useMemo(() => allDirs(tree), [tree]);

  const toggleDir = (p: string) =>
    setCollapsed((s) => {
      const n = new Set(s);
      if (n.has(p)) n.delete(p);
      else n.add(p);
      return n;
    });
  useEffect(() => saveCollapsed(cfg, collapsed), [cfg, collapsed]);
  // the tree's scroll position comes back with the view (bug 7)
  const listRestored = useRef(false);
  useEffect(() => {
    if (listRestored.current || files.length === 0 || !listRef.current) return;
    listRestored.current = true;
    listRef.current.scrollTop = tfMemory.listScroll.get(cfg) ?? 0;
  }, [files, cfg]);
  useEffect(() => saveListHidden(listHidden), [listHidden]);
  // auto-expand to the active file
  useEffect(() => {
    if (!activePath) return;
    const anc = ancestors(activePath);
    if (anc.some((d) => collapsed.has(d)))
      setCollapsed((s) => {
        const n = new Set(s);
        anc.forEach((d) => n.delete(d));
        return n;
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activePath]);

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

  // ---- Δ163n (bug 7): the selected file lives in the URL (#/testfiles?path=…): it survives leaving the view (Reports
  // and back), is deep-linkable, and browser back/forward moves between the files opened here ----
  const activeRef = useRef<string | null>(null);
  activeRef.current = activePath;
  const openRef = useRef(openFile);
  openRef.current = openFile;
  const urlMode = useRef<'push' | 'replace' | 'none'>('replace'); // how the next activePath change updates the URL
  useEffect(() => {
    if (!pendingOpen) {
      const p = pathFromHash(window.location.hash) ?? tfMemory.lastPath.get(cfg) ?? null;
      if (p) openFile(p);
    }
    const onHash = () => {
      const h = pathFromHash(window.location.hash);
      if (h && h !== activeRef.current) {
        urlMode.current = 'none';
        openRef.current(h);
      } else if (!h && activeRef.current && window.location.hash === filesHash(null)) {
        // the Test Files nav button again: keep the open file and put it back in the URL
        window.history.replaceState(null, '', filesHash(activeRef.current));
      }
    };
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    if (activePath) tfMemory.lastPath.set(cfg, activePath);
    else tfMemory.lastPath.delete(cfg);
    const want = filesHash(activePath);
    // a no-op (mount with no file yet, or the URL already says it) keeps the pending mode for the next change, so the
    // file restored on mount REPLACES #/testfiles instead of adding a second history entry (verifier round)
    if (window.location.hash === want) {
      if (urlMode.current === 'none') urlMode.current = 'push'; // back/forward already moved the URL
      return;
    }
    if (window.location.hash !== filesHash(null) && pathFromHash(window.location.hash) === null) return; // another view
    const mode = urlMode.current;
    urlMode.current = 'push';
    if (mode === 'none') return;
    if (mode === 'replace') window.history.replaceState(null, '', want);
    else window.location.hash = want; // a new history entry: browser back returns to the previous file
  }, [activePath, cfg]);

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
          if (watching) applyWatch(watchSaved(watchRef.current));
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
        if (activePath === from) {
          urlMode.current = 'replace'; // the old path no longer exists: don't leave it in the history
          setActivePath(to);
        }
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

  // Δ163n (bug 3): an error in an imported file is listed below but not marked on this file's line
  const errFile = (e: SelParseError) => (typeof e[2] === 'string' && e[2] ? normPath(e[2]) : null);
  const markers: EditorMarker[] = parseErrors
    .filter((e) => !active || errFile(e) === null || errFile(e) === active.path)
    .map((e) => ({ line: Number(e[1]), message: String(e[3] || e[2] || 'Parse error') }))
    .filter((m) => Number.isFinite(m.line) && m.line > 0);

  // ---- Δ163n (bug 8): upload ----
  const doUpload = async (list: FileList | null) => {
    if (!list || list.length === 0) return;
    const dir = normPath(upDir.trim());
    setBusy('Uploading…');
    setUpMsg(null);
    const written: string[] = [];
    const problems: string[] = [];
    for (const f of Array.from(list)) {
      const target = (dir ? dir + '/' : '') + f.name;
      try {
        const r = await api.uploadTestFile(target, f, upOverwrite);
        written.push(...r.written);
        problems.push(...r.skipped);
      } catch (e) {
        problems.push(f.name + ': ' + (e as Error).message);
      }
    }
    setBusy(null);
    if (upRef.current) upRef.current.value = '';
    setUpMsg(
      `Uploaded ${written.length} file(s)` + (problems.length ? `; skipped ${problems.length}: ${problems.join('; ')}` : '') + '.',
    );
    loadFiles();
    if (written.length === 1 && /\.(sel|xml|props)$/i.test(written[0])) openFile(written[0]);
  };

  const renderNode = (n: TreeNode, depth: number): JSX.Element => {
    const pad = { paddingLeft: 8 + depth * 14 };
    if (n.children) {
      const isCollapsed = collapsed.has(n.path);
      return (
        <div key={'d:' + n.path}>
          <div
            className="tf-node tf-dir mono small"
            style={pad}
            title={n.path}
            role="treeitem"
            aria-expanded={!isCollapsed}
            onClick={() => toggleDir(n.path)}
          >
            <span className="tf-caret">{isCollapsed ? '▸' : '▾'}</span>
            <span className="tf-dir-name ellipsis">{n.name || '(root)'}</span>
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
          <button
            className="btn ghost small"
            onClick={() => {
              setUpOpen((o) => !o);
              setUpDir(activePath && activePath.includes('/') ? activePath.slice(0, activePath.lastIndexOf('/')) : '');
            }}
          >
            ⇪ Upload
          </button>
          <button className="btn ghost small" onClick={loadFiles}>
            Refresh
          </button>
        </div>
      </div>

      {upOpen && (
        <div className="tf-new tf-upload">
          <input
            className="sf-input"
            placeholder="into folder (blank = top level)"
            value={upDir}
            onChange={(e) => setUpDir(e.target.value)}
          />
          <label className="small muted">
            <input type="checkbox" checked={upOverwrite} onChange={(e) => setUpOverwrite(e.target.checked)} /> overwrite
          </label>
          <input
            ref={upRef}
            type="file"
            multiple
            hidden
            data-testid="tf-upload"
            accept=".sel,.xml,.props,.csv,.json,.txt,.zip"
            onChange={(e) => doUpload(e.target.files)}
          />
          <button className="btn primary small" onClick={() => upRef.current?.click()}>
            Choose files…
          </button>
          <span className="muted small">.sel .xml .props .csv .json .txt, or a project .zip (unpacked into the folder)</span>
          <button className="btn ghost small" onClick={() => setUpOpen(false)}>
            Close
          </button>
        </div>
      )}
      {upMsg && <div className="view-note tf-upmsg">{upMsg}</div>}
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

      {/* Δ163n: a refresh (rename, upload) or an action error no longer unmounts the editor */}
      {(files.length > 0 || (!loading && !error)) && (
        <div className={'tf-split' + (listHidden ? ' tf-list-hidden' : '')}>
          <div className="tf-list" ref={listRef} onScroll={(e) => tfMemory.listScroll.set(cfg, e.currentTarget.scrollTop)}>
            <div className="tf-list-head">
              <button
                className="tf-iconbtn"
                title={listHidden ? 'Show the file list' : 'Hide the file list'}
                aria-label={listHidden ? 'Show the file list' : 'Hide the file list'}
                onClick={() => setListHidden((h) => !h)}
              >
                {listHidden ? '»' : '«'}
              </button>
              {!listHidden && dirs.length > 0 && (
                <>
                  <button className="tf-iconbtn" title="Expand all folders" onClick={() => setCollapsed(new Set())}>
                    ⊞
                  </button>
                  <button className="tf-iconbtn" title="Collapse all folders" onClick={() => setCollapsed(new Set(dirs))}>
                    ⊟
                  </button>
                </>
              )}
            </div>
            {!listHidden &&
              (files.length === 0 ? (
                <div className="view-note">No test files. Create one with “+ New file”.</div>
              ) : (
                <div className="tf-tree">{tree.children!.map((c) => renderNode(c, 0))}</div>
              ))}
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
                                disabled={dirty || watching}
                                title={dirty ? 'Save before running' : 'Run this script'}
                                onClick={() => setRunning(true)}
                              >
                                ▶ Run
                              </button>
                              <button
                                className="btn small"
                                disabled={dirty || watching}
                                title={dirty ? 'Save before debugging' : 'Debug this script'}
                                onClick={() => setDebugging(true)}
                              >
                                🐞 Debug
                              </button>
                              <button
                                className={'btn small' + (watching ? ' primary' : '')}
                                disabled={dirty && !watching}
                                title={watching ? 'Stop watching' : dirty ? 'Save before watching' : 'Run beside the editor and re-run on every save'}
                                onClick={() => {
                                  if (watching) {
                                    if (watch.running) api.stopRun().catch(() => {});
                                    setWatching(false);
                                    setWatch(initialWatch);
                                  } else {
                                    setWatching(true);
                                    applyWatch(watchOn(initialWatch));
                                  }
                                }}
                              >
                                👁 {watching ? 'Watching' : 'Watch'}
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
                    <SelDebugger filePath={active.path} onExit={() => setDebugging(false)} onOpenTrace={onOpenTrace} />
                  ) : running && active.kind === 'sel' ? (
                    <SelRunner filePath={active.path} code={active.draft} onExit={() => setRunning(false)} />
                  ) : (
                    <div className={watching && active.kind === 'sel' ? 'tf-watch' : undefined}>
                    <div>
                      {active.kind === 'sel' ? (
                        <>
                        <HealBar
                          path={active.path}
                          dirty={dirty}
                          onApplied={(content, line) => {
                            patchTab(active.path, { text: content, draft: content });
                            jumpTo(line);
                          }}
                        />
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
                            markLine={healLine}
                            initialScroll={tfMemory.scroll.get(cfg + '|' + active.path)}
                            onScrollTop={(y) => tfMemory.scroll.set(cfg + '|' + active.path, y)}
                          />
                        </Suspense>
                        </>
                      ) : (
                        <CodeEditor key={active.path} value={active.draft} onChange={setDraft} markers={markers} gotoLine={gotoLine} />
                      )}
                      {navMsg && <div className="view-note muted small tf-navmsg">{navMsg}</div>}
                      {saveMsg && <div className="view-error">{saveMsg}</div>}
                      {parseErrors.length > 0 && (
                        <div className="tf-problems">
                          <div className="tf-problems-h small">
                            {parseErrors.length} error(s) — written to disk, but the script will not run until they are fixed
                          </div>
                          {parseErrors.map((e, i) => {
                            const ln = Number(e[1]);
                            const ef = errFile(e);
                            const msg = (ef && ef !== active.path ? ef + ': ' : '') + String(e[3] || e[2] || 'Parse error');
                            return (
                              <div
                                key={i}
                                className="tf-problem"
                                onClick={() => Number.isFinite(ln) && ln > 0 && (ef && ef !== active.path ? openFile(ef, ln) : jumpTo(ln))}
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
                          ? 'Ctrl/Cmd-S saves; Ctrl-Space autocompletes. Saving parses, transpiles and compiles server-side; a backup .bk is kept.'
                          : 'Saving parse-validates the script server-side; a backup .bk is kept.'}
                      </div>
                    </div>
                    {watching && active.kind === 'sel' && watch.runId > 0 && (
                      <SelRunner
                        key={watch.runId}
                        filePath={active.path}
                        code={active.text}
                        embedded
                        onFinished={() => setWatch((w) => watchFinished(w)[0])}
                        onExit={() => {
                          if (watchRef.current.running) api.stopRun().catch(() => {});
                          setWatching(false);
                          setWatch(initialWatch);
                        }}
                      />
                    )}
                    </div>
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
