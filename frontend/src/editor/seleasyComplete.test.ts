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
