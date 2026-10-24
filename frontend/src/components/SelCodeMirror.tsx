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
import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { EditorState, StateEffect, StateField } from '@codemirror/state';
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
  drawSelection,
  Decoration,
  type DecorationSet,
} from '@codemirror/view';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import { syntaxHighlighting, bracketMatching } from '@codemirror/language';
import { autocompletion, completionKeymap, closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete';
import { lintGutter, lintKeymap, setDiagnostics, type Diagnostic } from '@codemirror/lint';
import { seleasyLanguage, seleasyHighlight } from '../editor/seleasyLang';
import { mkSeleasyCompletion } from '../editor/seleasyComplete';
import { seleasyNav } from '../editor/seleasyNav';
import { usePicked, pickStore } from '../editor/pickStore';
import { insertPickedLine } from '../editor/pastePick';
import { LivePicker, type LiveState } from '../editor/livePicker';
import { matchText } from '../trace/picker';
import { api } from '../api';
import { selThemeSpec } from './selTheme';

export interface EditorDiag {
  line: number; // 1-based
  message: string;
}

const cmTheme = EditorView.theme(selThemeSpec); // Δ163n: spec + rationale in selTheme.ts

// H1 (Δ150): the line a heal proposal would replace, marked until the heal is accepted or closed
const setHealMark = StateEffect.define<number | null>();
const healMarkField = StateField.define<DecorationSet>({
  create: () => Decoration.none,
  update(deco, tr) {
    let d = deco.map(tr.changes);
    for (const e of tr.effects) {
      if (!e.is(setHealMark)) continue;
      const n = e.value;
      d = n != null && n >= 1 && n <= tr.state.doc.lines
        ? Decoration.set([Decoration.line({ class: 'cm-heal-line' }).range(tr.state.doc.line(n).from)])
        : Decoration.none;
    }
    return d;
  },
  provide: (f) => EditorView.decorations.from(f),
});

export default function SelCodeMirror({
  value,
  onChange,
  filePath,
  diagnostics = [],
  onSave,
  gotoLine,
  gotoSeq,
  onNavigate,
  onNavError,
  markLine,
  initialScroll,
  onScrollTop,
}: {
  value: string;
  onChange?: (v: string) => void;
  filePath: string | null;
  diagnostics?: EditorDiag[];
  onSave?: () => void;
  gotoLine?: number | null;
  gotoSeq?: number;
  onNavigate?: (path: string, line?: number) => void;
  onNavError?: (msg: string) => void;
  markLine?: number | null;
  /** Δ163n (bug 7): scrollTop to restore when the editor is created, and a report of later scrolls. */
  initialScroll?: number;
  onScrollTop?: (y: number) => void;
}) {
  const hostRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const onChangeRef = useRef(onChange);
  const onSaveRef = useRef(onSave);
  const pathRef = useRef(filePath);
  const gotoLineRef = useRef(gotoLine);
  const onNavigateRef = useRef(onNavigate);
  const onNavErrorRef = useRef(onNavError);
  const onScrollTopRef = useRef(onScrollTop);
  onScrollTopRef.current = onScrollTop;
  onChangeRef.current = onChange;
  onSaveRef.current = onSave;
  pathRef.current = filePath;
  gotoLineRef.current = gotoLine;
  onNavigateRef.current = onNavigate;
  onNavErrorRef.current = onNavError;

  // build the editor once
  useEffect(() => {
    if (!hostRef.current) return;
    const state = EditorState.create({
      doc: value,
      extensions: [
        lineNumbers(),
        highlightActiveLine(),
        highlightActiveLineGutter(),
        history(),
        drawSelection(),
        bracketMatching(),
        closeBrackets(),
        seleasyLanguage,
        syntaxHighlighting(seleasyHighlight),
        autocompletion({ override: [mkSeleasyCompletion(() => pathRef.current)] }),
        seleasyNav(
          () => pathRef.current,
          (p, l) => onNavigateRef.current?.(p, l),
          (m) => onNavErrorRef.current?.(m),
        ),
        lintGutter(),
        EditorView.lineWrapping,
        keymap.of([
          { key: 'Mod-s', preventDefault: true, run: () => (onSaveRef.current?.(), true) },
          indentWithTab,
          ...closeBracketsKeymap,
          ...defaultKeymap,
          ...historyKeymap,
          ...completionKeymap,
          ...lintKeymap,
        ]),
        EditorView.updateListener.of((u) => {
          if (u.docChanged) onChangeRef.current?.(u.state.doc.toString());
        }),
        cmTheme,
        healMarkField,
      ],
    });
    const view = new EditorView({ state, parent: hostRef.current });
    viewRef.current = view;
    const onScroll = () => onScrollTopRef.current?.(view.scrollDOM.scrollTop);
    view.scrollDOM.addEventListener('scroll', onScroll, { passive: true });
    if (initialScroll) {
      // after the first measure, so the content height is known
      view.requestMeasure({ read: () => null, write: () => { view.scrollDOM.scrollTop = initialScroll; } });
    }
    return () => {
      view.scrollDOM.removeEventListener('scroll', onScroll);
      view.destroy();
      viewRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // external value changes (open a different file, Reset) → replace doc
  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    if (value !== view.state.doc.toString()) {
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } });
    }
  }, [value]);

  // parse-error diagnostics → CM lint (inline squiggles + gutter)
  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const lines = view.state.doc.lines;
    const diags: Diagnostic[] = diagnostics
      .filter((d) => Number.isFinite(d.line) && d.line >= 1 && d.line <= lines)
      .map((d) => {
        const line = view.state.doc.line(d.line);
        return { from: line.from, to: line.to, severity: 'error', message: d.message };
      });
    view.dispatch(setDiagnostics(view.state, diags));
  }, [diagnostics]);

  // Δ150: mark the line a shown heal targets (after the doc sync above, so the line exists)
  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    view.dispatch({ effects: setHealMark.of(markLine ?? null) });
  }, [markLine, value]);

  // jump to a line (from a problem click or a cross-file navigation) — gotoSeq re-triggers even for the same line
  useEffect(() => {
    const view = viewRef.current;
    const gl = gotoLineRef.current;
    if (!view || gl == null) return;
    const ln = Math.max(1, Math.min(gl, view.state.doc.lines));
    const line = view.state.doc.line(ln);
    view.dispatch({ selection: { anchor: line.from }, scrollIntoView: true });
    view.focus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gotoSeq]);

  return (
    <>
      <PickBar viewRef={viewRef} />
      <div className="scm" ref={hostRef} />
    </>
  );
}

/**
 * Δ148: paste a picked locator. The line is the server's (PickLocator / LocatorRanking), inserted verbatim as its own
 * line below the cursor. Sources: the Trace Viewer's snapshot picker (pickStore), or the live browser of a recording
 * session (opt-in, one pick per click of the button).
 */
function PickBar({ viewRef }: { viewRef: MutableRefObject<EditorView | null> }) {
  const picked = usePicked();
  const [live, setLive] = useState<LiveState>({ phase: 'idle' });
  const liveRef = useRef<LivePicker | null>(null);
  useEffect(() => {
    const lp = new LivePicker(
      api,
      (line) => { if (viewRef.current) insertPickedLine(viewRef.current, line); },
      setLive,
    );
    liveRef.current = lp;
    return () => lp.dispose();
  }, [viewRef]);
  const paste = () => {
    if (picked && viewRef.current && insertPickedLine(viewRef.current, picked.line)) pickStore.set(null);
  };
  const lastPick = live.phase === 'idle' ? live.pick : undefined;
  return (
    <div className="pick-bar small">
      {live.phase === 'armed' ? (
        <>
          <span>Click an element in the {live.browser} recording window (Esc there cancels)…</span>
          <button className="btn ghost small" onClick={() => liveRef.current?.cancel()}>Cancel</button>
        </>
      ) : (
        <button className="btn ghost small" disabled={live.phase === 'arming'} onClick={() => void liveRef.current?.start()}
          title="Pick an element in the browser of an active recording session">
          ⌖ Pick in live browser
        </button>
      )}
      {live.phase === 'idle' && live.message && <span className="muted">{live.message}</span>}
      {lastPick && !lastPick.cancelled && live.phase === 'idle' && live.message && (
        <span className="muted">
          {lastPick.candidates.map((c, i) => <code key={i}>{(c.locator ?? c.kind) + ' (' + matchText(c.n) + ') '}</code>)}
          {lastPick.fallback && <code>{lastPick.fallback.locator + ' (' + matchText(lastPick.fallback.n) + ')'}</code>}
        </span>
      )}
      {picked && (
        <>
          <span className="muted">Picked ({picked.origin}):</span>
          <code>{picked.line}</code>
          <button className="btn small primary" onClick={paste}>Insert below cursor</button>
          <button className="btn ghost small" onClick={() => pickStore.set(null)}>×</button>
        </>
      )}
    </div>
  );
}
