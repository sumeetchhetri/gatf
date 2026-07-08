// Cross-file navigation for the seleasy editor (C2.1) — ctrl/⌘-click or double-click on a
// reference line jumps to its target, matching the classic editorEvents() behavior:
//   include/import/config/dynprops <file>  -> GET …&referencedFile=<file>        -> [targetFile]
//   @call "subtest"                         -> GET …&possibleSubtestFuncCall=<ln> -> [targetFile, line]
//   goto https?://…                         -> open the URL in a new tab
import { EditorView, ViewPlugin, Decoration, type DecorationSet } from '@codemirror/view';
import { RangeSetBuilder, type Extension } from '@codemirror/state';
import { api } from '../api';

const REF_KW = ['dynprops', 'config', 'include', 'import'];

export type NavTarget = { kind: 'ref' | 'subtest' | 'goto'; arg: string };

export function navTarget(lineText: string): NavTarget | null {
  const t = lineText.trim();
  for (const kw of REF_KW) if (t.startsWith(kw + ' ')) return { kind: 'ref', arg: t.slice(kw.length).trim() };
  if (t.startsWith('@call ')) return { kind: 'subtest', arg: t }; // pass the whole @call line
  if (t.startsWith('goto ')) return { kind: 'goto', arg: t.slice(5).trim() };
  return null;
}

function navAt(
  e: MouseEvent,
  view: EditorView,
  getPath: () => string | null,
  onNavigate: (path: string, line?: number) => void,
): boolean {
  const pos = view.posAtCoords({ x: e.clientX, y: e.clientY });
  if (pos == null) return false;
  const line = view.state.doc.lineAt(pos);
  const nt = navTarget(line.text);
  if (!nt) return false;
  e.preventDefault();
  if (nt.kind === 'goto') {
    if (/^https?:\/\//i.test(nt.arg)) window.open(nt.arg, '_blank', 'noopener');
    return true;
  }
  const path = getPath();
  if (!path) return true;
  if (nt.kind === 'ref') {
    api
      .resolveRef(path, nt.arg)
      .then((out) => {
        const t = out && out[0];
        if (t) onNavigate(t);
      })
      .catch(() => {});
  } else {
    api
      .resolveSubtest(path, nt.arg)
      .then((out) => {
        const t = out && out[0];
        if (t) onNavigate(t, Number(out[1]));
      })
      .catch(() => {});
  }
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

export function seleasyNav(getPath: () => string | null, onNavigate: (path: string, line?: number) => void): Extension {
  return [
    decoPlugin(),
    EditorView.domEventHandlers({
      mousedown: (e, view) => (e.ctrlKey || e.metaKey ? navAt(e, view, getPath, onNavigate) : false),
      dblclick: (e, view) => navAt(e, view, getPath, onNavigate),
    }),
  ];
}
