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
// seleasy language server (LSP). Provides:
//   • diagnostics  — offline structural checks mirroring tools/smoke/sel-lint.py (unbalanced
//                    braces / value-lists / block-comment / code-block, and unrecognised leading
//                    keyword), trailing-comment-aware (Phase 7). Self-contained — no running GATF
//                    server needed (the real /parse writes the file, so we don't call it here).
//   • completion   — keywords from the bundled command catalog (generated from Command.java).
//   • hover        — the backing Command class + match kind for a keyword.
// The catalog is docs/seleasy/commands.json, bundled by the grammar generator into ./commands.json,
// so the language service stays in step with the transpiler and the CM6 web editor's autocomplete.
import {
  createConnection,
  TextDocuments,
  ProposedFeatures,
  type InitializeParams,
  TextDocumentSyncKind,
  type InitializeResult,
  DiagnosticSeverity,
  type Diagnostic,
  type CompletionItem,
  CompletionItemKind,
  type Hover,
  MarkupKind,
  type Range,
} from 'vscode-languageserver/node';
import { TextDocument } from 'vscode-languageserver-textdocument';
import catalog from './commands.json';

interface Cmd {
  keyword: string;
  match: 'prefix' | 'exact' | 'regex';
  requiresArg: boolean;
  canBeBare: boolean;
  class: string;
  qualifiers?: string[];
}
const COMMANDS = (catalog as { commands: Cmd[] }).commands;

// ---- catalog partitioning (mirrors sel-lint.py Catalog) ----
const ALPHA = new Set<string>();
const MULTIWORD: string[] = [];
const SYMBOLIC: string[] = [];
const REGEX_BASES: string[] = [];
for (const c of COMMANDS) {
  const kw = c.keyword.toLowerCase();
  if (c.match === 'regex') {
    REGEX_BASES.push(kw);
    continue;
  }
  if (kw.includes(' ')) MULTIWORD.push(kw);
  else if (!/^[a-z0-9]/.test(kw)) SYMBOLIC.push(kw);
  else ALPHA.add(kw);
}
MULTIWORD.sort((a, b) => b.length - a.length);
SYMBOLIC.sort((a, b) => b.length - a.length);
const ALPHA_TOK = /^[a-z0-9_-]+/;
const byKeyword = new Map(COMMANDS.map((c) => [c.keyword.toLowerCase(), c]));

function knownLeading(line: string): boolean {
  const L = line.trim().toLowerCase();
  if (!L) return true;
  for (const kw of MULTIWORD) if (L === kw || L.startsWith(kw + ' ')) return true;
  if (!/^[a-z0-9]/.test(L)) for (const kw of SYMBOLIC) if (L.startsWith(kw)) return true;
  const m = ALPHA_TOK.exec(L);
  if (m) {
    const w = m[0];
    if (ALPHA.has(w)) return true;
    for (const rb of REGEX_BASES) if (w === rb || w.startsWith(rb)) return true;
  }
  return false;
}

// Phase 7: strip a trailing whitespace-preceded // comment (quotes masked so // in a string is safe)
function stripEol(s: string): string {
  let masked = s.replace(/"(?:\\.|[^"\\])*"/g, (mm) => '\0'.repeat(mm.length));
  masked = masked.replace(/'(?:\\.|[^'\\])*'/g, (mm) => '\0'.repeat(mm.length));
  for (let i = 1; i < masked.length - 1; i++) {
    if (masked[i] === '/' && masked[i + 1] === '/' && (masked[i - 1] === ' ' || masked[i - 1] === '\t')) {
      return s.slice(0, i).replace(/[ \t]+$/, '');
    }
  }
  return s;
}

let strictDiagnostics = false;

function lint(doc: TextDocument): Diagnostic[] {
  const out: Diagnostic[] = [];
  const lines = doc.getText().split(/\r?\n/);
  let inBlockComment = false,
    inCode = false;
  let blockDepth = 0,
    vlistDepth = 0;
  let blockOpen = 0,
    codeOpen = 0,
    commentOpen = 0;
  const at = (line0: number, from = 0, to = 1): Range => ({
    start: { line: line0, character: from },
    end: { line: line0, character: to },
  });
  const err = (line0: number, message: string) =>
    out.push({ severity: DiagnosticSeverity.Error, range: at(line0, 0, (lines[line0] ?? '').length || 1), message, source: 'seleasy' });
  const warn = (line0: number, from: number, len: number, message: string) =>
    out.push({ severity: DiagnosticSeverity.Warning, range: at(line0, from, from + len), message, source: 'seleasy' });

  for (let n = 0; n < lines.length; n++) {
    let s = lines[n].trim();
    if (inBlockComment) {
      if (s.includes('*/')) inBlockComment = false;
      continue;
    }
    if (inCode) {
      if (s === '>>>' || s === 'end code') inCode = false;
      continue;
    }
    if (vlistDepth > 0) {
      if (s.startsWith(']')) vlistDepth--;
      else if (s.startsWith('[')) vlistDepth++;
      continue;
    }
    if (!s) continue;
    if (s.startsWith('/*')) {
      if (!s.includes('*/')) {
        inBlockComment = true;
        commentOpen = n;
      }
      continue;
    }
    if (s.startsWith('//')) continue;
    s = stripEol(s);
    if (!s) continue;
    if (s === '<<<' || s.startsWith('<<<(') || s.startsWith('begin code in')) {
      inCode = true;
      codeOpen = n;
      continue;
    }
    if (s.startsWith('[')) {
      vlistDepth++;
      continue;
    }
    if (s.startsWith(']')) {
      vlistDepth--;
      if (vlistDepth < 0) {
        err(n, 'value-list close `]` without a matching `[`');
        vlistDepth = 0;
      }
      continue;
    }
    if (s === '{') {
      blockDepth++;
      blockOpen = n;
      continue;
    }
    if (s === '}') {
      blockDepth--;
      if (blockDepth < 0) {
        err(n, 'block close `}` without a matching `{`');
        blockDepth = 0;
      }
      continue;
    }
    if (!knownLeading(s)) {
      const tok = s.split(/\s+/)[0] || s;
      if (strictDiagnostics)
        warn(n, lines[n].indexOf(tok) >= 0 ? lines[n].indexOf(tok) : 0, tok.length,
          `unrecognised leading keyword \`${tok}\` (plugin/NLP/subtest call, or a removed command?)`);
    }
  }
  if (inBlockComment) err(commentOpen, 'unterminated block comment `/*` (no `*/`)');
  if (inCode) err(codeOpen, 'unterminated code block (no `>>>` / `end code`)');
  if (blockDepth > 0) err(blockOpen, `${blockDepth} block(s) not closed (missing \`}\`)`);
  if (vlistDepth > 0) err(0, `${vlistDepth} value-list(s) not closed (missing \`]\`)`);
  return out;
}

// ---- LSP plumbing ----
const connection = createConnection(ProposedFeatures.all);
const documents = new TextDocuments(TextDocument);

connection.onInitialize((_params: InitializeParams): InitializeResult => ({
  capabilities: {
    textDocumentSync: TextDocumentSyncKind.Incremental,
    completionProvider: { triggerCharacters: ['@', '#', ' '] },
    hoverProvider: true,
  },
}));

connection.onInitialized(() => {
  connection.workspace?.getConfiguration?.('seleasy').then((cfg: { strictDiagnostics?: boolean } | null) => {
    if (cfg && typeof cfg.strictDiagnostics === 'boolean') strictDiagnostics = cfg.strictDiagnostics;
    documents.all().forEach(validate);
  }).catch(() => {});
});

connection.onDidChangeConfiguration((change) => {
  const cfg = (change.settings as { seleasy?: { strictDiagnostics?: boolean } } | undefined)?.seleasy;
  if (cfg && typeof cfg.strictDiagnostics === 'boolean') strictDiagnostics = cfg.strictDiagnostics;
  documents.all().forEach(validate);
});

function validate(doc: TextDocument) {
  connection.sendDiagnostics({ uri: doc.uri, diagnostics: lint(doc) });
}

documents.onDidChangeContent((e) => validate(e.document));
documents.onDidClose((e) => connection.sendDiagnostics({ uri: e.document.uri, diagnostics: [] }));

// completion — keyword catalog + a couple of block snippets
connection.onCompletion((): CompletionItem[] => {
  const items: CompletionItem[] = COMMANDS.map((c) => ({
    label: c.keyword,
    kind: CompletionItemKind.Keyword,
    detail: c.class,
    insertText: c.requiresArg ? c.keyword + ' ' : c.keyword,
    documentation: `${c.match} keyword${c.requiresArg ? ' — requires an argument' : ''}${c.canBeBare ? ' — may stand alone' : ''}`,
  }));
  items.push(
    { label: 'if-block', kind: CompletionItemKind.Snippet, insertText: '? ${1:condition} {\n\t$0\n}', insertTextFormat: 2, detail: 'seleasy if block' },
    { label: 'subtest', kind: CompletionItemKind.Snippet, insertText: 'subtest "${1:name}" {\n\t$0\n}', insertTextFormat: 2, detail: 'define a sub-test' },
  );
  return items;
});

connection.onHover((params): Hover | null => {
  const doc = documents.get(params.textDocument.uri);
  if (!doc) return null;
  const line = doc.getText({ start: { line: params.position.line, character: 0 }, end: { line: params.position.line, character: 500 } });
  const ch = params.position.character;
  const around = /[A-Za-z0-9_@#-]+/g;
  let m: RegExpExecArray | null;
  let word: string | null = null;
  while ((m = around.exec(line))) {
    if (m.index <= ch && ch <= m.index + m[0].length) {
      word = m[0];
      break;
    }
  }
  if (!word) return null;
  const c = byKeyword.get(word.toLowerCase());
  if (!c) return null;
  return {
    contents: {
      kind: MarkupKind.Markdown,
      value: [
        `**${c.keyword}** — \`${c.class}\``,
        '',
        `match: ${c.match}${c.requiresArg ? ' · requires an argument' : ''}${c.canBeBare ? ' · may stand alone' : ''}`,
        c.qualifiers && c.qualifiers.length ? `\nqualifiers: ${c.qualifiers.join(', ')}` : '',
      ].join('\n'),
    },
  };
});

documents.listen(connection);
connection.listen();
