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
import { type CompletionContext, type CompletionResult, type Completion } from '@codemirror/autocomplete';
import { SELEASY } from './seleasyLang';
import { api } from '../api';
import commandsData from '../config/seleasyCommands.json';

interface CmdMeta {
  keyword: string;
  match: string;
  requiresArg: boolean;
  canBeBare: boolean;
  class: string;
  qualifiers?: string[];
}
const cmdMeta = new Map<string, CmdMeta>();
for (const c of (commandsData as { commands: CmdMeta[] }).commands) cmdMeta.set(c.keyword, c);

function iconType(style: number | number[]): string {
  const code = Array.isArray(style) ? style[0] : style;
  if (code === 1) return 'keyword';
  if (code === 3) return 'constant';
  if (code === 4) return 'operator';
  if (code === 5) return 'variable';
  return 'text';
}

// Static keyword completions from the seleasy keyword map, enriched with commands.json metadata.
const baseOptions: Completion[] = Object.keys(SELEASY)
  .filter((k) => /^[@a-z][\w-]*$/i.test(k)) // word-like keywords only (skip pure-punctuation operators)
  .sort()
  .map((k) => {
    const meta = cmdMeta.get(k);
    const opt: Completion = { label: k, type: iconType(SELEASY[k]) };
    if (meta?.class) opt.detail = meta.class;
    if (meta?.requiresArg) opt.apply = k + ' '; // keyword needs an argument → insert trailing space
    return opt;
  });

// Build a file-aware completion source. `getPath` yields the current .sel file (for @call subtests).
export function mkSeleasyCompletion(getPath: () => string | null) {
  return async (ctx: CompletionContext): Promise<CompletionResult | null> => {
    const line = ctx.state.doc.lineAt(ctx.pos);
    const before = line.text.slice(0, ctx.pos - line.from);

    // `@call ` → complete with the file's declared subtests (fetched live)
    if (/^\s*@call\s/i.test(before)) {
      const path = getPath();
      if (path) {
        try {
          const subs = await api.subtests(path);
          const word = ctx.matchBefore(/\S*/);
          return {
            from: word ? word.from : ctx.pos,
            options: (subs || []).map((s) => ({ label: s, type: 'function' })),
          };
        } catch {
          /* fall through to keyword completion */
        }
      }
    }

    const word = ctx.matchBefore(/[@\w-]*/);
    if (!word || (word.from === word.to && !ctx.explicit)) return null;
    return { from: word.from, options: baseOptions, validFor: /^[@\w-]*$/ };
  };
}
