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
import { useEffect, useRef } from 'react';
import { EditorState } from '@codemirror/state';
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
  drawSelection,
} from '@codemirror/view';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import { syntaxHighlighting, bracketMatching } from '@codemirror/language';
import { autocompletion, completionKeymap, closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete';
import { lintGutter, lintKeymap, setDiagnostics, type Diagnostic } from '@codemirror/lint';
import { seleasyLanguage, seleasyHighlight } from '../editor/seleasyLang';
import { mkSeleasyCompletion } from '../editor/seleasyComplete';
import { seleasyNav } from '../editor/seleasyNav';

export interface EditorDiag {
  line: number; // 1-based
  message: string;
}

const cmTheme = EditorView.theme({
  '&': {
    height: '55vh',
    minHeight: '320px',
    fontSize: '12.5px',
    backgroundColor: 'var(--panel)',
    color: 'var(--fg)',
    border: '1px solid var(--border)',
    borderRadius: '6px',
  },
  '.cm-content': { fontFamily: 'var(--mono, monospace)', caretColor: 'var(--fg)' },
  '.cm-gutters': {
    backgroundColor: 'var(--panel2, rgba(127,127,127,.06))',
    color: 'var(--muted)',
    border: 'none',
    borderRight: '1px solid var(--border)',
  },
  '.cm-activeLine': { backgroundColor: 'rgba(127,127,127,.06)' },
  '.cm-activeLineGutter': { backgroundColor: 'rgba(127,127,127,.10)' },
  '&.cm-focused': { outline: 'none' },
  '.cm-scroller': { overflow: 'auto', lineHeight: '1.5' },
  '.cm-tooltip': { backgroundColor: 'var(--panel)', color: 'var(--fg)', border: '1px solid var(--border)' },
  '.cm-tooltip-autocomplete ul li[aria-selected]': { backgroundColor: 'var(--accent, #2b6cb0)', color: '#fff' },
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
}) {
  const hostRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const onChangeRef = useRef(onChange);
  const onSaveRef = useRef(onSave);
  const pathRef = useRef(filePath);
  const gotoLineRef = useRef(gotoLine);
  const onNavigateRef = useRef(onNavigate);
  const onNavErrorRef = useRef(onNavError);
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
      ],
    });
    const view = new EditorView({ state, parent: hostRef.current });
    viewRef.current = view;
    return () => {
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

  return <div className="scm" ref={hostRef} />;
}
