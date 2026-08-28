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
// Pure cross-file-navigation logic for the seleasy editor, extracted so it can be unit-tested
// headless (no CodeMirror layout, no DOM, no fetch). seleasyNav.ts wires this to CM6 mouse
// events + the api client; the click->resolve->navigate path itself lives here and is covered
// by tools/smoke/test-nav-wiring.mjs.
//
// Classic editorEvents() parity:
//   include/import/config/dynprops <file>  -> GET …&referencedFile=<file>       -> [targetFile]
//   @call "subtest"                         -> GET …&possibleSubtestFuncCall=<ln> -> [targetFile, line]
//   goto https?://…                         -> open the URL in a new tab

const REF_KW = ['dynprops', 'config', 'include', 'import'];

export type NavKind = 'ref' | 'subtest' | 'goto';
export type NavTarget = { kind: NavKind; arg: string };

// Phase 7: drop a trailing end-of-line // comment before classifying, so a commented reference
// line (`config app.properties //note`) still resolves. Mirrors Command.stripTrailingLineComment:
// the `//` must be whitespace-preceded, so `://` in a URL is preserved. Quotes are masked first
// so a `//` inside a string is never seen as a comment.
export function stripEolComment(line: string): string {
  let masked = line.replace(/"(?:\\.|[^"\\])*"/g, (m) => '\0'.repeat(m.length));
  masked = masked.replace(/'(?:\\.|[^'\\])*'/g, (m) => '\0'.repeat(m.length));
  for (let i = 1; i < masked.length - 1; i++) {
    if (masked[i] === '/' && masked[i + 1] === '/' && (masked[i - 1] === ' ' || masked[i - 1] === '\t')) {
      return line.slice(0, i).replace(/[ \t]+$/, '');
    }
  }
  return line;
}

export function navTarget(lineText: string): NavTarget | null {
  const t = stripEolComment(lineText).trim();
  for (const kw of REF_KW) if (t.startsWith(kw + ' ')) return { kind: 'ref', arg: t.slice(kw.length).trim() };
  if (t.startsWith('@call ')) return { kind: 'subtest', arg: t }; // pass the whole @call line
  if (t.startsWith('goto ')) return { kind: 'goto', arg: t.slice(5).trim() };
  return null;
}

export interface NavResolver {
  resolveRef(path: string, ref: string): Promise<string[]>;
  resolveSubtest(path: string, call: string): Promise<[string, number]>;
}

export interface NavHooks {
  getPath(): string | null;
  onNavigate(path: string, line?: number): void;
  openUrl?(url: string): void;
  onError?(msg: string): void;
}

// Resolve one line and drive navigation. Returns true if the line was a navigable reference
// (so the caller can suppress the editor's default handling), false if it was ordinary code.
// Never throws — resolve failures go to hooks.onError so they are visible, not silent.
export async function navigateForLine(lineText: string, resolver: NavResolver, hooks: NavHooks): Promise<boolean> {
  const nt = navTarget(lineText);
  if (!nt) return false;
  if (nt.kind === 'goto') {
    if (/^https?:\/\//i.test(nt.arg)) {
      if (hooks.openUrl) hooks.openUrl(nt.arg);
      else window.open(nt.arg, '_blank', 'noopener');
    }
    return true;
  }
  const path = hooks.getPath();
  if (!path) return true;
  try {
    if (nt.kind === 'ref') {
      const out = await resolver.resolveRef(path, nt.arg);
      const t = out && out[0];
      if (t) hooks.onNavigate(t);
      else hooks.onError?.(`Couldn't resolve “${nt.arg}” — not found under the test dir.`);
    } else {
      const out = await resolver.resolveSubtest(path, nt.arg);
      const t = out && out[0];
      if (t) hooks.onNavigate(t, Number(out[1]));
      else hooks.onError?.(`Couldn't find a subtest for ${nt.arg.slice(0, 48)}.`);
    }
  } catch (e) {
    hooks.onError?.(`Navigation failed: ${(e as Error)?.message ?? 'request error'}`);
  }
  return true;
}
