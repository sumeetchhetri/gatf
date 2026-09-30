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
import { describe, expect, it } from 'vitest';
import { initialWatch, onFinished, onSaved, watchOn } from './watchQueue';

describe('watch queue (M15)', () => {
  it('runs on watch-on and on every save while idle', () => {
    let [s, a] = watchOn(initialWatch);
    expect(a).toBe('start');
    expect(s).toEqual({ runId: 1, running: true, pending: false });
    [s, a] = onFinished(s);
    expect(a).toBe('none');
    expect(s.running).toBe(false);
    [s, a] = onSaved(s);
    expect(a).toBe('start');
    expect(s.runId).toBe(2);
  });

  it('a save mid-run stops it and re-runs once, however many saves arrive', () => {
    let [s] = watchOn(initialWatch);
    let a;
    [s, a] = onSaved(s);
    expect(a).toBe('stop');
    expect(s.pending).toBe(true);
    [s, a] = onSaved(s);
    expect(a).toBe('none');
    [s, a] = onSaved(s);
    expect(a).toBe('none');
    [s, a] = onFinished(s);
    expect(a).toBe('start');
    expect(s).toEqual({ runId: 2, running: true, pending: false });
    [s, a] = onFinished(s);
    expect(a).toBe('none');
    expect(s).toEqual({ runId: 2, running: false, pending: false });
  });
});
