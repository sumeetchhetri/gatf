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
// CodeMirror 6 wiring for cross-file navigation (C2.1). The resolve/classify logic is in
// navResolve.ts (pure, headless-tested); this module binds it to mouse events + the api client.
//
// Phase 7 fix — the defect was the click wiring, not the resolver (headless nav-target tests were
// 8/8 but nothing happened in-browser). Three changes make it work:
//   1. Navigate on `click` (fires after selection settles) instead of `mousedown` — a modifier
//      mousedown in CM6 starts a rectangular/added-cursor selection and swallowed our handler.
//      `mousedown` now only preventDefaults that selection on a nav line so the click stays clean.
//   2. posAtCoords(coords, false) returns the *nearest* position instead of null when the point
//      isn't exactly over a glyph, so an edge/padding click still resolves its line.
//   3. Resolve failures surface via onNavError instead of being swallowed by an empty .catch.
import { EditorView, ViewPlugin, Decoration, type DecorationSet } from '@codemirror/view';
import { RangeSetBuilder, type Extension } from '@codemirror/state';
import { api } from '../api';
import { navTarget, navigateForLine, type NavHooks } from './navResolve';

export { navTarget } from './navResolve';
export type { NavTarget } from './navResolve';

const resolver = {
  resolveRef: (path: string, ref: string) => api.resolveRef(path, ref),
  resolveSubtest: (path: string, call: string) => api.resolveSubtest(path, call),
};

// true if the click point sits on a navigable reference line.
function isNavAt(e: MouseEvent, view: EditorView): boolean {
  const pos = view.posAtCoords({ x: e.clientX, y: e.clientY }, false);
  if (pos == null) return false;
  return navTarget(view.state.doc.lineAt(pos).text) != null;
}

function navAt(e: MouseEvent, view: EditorView, hooks: NavHooks): boolean {
  const pos = view.posAtCoords({ x: e.clientX, y: e.clientY }, false);
  if (pos == null) return false;
  const line = view.state.doc.lineAt(pos);
  if (!navTarget(line.text)) return false;
  e.preventDefault();
  void navigateForLine(line.text, resolver, hooks);
  return true;
}

// underline/pointer affordance on navigable lines, and a modifier-held class (VS Code style)
function decoPlugin() {
  return ViewPlugin.fromClass(
    class {
      decorations: DecorationSet;
      onKey: (e: KeyboardEvent) => void;
      onBlur: () => void;
      constructor(readonly view: EditorView) {
        this.decorations = this.build(view);
        this.onKey = (e) => view.dom.classList.toggle('cm-mod-held', e.ctrlKey || e.metaKey);
        this.onBlur = () => view.dom.classList.remove('cm-mod-held');
        window.addEventListener('keydown', this.onKey);
        window.addEventListener('keyup', this.onKey);
        window.addEventListener('blur', this.onBlur);
      }
      update(u: { docChanged: boolean; viewportChanged: boolean; view: EditorView }) {
        if (u.docChanged || u.viewportChanged) this.decorations = this.build(u.view);
      }
      build(view: EditorView): DecorationSet {
        const b = new RangeSetBuilder<Decoration>();
        for (const { from, to } of view.visibleRanges) {
          let pos = from;
          while (pos <= to) {
            const line = view.state.doc.lineAt(pos);
            if (navTarget(line.text)) b.add(line.from, line.from, Decoration.line({ class: 'cm-nav-line' }));
            pos = line.to + 1;
          }
        }
        return b.finish();
      }
      destroy() {
        window.removeEventListener('keydown', this.onKey);
        window.removeEventListener('keyup', this.onKey);
        window.removeEventListener('blur', this.onBlur);
      }
    },
    { decorations: (v) => v.decorations },
  );
}

export function seleasyNav(
  getPath: () => string | null,
  onNavigate: (path: string, line?: number) => void,
  onNavError?: (msg: string) => void,
): Extension {
  const hooks: NavHooks = { getPath, onNavigate, onError: onNavError };
  return [
    decoPlugin(),
    EditorView.domEventHandlers({
      // Stop CM from starting a modifier-selection on a nav line so the click stays a plain click.
      mousedown: (e, view) =>
        (e.ctrlKey || e.metaKey) && isNavAt(e, view) ? (e.preventDefault(), true) : false,
      // Navigate here (selection has settled). Modifier for single-click; double-click needs none.
      click: (e, view) => ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey ? navAt(e, view, hooks) : false),
      dblclick: (e, view) => navAt(e, view, hooks),
    }),
  ];
}
