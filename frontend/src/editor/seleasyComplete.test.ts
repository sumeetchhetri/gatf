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
import { describe, it, expect } from 'vitest';
import { EditorState } from '@codemirror/state';
import { CompletionContext } from '@codemirror/autocomplete';
import { mkSeleasyCompletion } from './seleasyComplete';

const ctxFor = (doc: string, pos: number, explicit: boolean) =>
  new CompletionContext(EditorState.create({ doc }), pos, explicit);

describe('mkSeleasyCompletion', () => {
  it('offers keyword completions for a word prefix', async () => {
    const src = mkSeleasyCompletion(() => null);
    const res = await src(ctxFor('op', 2, true));
    expect(res).not.toBeNull();
    expect(res!.from).toBe(0);
    expect(res!.options.map((o) => o.label)).toContain('open');
  });

  it('returns null at an empty, non-explicit position', async () => {
    const src = mkSeleasyCompletion(() => null);
    expect(await src(ctxFor('', 0, false))).toBeNull();
  });
});
