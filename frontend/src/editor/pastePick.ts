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
// Δ148: paste a picked locator line into the seleasy editor. The line comes from the server as a whole seleasy step
// (PickLocator.line, the recorder's own click step), so it is inserted verbatim as its own line — never inside a string
// or another statement, and never re-quoted here. The only text added is the newline and the current line's indent.
import type { Text } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';

export interface LineInsert {
  from: number;
  to: number;
  insert: string;
  /** cursor after the insert */
  anchor: number;
}

/** Where the line goes: replacing the cursor's line when it is blank, else on a new line after it. Null for a line
 *  that is not a single line (the server never sends one; refuse rather than split it). */
export function lineInsertion(doc: Text, pos: number, line: string): LineInsert | null {
  if (!line || /[\r\n\u2028\u2029]/.test(line)) return null;
  const at = Math.max(0, Math.min(pos, doc.length));
  const l = doc.lineAt(at);
  const indent = /^[ \t]*/.exec(l.text)![0];
  if (l.text.trim() === '') {
    const insert = indent + line;
    return { from: l.from, to: l.to, insert, anchor: l.from + insert.length };
  }
  const insert = '\n' + indent + line;
  return { from: l.to, to: l.to, insert, anchor: l.to + insert.length };
}

export function insertPickedLine(view: EditorView, line: string): boolean {
  const ins = lineInsertion(view.state.doc, view.state.selection.main.head, line);
  if (!ins) return false;
  view.dispatch({ changes: { from: ins.from, to: ins.to, insert: ins.insert }, selection: { anchor: ins.anchor }, scrollIntoView: true });
  view.focus();
  return true;
}
