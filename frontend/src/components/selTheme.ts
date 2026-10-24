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
// The seleasy editor's CodeMirror theme, as a plain spec (EditorView.theme(selThemeSpec) in SelCodeMirror) so it can be
// unit-checked. Every colour comes from the app's theme tokens (styles.css, both :root[data-theme] blocks).
//
// Δ163n (bug 6): drawSelection() draws the caret as .cm-cursor (the native caret is hidden), and this theme is not a
// CodeMirror "dark" theme, so the base theme painted the cursor near-black on the dark panel: invisible. Cursor,
// selection, bracket match and active line now use the tokens, so they read in both themes.
export const SEL_MIX = {
  selection: 30, // % of --accent over --panel behind selected text
  bracket: 28, // % of --accent behind a matching bracket
  activeLine: 7, // % of --fg over --panel on the active line
};

const mix = (token: string, pct: number) => `color-mix(in srgb, var(${token}) ${pct}%, transparent)`;

export const selThemeSpec = {
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
  '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--fg)', borderLeftWidth: '2px' },
  '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection':
    { backgroundColor: mix('--accent', SEL_MIX.selection) },
  '&.cm-focused .cm-matchingBracket': { backgroundColor: mix('--accent', SEL_MIX.bracket), outline: '1px solid var(--accent)', color: 'inherit' },
  '&.cm-focused .cm-nonmatchingBracket': { backgroundColor: mix('--red', SEL_MIX.bracket), color: 'inherit' },
  '.cm-gutters': {
    backgroundColor: 'var(--panel2, rgba(127,127,127,.06))',
    color: 'var(--muted)',
    border: 'none',
    borderRight: '1px solid var(--border)',
  },
  '.cm-activeLine': { backgroundColor: mix('--fg', SEL_MIX.activeLine) },
  '.cm-activeLineGutter': { backgroundColor: mix('--fg', SEL_MIX.activeLine + 4), color: 'var(--fg)' },
  '&.cm-focused': { outline: 'none' },
  '.cm-scroller': { overflow: 'auto', lineHeight: '1.5' },
  '.cm-tooltip': { backgroundColor: 'var(--panel)', color: 'var(--fg)', border: '1px solid var(--border)' },
  '.cm-tooltip-autocomplete ul li[aria-selected]': { backgroundColor: 'var(--accent, #2b6cb0)', color: '#fff' },
  '.cm-heal-line': { backgroundColor: 'rgba(248, 81, 73, 0.14)' },
};
